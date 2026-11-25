import assert from 'node:assert/strict'
import { chromium } from 'playwright-core'

const url = process.env.INNERSIGHT_UI_URL ?? 'http://localhost:5173'
const browser = await chromium.launch({ executablePath: process.env.INNERSIGHT_BROWSER, headless: true })
const errors = []
const profile = { id: 1, login: 'reader', name: '只读账号', temporary: false, admin: false, roles: [], permissions: ['user:read'] }
let signedIn = false
try {
  const context = await browser.newContext({ viewport: { width: 320, height: 760 }, colorScheme: 'dark', reducedMotion: 'reduce' })
  const page = await context.newPage()
  page.setDefaultTimeout(10000)
  page.on('pageerror', error => errors.push(error.message))
  // 固定的 API 响应只用于浏览器布局与交互检查，不替代真实 MySQL 验收。
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const endpoint = new URL(route.request().url()).pathname
    if (endpoint === '/api/session' && route.request().method() === 'POST') { signedIn = true; return route.fulfill({json:{temporary:false}}) }
    if (endpoint === '/api/session' && route.request().method() === 'DELETE') { signedIn = false; return route.fulfill({status:204}) }
    if (endpoint === '/api/session') return route.fulfill(signedIn ? {json:profile} : {status:401, json:{code:'SESSION_EXPIRED',message:'请重新登录。'}})
    const user = {id:2, login:'viewed', name:'受检账号', enabled:true, revision:0, roles:[]}
    if (endpoint === '/api/users/2') return route.fulfill({json:user})
    if (endpoint === '/api/users') return route.fulfill({json:{items:[user],total:1,size:20,page:0}})
    throw new Error(`Unexpected fixture request: ${endpoint}`)
  })
  const noOverflow = async () => assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),true)
  await page.goto(`${url}/login`)
  await page.getByRole('heading',{name:'登录后台'}).waitFor()
  assert.equal(await page.locator('html').getAttribute('data-theme'),'dark')
  await noOverflow()
  await page.getByRole('button',{name:'登录',exact:true}).click()
  assert.equal(await page.getByLabel('登录名').getAttribute('aria-invalid'),'true')
  await page.getByLabel('登录名').fill('reader')
  await page.getByRole('radio',{name:'浅色'}).check()
  assert.equal(await page.locator('html').getAttribute('data-theme'),'light')
  assert.equal(await page.getByLabel('登录名').inputValue(),'reader')
  await page.reload()
  await page.getByRole('heading',{name:'登录后台'}).waitFor()
  assert.equal(await page.locator('html').getAttribute('data-theme'),'light')
  await page.getByLabel('登录名').fill('reader')
  await page.getByLabel('密码',{exact:true}).fill('browser-fixture-only')
  await page.getByRole('button',{name:'登录',exact:true}).click()
  await page.getByRole('heading',{name:'后台首页'}).waitFor()
  assert.equal(await page.getByRole('link',{name:'角色管理'}).count(),0)
  await noOverflow()
  await page.getByRole('link',{name:/用户管理/}).click()
  await page.getByRole('button',{name:'查看',exact:true}).waitFor()
  assert.equal(await page.getByRole('button',{name:'新建用户'}).count(),0)
  await noOverflow()
  await page.getByRole('button',{name:'查看',exact:true}).click()
  await page.getByRole('dialog').getByText('受检账号').waitFor()
  await page.keyboard.press('Escape')
  await page.getByRole('dialog').waitFor({state:'hidden'})
  assert.equal(await page.evaluate(() => document.activeElement?.textContent?.trim()),'查看')
  await page.setViewportSize({width:1280,height:800})
  await page.getByRole('radio',{name:'深色'}).check()
  assert.equal(await page.locator('html').getAttribute('data-theme'),'dark')
  await noOverflow()
  await page.clock.install()
  await page.locator('.account-wrap summary').click()
  await page.getByRole('button',{name:'退出登录'}).click()
  await page.getByRole('heading',{name:'登录后台'}).waitFor()
  const logoutNotice = page.getByRole('status').getByText('已退出登录。',{exact:true})
  await logoutNotice.waitFor()
  for (const [width, theme] of [[1280, '深色'], [320, '浅色']]) {
    await page.setViewportSize({width,height:800})
    await page.getByRole('radio',{name:theme}).check()
    const box = await logoutNotice.boundingBox()
    const picker = await page.locator('.appearance').boundingBox()
    assert.ok(box && picker)
    assert.ok(Math.abs(box.x + box.width / 2 - width / 2) < 1)
    assert.ok(Math.abs(box.y - picker.y - picker.height - 20) < 1)
    assert.ok(box.x >= 0 && box.x + box.width <= width)
    const glass = await logoutNotice.evaluate(node => {
      const style = getComputedStyle(node)
      return {background:style.backgroundColor, filter:style.backdropFilter}
    })
    assert.match(glass.filter,/blur\(16px\)/)
    assert.match(glass.background,/\/ 0\.54/)
  }
  assert.equal(await page.locator('.auth .notice').count(),0)
  await page.clock.fastForward(3100)
  assert.equal(await logoutNotice.count(),0)
  assert.deepEqual(errors,[])
  console.log('Browser smoke PASS: system/hand-picked themes, input persistence, refresh, readonly navigation, modal Escape/focus, 320px/1280px no overflow, glass logout notice horizontally centered at the theme-control offset, dismissed after 3s, 0 JS exceptions (fixture API).')
} finally { await browser.close() }
