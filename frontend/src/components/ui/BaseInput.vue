<template>
  <div class="input-group">
    <label v-if="label" class="input-label" :for="id">{{ label }}</label>
    <input
      :id="id"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      class="input-field"
      @input="$emit('update:modelValue', $event.target.value)"
    />
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: [String, Number], default: '' },
  label: { type: String, default: '' },
  type: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  id: { type: String, default: () => `input-${Math.random().toString(36).slice(2, 8)}` }
})
defineEmits(['update:modelValue'])
</script>

<style scoped>
.input-group { display: flex; flex-direction: column; gap: var(--space-xs); }
.input-label { font-size: var(--text-sm); color: var(--color-text-secondary); font-weight: 500; }
.input-field {
  padding: var(--space-sm) var(--space-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  color: var(--color-text);
  background: var(--color-bg);
  transition: border-color var(--transition-fast);
  outline: none;
}
.input-field:focus { border-color: var(--color-border-focus); box-shadow: 0 0 0 3px var(--color-primary-light); }
.input-field:disabled { background: var(--color-bg-tertiary); opacity: 0.7; }
.input-field::placeholder { color: var(--color-text-tertiary); }
</style>
