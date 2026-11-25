import { defineComponent, h } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

const marker = defineComponent({ render: () => h('span') })
export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', name: 'home', component: marker },
  { path: '/login', name: 'login', component: marker },
  { path: '/password', name: 'password', component: marker },
  { path: '/users', name: 'users', component: marker },
  { path: '/roles', name: 'roles', component: marker },
  { path: '/:rest(.*)*', redirect: '/' },
] })
