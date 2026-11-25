import { ref } from 'vue'

export type Theme = 'light' | 'dark'
const key = 'innersight.theme'
export function resolveTheme(stored: string | null, systemDark: boolean): Theme {
  return stored === 'light' || stored === 'dark' ? stored : systemDark ? 'dark' : 'light'
}
export const theme = ref<Theme>('light')
let manual: Theme | null = null
let system: MediaQueryList | undefined
function apply() {
  theme.value = resolveTheme(manual, system?.matches ?? false)
  document.documentElement.dataset.theme = theme.value
}
export function mountTheme() {
  try {
    const stored = localStorage.getItem(key)
    if (stored === 'light' || stored === 'dark') manual = stored
  } catch { /* 禁用本地存储时仍可切换。 */ }
  system = window.matchMedia?.('(prefers-color-scheme: dark)')
  system?.addEventListener('change', apply)
  apply()
}
export function setTheme(value: Theme) {
  manual = value
  apply()
  try { localStorage.setItem(key, value) } catch { /* 保留当前文档内选择。 */ }
}
