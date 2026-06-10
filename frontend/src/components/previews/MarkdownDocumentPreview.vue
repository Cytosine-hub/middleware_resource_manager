<template>
  <div :class="['markdown-document-preview', variant]" v-html="html"></div>
</template>

<script setup>
defineProps({
  html: { type: String, default: '' },
  variant: { type: String, default: 'default' }
})
</script>

<style scoped>
.markdown-document-preview {
  width: 100%;
  color: var(--color-text);
}

.markdown-document-preview.default {
  line-height: 1.8;
}

.markdown-document-preview.article {
  min-width: 0;
}
</style>
