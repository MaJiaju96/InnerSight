<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ApiError, can, listQuery, message, request, type Page, type Permission, type Profile, type Role, type RoleRef, type User } from '../api'

type Item = User | Role
type Mode = 'detail' | 'create' | 'edit' | 'status' | 'assign' | 'reset'
const props = defineProps<{ kind: 'users' | 'roles'; profile: Profile }>()
const emit = defineEmits<{ revoked: []; expired: []; permissionsUpdated: [profile: Profile] }>()
const isUser = computed(() => props.kind === 'users')
const noun = computed(() => isUser.value ? '用户' : '角色')
const base = computed(() => `/${props.kind}`)
const canCreate = computed(() => isUser.value ? can(props.profile, 'user:manage') : props.profile.admin)
const pageSizes = [20, 50, 100]
const pageSize = ref(20)
const createdLogin = ref('')
const conflict = ref(false)
const search = ref(''), status = ref(''), appliedSearch = ref(''), appliedStatus = ref(''), page = ref(0)
const result = ref<Page<Item>>(), reading = ref(true), readError = ref(''), feedback = ref('')
const dialog = ref<HTMLDialogElement>(), cancel = ref<HTMLButtonElement>(), heading = ref<HTMLElement>(), form = ref<HTMLFormElement>()
const mode = ref<Mode>('detail'), item = ref<Item>(), target = ref<number>(), dialogError = ref(''), fieldError = ref(''), saving = ref(false), detailLoading = ref(false), reloadNeeded = ref(false)
const code = ref(''), name = ref(''), password = ref(''), selection = ref<Permission[]>([]), selectedRoles = ref<number[]>([]), roleOptions = ref<RoleRef[]>([]), catalog = ref<Permission[]>([])
let trigger: HTMLElement | null = null
let readGeneration = 0
let detailGeneration = 0
const permissionLabel: Record<Permission,string> = { 'user:read':'查看用户', 'user:manage':'维护普通用户', 'role:read':'查看角色和权限目录' }
const itemCode = (row: Item) => 'login' in row ? row.login : row.code
const initialRoles = computed(() => item.value && 'roles' in item.value ? item.value.roles : [])
const added = computed(() => roleOptions.value.filter(role => selectedRoles.value.includes(role.id) && !initialRoles.value.some(r => r.id === role.id)))
const removed = computed(() => initialRoles.value.filter(role => !selectedRoles.value.includes(role.id)))
const grantsAdmin = computed(() => added.value.some(role => role.code === 'ADMIN'))
const removesAdmin = computed(() => removed.value.some(role => role.code === 'ADMIN'))
const assignmentChanged = computed(() => added.value.length > 0 || removed.value.length > 0)
const targetUser = computed(() => item.value && 'login' in item.value ? item.value : undefined)
const permissionGroups = [
  { label: '用户权限', values: ['user:read', 'user:manage'] as const },
  { label: '角色权限', values: ['role:read'] as const },
]
const adminCapabilities = '分配角色、重置他人密码、维护管理员账号，以及新建、编辑和启停自定义角色。'
const description = computed(() => isUser.value
  ? canCreate.value ? '查看账号状态，维护用户资料与角色分配。' : '查看账号资料、状态与已分配角色。'
  : '查看角色权限与绑定人数，维护自定义角色。')
const submitLabel = computed(() => {
  if (saving.value) return '正在保存…'
  if (mode.value === 'create') return `创建${noun.value}`
  if (mode.value === 'status') return `${item.value?.enabled ? '停用' : '启用'}${noun.value}`
  if (mode.value === 'reset') return '重置密码'
  if (mode.value === 'assign') return grantsAdmin.value ? '确认授予管理员权限' : removesAdmin.value ? '确认移除管理员权限' : '保存分配'
  return '保存'
})
function roleSummary(roles: RoleRef[]) {
  return roles.length ? roles.slice(0, 2).map(role => role.name).join('、') + (roles.length > 2 ? ` +${roles.length - 2}` : '') : '未分配'
}
const dialogTitle = computed(() => {
  const label = item.value?.name ?? ''
  return mode.value === 'detail' ? `${noun.value}详情` : mode.value === 'create' ? `新建${noun.value}` : mode.value === 'edit' ? `编辑${noun.value}` : mode.value === 'assign' ? `为「${targetUser.value?.login ?? label}」分配角色` : mode.value === 'reset' ? `重置「${label}」的密码` : `${item.value?.enabled ? '停用' : '启用'}${noun.value}「${label}」`
})
function boundary(error: unknown) {
  if (!(error instanceof ApiError)) return false
  if (error.status !== 401 && !(error.status === 403 && error.code === 'FORBIDDEN')) return false
  result.value = undefined; item.value = undefined; password.value = ''; dialog.value?.close()
  if (error.status === 401) emit('expired'); else emit('revoked')
  return true
}
async function load() {
  const generation = ++readGeneration
  reading.value = true; readError.value = ''
  try {
    const data = await request<Page<Item>>(base.value + listQuery(appliedSearch.value, appliedStatus.value, page.value, pageSize.value))
    if (generation !== readGeneration) return
    const last = Math.max(0, Math.ceil(data.total / data.size) - 1)
    if (page.value > last) { page.value = last; await load(); return }
    result.value = data
  } catch (error) {
    if (generation !== readGeneration) return
    result.value = undefined
    if (!boundary(error)) readError.value = message(error)
  } finally { if (generation === readGeneration) reading.value = false }
}
function filter(clear = false) {
  if (clear) { search.value = ''; status.value = '' }
  appliedSearch.value = search.value; appliedStatus.value = status.value; page.value = 0; void load()
}
function changeSize() { page.value = 0; void load() }
async function continueAssignment() {
  const login = createdLogin.value
  if (!props.profile.admin || !login || reading.value) return
  search.value = login; status.value = ''; appliedSearch.value = login; appliedStatus.value = ''; page.value = 0
  await load()
  const user = result.value?.items.find(row => 'login' in row && row.login === login)
  if (user && createdLogin.value === login) { createdLogin.value = ''; await open('assign', user.id) }
}
function turn(offset: number) { page.value += offset; void load() }
function editable(row: Item) {
  return 'login' in row ? canCreate.value && (props.profile.admin || !row.roles.some(role => role.code === 'ADMIN')) : props.profile.admin && row.code !== 'ADMIN'
}
function switchable(row: Item) { return editable(row) && (!('login' in row) || row.id !== props.profile.id) }
function assignable(row: Item) { return 'login' in row && props.profile.admin }
function resettable(row: Item) { return assignable(row) && row.id !== props.profile.id }
function clearForm() { code.value = ''; name.value = ''; password.value = ''; selection.value = []; selectedRoles.value = []; item.value = undefined; dialogError.value = ''; fieldError.value = ''; reloadNeeded.value = false; conflict.value = false }
async function open(next: Mode, id?: number) {
  if (!dialog.value?.open) { trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null; dialog.value?.showModal() }
  mode.value = next; target.value = id
  await readDetail()
}
async function readDetail() {
  const generation = ++detailGeneration
  clearForm(); detailLoading.value = true
  try {
    const [row, available, permissions] = await Promise.all([
      target.value === undefined ? Promise.resolve(undefined) : request<Item>(`${base.value}/${target.value}`),
      mode.value === 'assign' ? request<RoleRef[]>('/roles/options') : Promise.resolve([]),
      !isUser.value && (mode.value === 'create' || mode.value === 'edit') ? request<Permission[]>('/roles/catalog') : Promise.resolve([]),
    ])
    if (generation !== detailGeneration) return
    item.value = row; roleOptions.value = available; catalog.value = permissions.sort()
    if (row) { code.value = itemCode(row); name.value = row.name; if ('roles' in row) selectedRoles.value = row.roles.map(r => r.id); else selection.value = [...row.permissions] }
    if (mode.value === 'status' || mode.value === 'reset') void nextTick(() => cancel.value?.focus())
    else void nextTick(() => heading.value?.focus())
  } catch (error) {
    if (generation !== detailGeneration) return
    if (!boundary(error)) { dialogError.value = message(error); reloadNeeded.value = true }
  } finally { if (generation === detailGeneration) detailLoading.value = false }
}
function close() { if (!saving.value) dialog.value?.close() }
function onClose() {
  ++detailGeneration; clearForm()
  if (trigger?.isConnected) trigger.focus()
  else document.getElementById('list-title')?.focus()
}
function onCancel(event: Event) { if (saving.value) event.preventDefault() }
function invalid(field: string, text: string) {
  fieldError.value = field; dialogError.value = text
  void nextTick(() => form.value?.querySelector<HTMLElement>(`#${field}`)?.focus())
}
async function save() {
  if (saving.value || detailLoading.value || reloadNeeded.value || mode.value === 'detail' || (mode.value === 'assign' && !assignmentChanged.value)) return
  const row = item.value
  const action = mode.value
  const creating = action === 'create'
  fieldError.value = ''; dialogError.value = ''
  if (creating && !(isUser.value ? /^[a-zA-Z0-9._-]{3,32}$/ : /^[a-zA-Z0-9_]{3,32}$/).test(code.value.trim())) return invalid('record-code', `${isUser.value ? '登录名' : '角色编码'}格式不正确。`)
  if ((creating || action === 'edit') && (![...name.value.trim()].length || [...name.value.trim()].length > 50)) return invalid('record-name','名称须为 1–50 个字符。')
  if ((action === 'reset' || (creating && isUser.value)) && ([...password.value].length < 15 || [...password.value].length > 128)) return invalid('record-password','临时密码须为 15–128 个字符。')
  if (!creating && !row) return
  saving.value = true
  try {
    const path = creating ? base.value : `${base.value}/${row?.id}${action === 'assign' ? '/roles' : action === 'reset' ? '/password' : ''}`
    let data: unknown
    if (creating) data = isUser.value ? { login:code.value, name:name.value, password:password.value } : { code:code.value, name:name.value, permissions:selection.value }
    else if (row) {
      const revision = row.revision
      if (action === 'assign') data = { revision, roles:selectedRoles.value }
      else if (action === 'reset') data = { revision, password:password.value }
      else data = { revision, name:name.value, enabled:action === 'status' ? !row.enabled : row.enabled, ...('permissions' in row ? { permissions:action === 'status' ? row.permissions : selection.value, count:row.count } : {}) }
    }
    await request(path, creating ? 'POST' : 'PUT', data)
    createdLogin.value = creating && isUser.value && props.profile.admin ? code.value.trim().toLowerCase() : ''
    feedback.value = action === 'reset' ? '临时密码已重置。' : `${noun.value}变更已保存。`
    dialog.value?.close(); void load()
  } catch (error) {
    if (error instanceof ApiError && error.status === 403 && error.code === 'FORBIDDEN') {
      try {
        const account = await request<Profile>('/session')
        if (!can(account, isUser.value ? 'user:read' : 'role:read')) { boundary(error); return }
        emit('permissionsUpdated', account)
        dialog.value?.close()
        feedback.value = message(error)
        void load()
      } catch (failure) { if (!boundary(failure)) { dialogError.value = message(failure); reloadNeeded.value = true } }
    } else if (!boundary(error)) {
      if (error instanceof ApiError && error.code === 'DUPLICATE') invalid('record-code',message(error))
      else if (error instanceof ApiError && (error.status === 0 || error.status >= 500)) {
        dialogError.value = '保存结果未知。请重新读取记录确认，再决定是否重试。'; reloadNeeded.value = true
      } else { dialogError.value = message(error); conflict.value = error instanceof ApiError && error.code === 'CONFLICT'; reloadNeeded.value = conflict.value }
    }
  } finally { password.value = ''; saving.value = false }
}
function recover() { if (mode.value === 'create') { close(); void load() } else void readDetail() }
onMounted(() => void load())
</script>

<template>
  <section>
    <p class="micro with-rule work-kicker">WORKSPACE / {{ isUser ? 'USERS' : 'ROLES' }}</p>
    <div class="head-row list-head"><h1 id="list-title" tabindex="-1">{{ noun }}管理</h1><button v-if="canCreate" class="primary" type="button" @click="open('create')">新建{{ noun }}</button></div>
    <p class="list-description">{{ description }}</p>
    <form class="filters" @submit.prevent="filter()"><label>搜索<input v-model="search" maxlength="100" :placeholder="isUser ? '登录名或显示名' : '角色名称或编码'"></label><label>状态<select v-model="status"><option value="">全部状态</option><option value="true">启用</option><option value="false">停用</option></select></label><div class="row-actions"><button class="secondary" type="submit">查询</button><button class="text-button" type="button" @click="filter(true)">重置</button></div></form>
    <div v-if="feedback" class="notice" role="status">{{ feedback }} <button v-if="createdLogin && profile.admin" type="button" class="text-button" :disabled="reading" @click="continueAssignment">继续分配角色</button></div>
    <div class="data-panel" :aria-busy="reading">
      <p v-if="reading" class="empty-panel" role="status">正在加载{{ noun }}…</p>
      <div v-else-if="readError" class="empty-panel" role="alert"><p>{{ readError }}</p><button class="secondary" type="button" @click="load">重试</button></div>
      <div v-else-if="!result?.items.length" class="empty-panel"><p>{{ appliedSearch.trim() || appliedStatus ? '没有符合条件的记录' : `暂无${noun}` }}</p><button v-if="appliedSearch.trim() || appliedStatus" class="text-button" type="button" @click="filter(true)">清除筛选</button><button v-else-if="canCreate" class="text-button" type="button" @click="open('create')">新建{{ noun }}</button></div>
      <template v-else>
        <table><thead><tr><th scope="col">{{ isUser ? '登录名' : '角色名称' }}</th><th scope="col" class="wide-col">{{ isUser ? '显示名' : '角色编码' }}</th><th scope="col">状态</th><th scope="col" class="wide-col">{{ isUser ? '角色' : '绑定用户数' }}</th><th scope="col">操作</th></tr></thead>
          <tbody><tr v-for="row in result.items" :key="row.id"><td><button type="button" class="name-link" @click="open('detail', row.id)">{{ 'login' in row ? row.login : row.name }}</button></td><td class="wide-col">{{ 'login' in row ? row.name : row.code }}</td><td><span class="status-chip" :class="{ muted: !row.enabled }">{{ row.enabled ? '启用' : '停用' }}</span></td><td class="wide-col">{{ 'roles' in row ? roleSummary(row.roles) : row.count }}</td><td><div class="row-actions"><button class="text-button" type="button" @click="open('detail', row.id)">查看</button><details v-if="editable(row) || assignable(row)" class="row-more"><summary>更多</summary><div class="pop-menu"><button v-if="editable(row)" @click="open('edit',row.id)">编辑</button><button v-if="switchable(row)" @click="open('status',row.id)">{{ row.enabled ? '停用' : '启用' }}</button><button v-if="assignable(row)" @click="open('assign',row.id)">分配角色</button><button v-if="resettable(row)" @click="open('reset',row.id)">重置密码</button></div></details></div></td></tr></tbody>
        </table>
        <nav class="pagination" aria-label="分页"><span>共 {{ result.total }} 条 · 第 {{ page + 1 }} 页</span><label><span class="sr">每页条数</span><select v-model.number="pageSize" @change="changeSize"><option v-for="size in pageSizes" :key="size" :value="size">{{ size }} 条 / 页</option></select></label><button class="secondary" type="button" :disabled="!page" @click="turn(-1)">上一页</button><button class="secondary" type="button" :disabled="(page + 1) * result.size >= result.total" @click="turn(1)">下一页</button></nav>
      </template>
    </div>
  </section>
  <Teleport to="body"><dialog ref="dialog" class="drawer" :class="{ confirmation: mode === 'status' || mode === 'reset', 'status-confirmation': mode === 'status', 'reset-confirmation': mode === 'reset', 'user-detail-drawer': mode === 'detail' && isUser }" aria-labelledby="record-title" @close="onClose" @cancel="onCancel">
    <div class="drawer-head"><h2 id="record-title" ref="heading" tabindex="-1">{{ dialogTitle }}</h2><button type="button" class="text-button" :disabled="saving" @click="close">关闭</button></div>
    <p v-if="detailLoading" class="detail-loading" role="status">正在读取…</p>
    <form v-else ref="form" class="drawer-form" novalidate @submit.prevent="save">
      <p v-if="dialogError" id="drawer-error" class="error-panel" role="alert">{{ dialogError }}</p>
      <p v-if="conflict" class="list-description">其他管理员已修改这条记录。重新加载后，请基于最新资料检查并修改。下方为尚未保存的输入。</p>
      <button v-if="reloadNeeded" class="secondary" type="button" @click="recover">{{ conflict ? '重新加载' : '查看最新记录' }}</button>
      <template v-if="mode === 'detail' && item">
        <div v-if="targetUser" class="detail-identity"><span class="avatar" aria-hidden="true">{{ [...targetUser.name][0] }}</span><div><strong>{{ targetUser.name }}</strong><p>{{ targetUser.login }}</p></div></div>
        <h3 v-if="targetUser" class="detail-section">账号资料</h3>
        <dl class="details-list"><dt>{{ isUser ? '显示名' : '名称' }}</dt><dd>{{ item.name }}</dd><dt>{{ isUser ? '登录名' : '角色编码' }}</dt><dd>{{ itemCode(item) }}</dd><dt>状态</dt><dd>{{ item.enabled ? '启用' : '停用' }}{{ itemCode(item) === 'ADMIN' ? ' · 内置 · 只读' : '' }}</dd><template v-if="'roles' in item"><dt>角色</dt><dd>{{ item.roles.map(r => r.name + (r.enabled ? '' : '（停用）')).join('、') || '未分配' }}</dd></template><template v-else><dt>权限</dt><dd v-if="item.code !== 'ADMIN'">{{ item.permissions.map(p => permissionLabel[p]).join('、') || '无' }}</dd><dd v-else><strong>固定权限目录</strong><div v-for="group in permissionGroups" :key="group.label">{{ group.label }}：{{ group.values.map(p => permissionLabel[p]).join('、') }}</div></dd><dt>绑定用户数</dt><dd>{{ item.count }}</dd></template></dl>
        <section v-if="itemCode(item) === 'ADMIN' && !isUser" class="admin-capabilities"><h3>管理员专有能力</h3><p>{{ adminCapabilities }}</p><p>内置角色不可编辑或停用。</p></section><p v-else-if="!editable(item)">当前记录只读。</p><div class="row-actions wrap"><button v-if="editable(item)" type="button" class="secondary" @click="open('edit',item.id)">编辑{{ noun }}</button><button v-if="switchable(item)" type="button" class="secondary" @click="open('status',item.id)">{{ item.enabled ? '停用' : '启用' }}</button><button v-if="assignable(item)" type="button" class="secondary" @click="open('assign',item.id)">分配角色</button><button v-if="resettable(item)" type="button" class="secondary" @click="open('reset',item.id)">重置密码</button></div>
        <footer v-if="targetUser" class="detail-return"><button type="button" class="secondary" @click="close">返回用户列表</button></footer>
      </template>
      <template v-if="mode === 'create' || (mode === 'edit' && item)">
        <p v-if="mode === 'create' && isUser">账号创建后默认启用，角色需要另行分配。首次登录需要修改密码。</p>
        <label :for="'record-code'">{{ isUser ? '登录名' : '角色编码' }}<input id="record-code" v-model="code" :readonly="mode === 'edit'" maxlength="32" :aria-invalid="fieldError === 'record-code'" :aria-describedby="fieldError === 'record-code' ? 'drawer-error' : undefined"></label>
        <label for="record-name">{{ isUser ? '显示名' : '角色名称' }}<input id="record-name" v-model="name" maxlength="100" :aria-invalid="fieldError === 'record-name'" :aria-describedby="fieldError === 'record-name' ? 'drawer-error' : undefined"></label>
        <fieldset v-for="group in !isUser ? permissionGroups : []" :key="group.label"><legend>{{ group.label }}</legend><label v-for="p in group.values.filter(value => catalog.includes(value))" :key="p" class="check"><input v-model="selection" type="checkbox" :value="p" :checked="p === 'user:read' && selection.includes('user:manage') || selection.includes(p)" :disabled="p === 'user:read' && selection.includes('user:manage')">{{ permissionLabel[p] }} <code>{{ p }}</code></label><p v-if="group.label === '用户权限' && selection.includes('user:manage')">维护普通用户包含查看用户权限。</p></fieldset>
        <p v-if="!isUser && !selection.length">该角色暂不提供管理权限。</p>
        <p v-if="!isUser">分配角色与重置他人密码仅限内置管理员。</p>
      </template>
      <p v-if="targetUser && (mode === 'reset' || mode === 'status')">登录名：{{ targetUser.login }}</p>
      <label v-if="(mode === 'create' && isUser) || mode === 'reset'" for="record-password">{{ mode === 'reset' ? '新的临时密码' : '临时密码' }}<input id="record-password" v-model="password" type="password" autocomplete="new-password" maxlength="256" :aria-invalid="fieldError === 'record-password'" :aria-describedby="fieldError === 'record-password' ? 'drawer-error' : undefined"><small>15–128 个字符。提交后不再显示，请线下交给本人。</small></label>
      <p v-if="mode === 'reset' && item">现有会话将退出，下次登录必须改密。</p>
      <p v-if="mode === 'status' && item">{{ 'count' in item ? `当前有 ${item.count} 个用户绑定此角色。${item.enabled ? '停用后不再贡献权限，绑定仍保留。' : '启用后恢复已有绑定贡献的权限。'}` : item.enabled ? '停用后该账号的旧会话将立即失效。' : '启用后可以重新登录，旧会话不会恢复。' }}</p>
      <p v-if="mode === 'status' && item && 'count' in item && item.enabled">其他启用角色授予的权限不受影响。</p>
      <template v-if="mode === 'assign' && item"><fieldset><legend>角色选择</legend><label v-for="role in roleOptions" :key="role.id" class="check"><input v-model="selectedRoles" type="checkbox" :value="role.id" :disabled="(!role.enabled && !selectedRoles.includes(role.id) && !initialRoles.some(r => r.id === role.id)) || (role.code === 'ADMIN' && item.id === profile.id)">{{ role.name }} <span v-if="!role.enabled">（停用）</span></label></fieldset><p>将添加：{{ added.map(r=>r.name).join('、') || '无' }}</p><p>将移除：{{ removed.map(r=>r.name).join('、') || '无' }}</p><p v-if="targetUser?.id === profile.id && initialRoles.some(role => role.code === 'ADMIN')">不能移除自己的管理员角色。</p><div v-if="grantsAdmin || removesAdmin" class="error-panel"><p>{{ grantsAdmin ? '将授予' : '将移除' }} {{ targetUser?.login }} 的后台管理员权限。</p><p v-if="grantsAdmin">{{ adminCapabilities }}</p></div></template>
      <div v-if="mode !== 'detail'" class="drawer-actions"><button ref="cancel" class="secondary" type="button" :disabled="saving" autofocus @click="close">取消</button><button class="primary" :class="{ danger: mode === 'status' && item?.enabled }" type="submit" :disabled="saving || reloadNeeded || (mode !== 'create' && !item) || (mode === 'assign' && !assignmentChanged)">{{ submitLabel }}</button></div>
    </form>
  </dialog></Teleport>
</template>
