<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, message, request } from '../api'

const props = defineProps<{ mode: 'login' | 'temporary' | 'password'; notice: string }>()
const emit = defineEmits<{ signedIn: []; changed: []; logout: []; expired: [] }>()
const login = ref(''), current = ref(''), next = ref(''), repeat = ref(''), error = ref(''), busy = ref(false), visible = ref(false)
const invalid = ref('')
const form = ref<HTMLFormElement>()
const router = useRouter()
watch(() => props.mode, () => { current.value = next.value = repeat.value = error.value = ''; invalid.value = ''; visible.value = false })
function fail(field: string, text: string) {
  invalid.value = field; error.value = text
  void nextTick(() => form.value?.querySelector<HTMLElement>(`#${field}`)?.focus())
}
async function submit() {
  if (busy.value) return
  invalid.value = ''; error.value = ''
  if (props.mode === 'login' && !login.value.trim()) return fail('login', '请输入登录名。')
  if (!current.value) return fail('current', '请输入密码。')
  if (props.mode !== 'login') {
    const length = [...next.value].length
    if (length < 15 || length > 128) return fail('next', '新密码须为 15–128 个字符。')
    if (next.value === current.value) return fail('next', '新密码不能与当前密码相同。')
    if (next.value !== repeat.value) return fail('repeat', '两次输入的密码不一致。')
  }
  busy.value = true
  try {
    if (props.mode === 'login') {
      await request('/session', 'POST', { login: login.value, password: current.value })
      emit('signedIn')
    } else {
      await request('/session/password', 'PUT', { current: current.value, next: next.value })
      emit('changed')
    }
    current.value = next.value = repeat.value = ''
  } catch (failure) {
    if (failure instanceof ApiError && failure.code === 'LOGIN_FAILED') {
      current.value = ''; fail('current', message(failure))
    } else if (failure instanceof ApiError && failure.status === 401) emit('expired')
    else if (props.mode !== 'login' && failure instanceof ApiError && (failure.status === 0 || failure.status >= 500)) {
      current.value = next.value = repeat.value = ''
      error.value = '改密结果未知。请返回登录页尝试新密码，确认后再决定是否重试。'
    } else error.value = message(failure)
  } finally { busy.value = false }
}
</script>

<template>
  <form ref="form" class="auth" novalidate @submit.prevent="submit">
    <div class="auth-top"><span class="micro with-rule" lang="en">ACCOUNT ACCESS</span><svg class="ico crosshair" aria-hidden="true"><use href="#i-plus" /></svg></div>
    <h1 tabindex="-1">{{ mode === 'login' ? '登录后台' : mode === 'temporary' ? '设置新密码' : '修改密码' }}</h1>
    <p class="auth-desc">{{ mode === 'login' ? '使用你的账号登录内观后台。' : mode === 'temporary' ? '当前使用临时密码。设置新密码后，请重新登录。' : '修改密码将结束此账号的所有会话。' }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <div v-if="mode === 'login'" class="field">
      <label for="login">登录名</label>
      <div class="input-wrap" :class="{ bad: invalid === 'login' }">
        <input id="login" v-model="login" type="text" placeholder="请输入登录名" autocomplete="username" autocapitalize="none" spellcheck="false" required maxlength="128" :aria-invalid="invalid === 'login'" :aria-describedby="invalid === 'login' ? 'form-message' : undefined">
      </div>
    </div>
    <div class="field">
      <label for="current">{{ mode === 'login' ? '密码' : '当前密码' }}</label>
      <div class="input-wrap" :class="{ bad: invalid === 'current' }">
        <input id="current" v-model="current" :type="visible ? 'text' : 'password'" :placeholder="mode === 'login' ? '请输入密码' : '请输入当前密码'" autocomplete="current-password" required maxlength="256" :aria-invalid="invalid === 'current'" :aria-describedby="invalid === 'current' ? 'form-message' : undefined">
        <button class="eye" type="button" :aria-label="visible ? '隐藏密码' : '显示密码'" :aria-pressed="visible" @click="visible = !visible">
          <svg class="ico" aria-hidden="true"><use :href="visible ? '#i-eyeoff' : '#i-eye'" /></svg>
        </button>
      </div>
    </div>
    <template v-if="mode !== 'login'">
      <div class="field">
        <label for="next">新密码</label>
        <div class="input-wrap" :class="{ bad: invalid === 'next' }">
          <input id="next" v-model="next" type="password" placeholder="请输入新密码" required autocomplete="new-password" :aria-invalid="invalid === 'next'" :aria-describedby="invalid === 'next' ? 'form-message' : undefined">
        </div>
      </div>
      <div class="field">
        <label for="repeat">确认新密码</label>
        <div class="input-wrap" :class="{ bad: invalid === 'repeat' }">
          <input id="repeat" v-model="repeat" type="password" placeholder="请再次输入新密码" required autocomplete="new-password" :aria-invalid="invalid === 'repeat'" :aria-describedby="invalid === 'repeat' ? 'form-message' : undefined">
        </div>
      </div>
    </template>
    <p id="form-message" class="form-msg" role="status">{{ error }}</p>
    <button class="login-btn" :disabled="busy" type="submit"><span>{{ busy ? (mode === 'login' ? '正在登录…' : '正在修改…') : (mode === 'login' ? '登录' : '修改密码') }}</span><svg class="ico" aria-hidden="true"><use href="#i-arrow" /></svg></button>
    <div class="auth-base"><span class="fine" aria-hidden="true">INNERSIGHT / CONSOLE</span><button v-if="mode === 'temporary'" type="button" class="text-button" :disabled="busy" @click="emit('logout')">退出登录</button><button v-else-if="mode === 'password'" type="button" class="text-button" :disabled="busy" @click="router.push({ name: 'home' })">取消</button></div>
  </form>
</template>
