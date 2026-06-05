<template>
  <BaseModal :modelValue="modelValue" @update:modelValue="$emit('update:modelValue', $event)" :title="title" :width="width">
    <form @submit.prevent="$emit('submit')" class="form-modal-inner">
      <div class="form-modal-body">
        <slot />
      </div>
      <div class="form-actions">
        <slot name="actions">
          <BaseButton type="submit" variant="primary">{{ submitText }}</BaseButton>
          <BaseButton variant="ghost" type="button" @click="$emit('update:modelValue', false)">取消</BaseButton>
        </slot>
      </div>
    </form>
  </BaseModal>
</template>

<script setup>
import BaseModal from './BaseModal.vue'
import BaseButton from './BaseButton.vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: String, default: '520px' },
  submitText: { type: String, default: '保存' }
})
defineEmits(['update:modelValue', 'submit'])
</script>

<style scoped>
.form-modal-inner {
  display: flex; flex-direction: column; max-height: 70vh;
}
.form-modal-body {
  flex: 1; overflow-y: auto; min-height: 0;
}
.form-actions {
  display: flex; justify-content: flex-end; gap: var(--space-sm);
  margin-top: var(--space-xl);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}
</style>
