<template>
  <div class="empty-state">
    <div v-if="icon" class="empty-icon">{{ icon }}</div>
    <p class="empty-message">{{ message }}</p>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  message: { type: String, default: '暂无数据' },
  icon: { type: String, default: '' }
})
</script>

<style scoped>
.empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: var(--space-3xl) var(--space-xl);
  color: var(--color-text-tertiary);
  text-align: center;
}
.empty-icon { font-size: 48px; margin-bottom: var(--space-lg); opacity: 0.6; }
.empty-message { font-size: var(--text-base); margin: 0; }
</style>
