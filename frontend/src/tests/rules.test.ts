import { describe, expect, it } from 'vitest'
import { can, listQuery, type Profile } from '../api'
import { resolveTheme } from '../composables/useTheme'

const profile: Profile = { id: 1, login: 'reader', name: 'Reader', temporary: false, admin: false, roles: [], permissions: ['user:read'] }
describe('权限状态', () => {
  it('只读账号没有维护权限', () => {
    expect(can(profile, 'user:read')).toBe(true)
    expect(can(profile, 'user:manage')).toBe(false)
  })
  it('临时密码会话不显示管理入口', () => {
    expect(can({ ...profile, temporary: true, admin: true }, 'user:read')).toBe(false)
  })
  it('筛选参数编码后发送，不受输入中的 & 干扰', () => {
    const query = new URLSearchParams(listQuery(' test&enabled=true ', 'false', 2))
    expect(query.get('search')).toBe('test&enabled=true')
    expect(query.get('enabled')).toBe('false')
    expect(query.get('page')).toBe('2')
  })
})
describe('双主题', () => {
  it('手选优先，否则跟随系统', () => {
    expect(resolveTheme('light', true)).toBe('light')
    expect(resolveTheme('dark', false)).toBe('dark')
    expect(resolveTheme('invalid', true)).toBe('dark')
    expect(resolveTheme(null, false)).toBe('light')
  })
})
