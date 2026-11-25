<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, can, message, request, type Profile } from './api'
import AdminList from './views/AdminList.vue'
import HomeView from './views/HomeView.vue'
import AuthForm from './components/AuthForm.vue'
import OpticArt from './components/optics/OpticArt.vue'
import OpticDefs from './components/optics/OpticDefs.vue'
import ThemePicker from './components/ThemePicker.vue'

const route = useRoute(), router = useRouter()
const profile = ref<Profile | null>(null), loading = ref(true), notice = ref(''), failure = ref(''), denied = ref(false)
const logoutNotice = ref(false)
let logoutNoticeTimer: number | undefined
const homeView = ref<InstanceType<typeof HomeView>>()
const exhibit = ref<HTMLElement>()
const authArea = ref<HTMLElement>()
const mobileMenu = ref(false), accountMenu = ref<HTMLDetailsElement>(), title = ref<HTMLElement>()
const entries = computed(() => [
  ...(can(profile.value, 'user:read') ? [{name:'users', label:'用户管理', icon:'user', mark:'01 / USERS', desc:'查看账号、用户状态与角色。'}] : []),
  ...(can(profile.value, 'role:read') ? [{name:'roles', label:'角色管理', icon:'role', mark:'02 / ROLES', desc:'查看角色与权限。'}] : []),
])
const authMode = computed(() => !profile.value ? 'login' : profile.value.temporary ? 'temporary' : 'password')
const branded = computed(() => !profile.value || profile.value.temporary)
const section = computed(() => route.name === 'users' ? 'users' : route.name === 'roles' ? 'roles' : null)
const permitted = computed(() => !section.value || entries.value.some(entry => entry.name === section.value))
let returnTo = ''
let initialized = false
function closeMenu() { mobileMenu.value = false; if (accountMenu.value) accountMenu.value.open = false }
function focusTitle() {
  void nextTick(() => {
    const activeTitle = title.value ?? authArea.value?.querySelector('h1')
    if (activeTitle) activeTitle.focus()
    else homeView.value?.focus()
  })
}
function moveOptic(event: PointerEvent) {
  if (event.pointerType !== 'mouse' || window.matchMedia('(prefers-reduced-motion: reduce)').matches || !exhibit.value) return
  const box = exhibit.value.getBoundingClientRect()
  exhibit.value.style.setProperty('--mx', `${((event.clientX - box.left) / box.width - 0.5) * 20}px`)
  exhibit.value.style.setProperty('--my', `${((event.clientY - box.top) / box.height - 0.5) * 14}px`)
  exhibit.value.classList.add('is-lit')
}
function resetOptic() {
  exhibit.value?.classList.remove('is-lit')
  exhibit.value?.style.removeProperty('--mx')
  exhibit.value?.style.removeProperty('--my')
}
async function sessionExpired(text = '会话已失效，请重新登录。') {
  if (section.value) returnTo = section.value
  profile.value = null; failure.value = ''; notice.value = text
  await router.replace({name:'login'}); focusTitle()
}
async function refresh(navigate = true) {
  loading.value = true; failure.value = ''
  try {
    profile.value = await request<Profile>('/session')
    if (profile.value.temporary) await router.replace({name:'password'})
    else if (navigate && route.name === 'login') {
      const target = entries.value.some(entry => entry.name === returnTo) ? returnTo : 'home'
      returnTo = ''; await router.replace({name:target})
    }
  } catch (error) {
    profile.value = null
    if (error instanceof ApiError && error.status === 401) {
      if (section.value) returnTo = section.value
      if (route.name !== 'login' && route.name !== 'home') notice.value = '会话已失效，请重新登录。'
      await router.replace({name:'login'})
    } else failure.value = message(error)
  } finally { loading.value = false; initialized = true; focusTitle() }
}
async function revoked() {
  denied.value = true
  await refresh(false)
}
function clearLogoutNotice() {
  window.clearTimeout(logoutNoticeTimer)
  logoutNoticeTimer = undefined
  logoutNotice.value = false
}
async function logout() {
  try {
    await request('/session', 'DELETE')
    returnTo = ''
    await sessionExpired('')
    clearLogoutNotice()
    logoutNotice.value = true
    logoutNoticeTimer = window.setTimeout(clearLogoutNotice, 3000)
  } catch (error) { notice.value = message(error) }
}
function escapeAccount(event: KeyboardEvent) {
  if (event.key !== 'Escape' || !accountMenu.value?.open) return
  accountMenu.value.open = false
  accountMenu.value.querySelector('summary')?.focus()
}
watch(() => route.name, () => {
  denied.value = false; closeMenu()
  if (initialized && !loading.value && !failure.value) {
    if (!profile.value && route.name !== 'login') { if (section.value) returnTo = section.value; void router.replace({name:'login'}) }
    else if (profile.value?.temporary && route.name !== 'password') void router.replace({name:'password'})
    else if (profile.value && !profile.value.temporary && route.name === 'login') void router.replace({name:'home'})
    focusTitle()
  }
})
onMounted(() => void refresh())
onUnmounted(clearLogoutNotice)
</script>

<template>
  <OpticDefs />
  <div :class="{ 'in-workspace': !branded }" @keydown="escapeAccount">
    <header class="masthead"><div class="mast-inner">
      <div class="brand"><svg class="ico" aria-hidden="true"><use href="#i-mark" /></svg><span class="brand-type"><b>内观</b><span>InnerSight</span></span></div>
      <span v-if="!branded" class="console-name">管理控制台</span>
      <button v-if="!branded" type="button" class="menu-toggle" aria-controls="workspace-sidebar" :aria-expanded="mobileMenu" @click="mobileMenu = !mobileMenu">菜单</button>
      <div class="mast-actions">
        <ThemePicker />
        <details v-if="profile && !profile.temporary" ref="accountMenu" class="account-wrap">
          <summary class="menu-trigger" :aria-label="`${profile.name}，账号菜单`"><span class="avatar" aria-hidden="true">{{ [...profile.name][0] }}</span><span class="acct-name">{{ profile.name }}</span><svg class="ico chev" aria-hidden="true"><use href="#i-chev" /></svg></summary>
          <div class="pop-menu"><RouterLink :to="{name:'password'}" @click="closeMenu">修改密码</RouterLink><button type="button" @click="logout">退出登录</button></div>
        </details>
      </div>
      <div class="logout-notice" role="status">
        <span v-if="logoutNotice">已退出登录。</span>
      </div>
    </div></header>
    <main v-if="loading || failure" class="center-state" aria-live="polite">
      <h1 ref="title" tabindex="-1">{{ loading ? '正在读取会话…' : '连接服务失败' }}</h1><p v-if="failure">{{ failure }}</p>
      <button v-if="failure" class="secondary" type="button" @click="refresh()">重试</button>
    </main>
    <div v-else-if="branded" class="login-page">
      <div class="login-frame"><main ref="authArea" class="login-main">
        <section ref="exhibit" class="exhibit" aria-label="品牌视觉：折光" @pointermove="moveOptic" @pointerleave="resetOptic"><div class="story"><p class="micro with-rule" lang="en">OPTICAL STUDIES / 01</p><h2>向内，见深。</h2><blockquote class="inscription"><p>知人者智，自知者明。</p><cite>《道德经》第三十三章</cite></blockquote></div>
          <figure class="model" aria-hidden="true"><OpticArt /></figure><div class="figure-caption fine" aria-hidden="true"><i class="ruler"></i><b>FIG. 01</b><span>折光 / REFRACTION</span></div>
        </section>
        <AuthForm :mode="authMode" :notice="notice" @signed-in="clearLogoutNotice(); notice = ''; refresh()" @changed="sessionExpired('密码已修改，请使用新密码重新登录。')" @logout="logout" @expired="sessionExpired()" />
      </main>
      <footer class="login-foot"><span>内观：内视自身，自省自身。</span><span class="fine">INNERSIGHT / OPTICAL STUDIES</span></footer></div>
    </div>
    <div v-else-if="profile" class="console-grid">
      <aside id="workspace-sidebar" class="sidebar" :class="{'mobile-open':mobileMenu}"><p class="side-label">工作空间</p><nav class="nav-links" aria-label="工作空间">
        <RouterLink :to="{name:'home'}"><svg class="ico" aria-hidden="true"><use href="#i-home" /></svg>首页</RouterLink>
        <RouterLink v-for="entry in entries" :key="entry.name" :to="{name:entry.name}"><svg class="ico" aria-hidden="true"><use :href="`#i-${entry.icon}`" /></svg>{{ entry.label }}</RouterLink>
      </nav><span class="side-end fine">INNERSIGHT / 01</span></aside>
      <main class="work">
        <p v-if="notice && route.name !== 'password'" class="notice" role="status">{{ notice }}</p>
        <section v-if="section && (denied || !permitted)" class="center-state"><h1 ref="title" tabindex="-1">访问权限已收回</h1><p>当前账号无权查看此页面，原有内容已清除。</p><RouterLink class="secondary" :to="{name:'home'}">返回首页</RouterLink></section>
        <AdminList v-else-if="section" :key="section" :kind="section" :profile="profile" @revoked="revoked" @permissions-updated="profile = $event" @expired="sessionExpired()" />
        <AuthForm v-else-if="route.name === 'password'" class="account-form" mode="password" :notice="notice" @changed="sessionExpired('密码已修改，请使用新密码重新登录。')" @logout="logout" @expired="sessionExpired()" />
        <HomeView v-else ref="homeView" :profile="profile" :entries="entries" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.mast-inner {
  position: relative;
}
.logout-notice {
  position: absolute;
  /* 主题切换栏垂直居中，高 50px；提示保留其下方 20px 的间距。 */
  inset-block-start: calc(50% + 25px + 20px);
  inset-inline: 0;
  margin-inline: auto;
  inline-size: max-content;
  max-inline-size: calc(100vw - 32px);
  pointer-events: none;
}
.logout-notice span {
  display: block;
  padding: 10px 16px;
  border: 1px solid color-mix(in srgb, var(--glass-border) 65%, transparent);
  border-radius: 12px;
  background: var(--glass-a);
  background: color-mix(in srgb, var(--surface) 54%, transparent);
  -webkit-backdrop-filter: blur(16px) saturate(140%);
  backdrop-filter: blur(16px) saturate(140%);
  color: var(--ink);
  box-shadow: inset 0 1px var(--glint), 0 6px 18px var(--shadow);
  text-align: center;
}
</style>
