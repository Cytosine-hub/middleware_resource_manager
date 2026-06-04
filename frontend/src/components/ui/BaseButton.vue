<template>
  <button
    :class="['btn', variant, size, { loading: loading }]"
    :disabled="disabled || loading"
    @click="$emit('click', $event)"
  >
    <span v-if="loading" class="btn-spinner"></span>
    <slot />
  </button>
</template>

<script setup>
defineProps({
  variant: { type: String, default: 'default' }, // default | primary | success | danger | ghost
  size: { type: String, default: 'md' },          // sm | md | lg
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})
defineEmits(['click'])
</script>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  color: var(--color-text);
  font-size: var(--text-base);
  line-height: var(--leading-normal);
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
}
.btn:hover:not(:disabled) { border-color: var(--color-border-hover); background: var(--color-bg-secondary); }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* Sizes */
.btn.sm { padding: var(--space-xs) var(--space-sm); font-size: var(--text-sm); }
.btn.md { padding: var(--space-sm) var(--space-lg); }
.btn.lg { padding: var(--space-md) var(--space-xl); font-size: var(--text-lg); }

/* Variants */
.btn.primary { background: var(--color-primary); color: var(--color-text-inverse); border-color: var(--color-primary); }
.btn.primary:hover:not(:disabled) { background: var(--color-primary-hover); border-color: var(--color-primary-hover); }

.btn.success { background: var(--color-success); color: var(--color-text-inverse); border-color: var(--color-success); }
.btn.success:hover:not(:disabled) { background: #15803d; border-color: #15803d; }

.btn.danger { background: var(--color-danger); color: var(--color-text-inverse); border-color: var(--color-danger); }
.btn.danger:hover:not(:disabled) { background: #b91c1c; border-color: #b91c1c; }

.btn.ghost { background: transparent; border-color: transparent; color: var(--color-text-secondary); }
.btn.ghost:hover:not(:disabled) { background: var(--color-bg-tertiary); color: var(--color-text); }

/* Spinner */
.btn-spinner {
  width: 14px; height: 14px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
