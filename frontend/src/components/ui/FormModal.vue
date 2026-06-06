<template>
  <BaseModal :modelValue="modelValue" @update:modelValue="$emit('update:modelValue', $event)" :title="title" :width="width">
    <form @submit.prevent="$emit('submit')">
      <slot />
    </form>
    <template #footer>
      <slot name="actions">
        <BaseButton type="submit" variant="primary" @click="$emit('submit')">{{ submitText }}</BaseButton>
        <BaseButton variant="ghost" @click="$emit('update:modelValue', false)">取消</BaseButton>
      </slot>
    </template>
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
