<script setup lang="ts">
import { setTheme, theme } from '../composables/useTheme'
</script>

<template>
  <fieldset class="appearance" aria-label="界面主题" :data-theme-choice="theme">
    <legend class="sr">界面主题</legend>
    <label class="mode" v-for="option in [{ value: 'light' as const, text: '浅色', icon: 'sun' }, { value: 'dark' as const, text: '深色', icon: 'moon' }]" :key="option.value">
      <input type="radio" name="appearance" :value="option.value" :checked="theme === option.value" :aria-label="option.text" @change="setTheme(option.value)">
      <span class="mode-face"><svg class="ico" aria-hidden="true"><use :href="`#i-${option.icon}`" /></svg><span class="mode-text">{{ option.text }}</span></span>
    </label>
  </fieldset>
</template>
