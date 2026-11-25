export type Permission = 'user:read' | 'user:manage' | 'role:read'
export interface RoleRef { id: number; code: string; name: string; enabled: boolean }
export interface Profile { id: number; login: string; name: string; temporary: boolean; admin: boolean; roles: RoleRef[]; permissions: Permission[] }
export interface User extends RecordBase { login: string; roles: RoleRef[] }
export interface Role extends RecordBase, RoleRef { count: number; permissions: Permission[] }
export interface RecordBase { id: number; name: string; enabled: boolean; revision: number }
export interface Page<T> { items: T[]; total: number; page: number; size: number }

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message) }
}

export async function request<T>(path: string, method = 'GET', data?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method, credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(10000),
      ...(data === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }),
    })
  } catch {
    throw new ApiError(0, 'NETWORK', '无法连接服务，请检查网络后重试。')
  }
  if (!response.ok) {
    const error: unknown = await response.json().catch(() => null)
    if (error && typeof error === 'object' && 'code' in error && 'message' in error && typeof error.code === 'string' && typeof error.message === 'string') {
      throw new ApiError(response.status, error.code, error.message)
    }
    throw new ApiError(response.status, 'HTTP', response.status >= 500 ? '服务暂不可用，请稍后重试。' : '请求失败。')
  }
  return response.status === 204 ? undefined as T : await response.json()
}

export function message(error: unknown) { return error instanceof ApiError ? error.message : '操作未完成，请稍后重试。' }
export function can(profile: Profile | null, permission: Permission) { return !!profile && !profile.temporary && (profile.admin || profile.permissions.includes(permission)) }
export function listQuery(search: string, status: string, page: number) {
  const query = new URLSearchParams({ search: search.trim(), page: String(page) })
  if (status) query.set('enabled', status)
  return `?${query}`
}
