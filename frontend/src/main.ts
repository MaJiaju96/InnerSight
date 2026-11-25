import { createApp } from 'vue'
import App from './App.vue'
import { mountTheme } from './composables/useTheme'
import { router } from './router'
import './styles/refraction.css'
import './styles/workspace.css'

mountTheme()
createApp(App).use(router).mount('#app')
