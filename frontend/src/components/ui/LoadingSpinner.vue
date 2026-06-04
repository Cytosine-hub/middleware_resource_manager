<template>
  <div class="loading-spinner" :class="size">
    <div class="spinner"></div>
    <span v-if="text" class="loading-text">{{ text }}</span>
  </div>
</template>

<script setup>
defineProps({
  text: { type: String, default: '' },
  size: { type: String, default: 'md' } // sm | md | lg
})
</script>

<style scoped>
.loading-spinner { display: inline-flex; align-items: center; gap: var(--space-sm); }
.spinner {
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.loading-spinner.sm .spinner { width: 16px; height: 16px; border-width: 2px; }
.loading-spinner.md .spinner { width: 24px; height: 24px; }
.loading-spinner.lg .spinner { width: 36px; height: 36px; border-width: 4px; }
.loading-text { color: var(--color-text-secondary); font-size: var(--text-sm); }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
