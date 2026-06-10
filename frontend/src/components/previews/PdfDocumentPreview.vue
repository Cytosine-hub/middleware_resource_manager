<template>
  <div class="pdf-preview">
    <div v-if="loading" class="preview-state">加载中...</div>
    <div v-else-if="error" class="preview-state error">{{ error }}</div>
    <iframe v-else :src="viewerSrc" class="pdf-preview-frame" title="PDF 预览"></iframe>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { fetchBinary } from '../../api'

const props = defineProps({
  src: { type: String, required: true },
  fetchBlob: { type: Boolean, default: false }
})

const loading = ref(false)
const error = ref('')
const viewerSrc = ref('')
let objectUrl = ''

function revokeObjectUrl() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
}

async function loadPdf() {
  revokeObjectUrl()
  error.value = ''
  viewerSrc.value = ''
  if (!props.src) return
  if (!props.fetchBlob) {
    viewerSrc.value = props.src
    return
  }
  loading.value = true
  try {
    const blob = await fetchBinary(props.src)
    objectUrl = URL.createObjectURL(blob)
    viewerSrc.value = objectUrl
  } catch (e) {
    error.value = e.message || '加载 PDF 预览失败'
  } finally {
    loading.value = false
  }
}

watch(() => [props.src, props.fetchBlob], loadPdf, { immediate: true })
onBeforeUnmount(revokeObjectUrl)
</script>

<style scoped>
.pdf-preview {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  background: var(--color-bg-secondary);
}

.pdf-preview-frame {
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  border: 0;
  background: var(--color-bg-secondary);
}

.preview-state {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  font-size: var(--text-base);
}

.preview-state.error {
  color: var(--color-danger);
}
</style>
