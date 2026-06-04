<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="modal-backdrop" @click.self="closeOnBackdrop && close()">
        <div class="modal-panel" :style="{ maxWidth: width }" role="dialog" aria-modal="true">
          <div v-if="title" class="modal-header">
            <h3>{{ title }}</h3>
            <button class="modal-close" @click="close()" aria-label="关闭">&times;</button>
          </div>
          <div class="modal-body">
            <slot />
          </div>
          <div v-if="$slots.footer" class="modal-footer">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: String, default: '520px' },
  closeOnBackdrop: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue'])

function close() { emit('update:modelValue', false) }

function handleEsc(e) {
  if (e.key === 'Escape' && props.modelValue) close()
}

onMounted(() => document.addEventListener('keydown', handleEsc))
onBeforeUnmount(() => document.removeEventListener('keydown', handleEsc))
</script>

<style scoped>
.modal-backdrop {
  position: fixed; inset: 0;
  background: var(--color-bg-overlay);
  display: flex; align-items: center; justify-content: center;
  z-index: var(--z-modal);
  padding: var(--space-xl);
}
.modal-panel {
  background: var(--color-bg);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xl);
  width: 100%;
  max-height: 85vh;
  display: flex; flex-direction: column;
}
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-lg) var(--space-xl);
  border-bottom: 1px solid var(--color-border);
}
.modal-header h3 { margin: 0; font-size: var(--text-lg); color: var(--color-text); }
.modal-close {
  background: none; border: none; font-size: 24px; cursor: pointer;
  color: var(--color-text-secondary); padding: 0; line-height: 1;
}
.modal-close:hover { color: var(--color-text); }
.modal-body { padding: var(--space-xl); overflow-y: auto; flex: 1; }
.modal-footer {
  display: flex; justify-content: flex-end; gap: var(--space-sm);
  padding: var(--space-lg) var(--space-xl);
  border-top: 1px solid var(--color-border);
}

/* Transition */
.modal-enter-active, .modal-leave-active { transition: opacity var(--transition-normal); }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
