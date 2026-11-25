<script setup lang="ts">
import { ref } from 'vue'
import type { Profile } from '../api'
import OpticArt from '../components/optics/OpticArt.vue'

defineProps<{ profile: Profile; entries: ReadonlyArray<{ name: string; label: string; icon: string; mark: string; desc: string }> }>()
const heading = ref<HTMLElement>()
defineExpose({ focus: () => heading.value?.focus() })
</script>

<template>
  <p class="micro with-rule work-kicker" lang="en">WORKSPACE / 01</p><div class="head-row"><h1 ref="heading" tabindex="-1">{{ entries.length ? '后台首页' : '个人首页' }}</h1><span class="bread">管理控制台 / 首页</span></div>
  <section class="identity"><div class="who"><p class="fine" lang="en">ACCOUNT PROFILE</p><h2 class="who-label">当前账号</h2><p class="who-main"><strong>{{ profile.name }}</strong><span>{{ profile.login }}</span></p><div class="who-bottom">有效角色 <span v-for="role in profile.roles" :key="role.id" class="role-badge"><i class="dot"></i>{{ role.name }}</span><span v-if="!profile.roles.length">未分配</span></div></div><div class="identity-art" aria-hidden="true"><OpticArt /></div><div class="identity-tag" aria-hidden="true"><span>FIG. 01 / OPTICS</span><b>折光镜片</b></div></section>
  <section class="entry-section"><h2>可用入口</h2><div v-if="entries.length" class="tiles">
    <RouterLink v-for="entry in entries" :key="entry.name" class="tile" :to="{name:entry.name}"><span class="tile-top"><span class="tile-icon"><svg class="ico" aria-hidden="true"><use :href="`#i-${entry.icon}`" /></svg></span><span class="fine">{{ entry.mark }}</span><span class="arr"><svg class="ico" aria-hidden="true"><use href="#i-arrow" /></svg></span></span><strong>{{ entry.label }}</strong><span class="desc">{{ entry.desc }}</span></RouterLink>
  </div><div v-else class="empty-panel"><h3>暂无管理权限</h3><p>你可以修改自己的密码或退出登录。</p><RouterLink :to="{name:'password'}">修改密码</RouterLink></div></section>
</template>
