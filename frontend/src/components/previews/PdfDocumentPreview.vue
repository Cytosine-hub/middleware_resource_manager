<template>
  <div class="pdf-preview">
    <div v-if="loading" class="preview-state">加载中...</div>
    <div v-else-if="error" class="preview-state error">{{ error }}</div>
    <div v-else-if="viewerSrc && PdfEmbedComponent" ref="previewRoot" class="pdf-preview-scroller">
      <component
        :is="PdfEmbedComponent"
        class="pdf-preview-document"
        text-layer
        :source="viewerSrc"
        @rendered="applyParamReplacements"
        @loading-failed="onRenderFailed"
        @rendering-failed="onRenderFailed"
      />
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import 'vue-pdf-embed/dist/styles/textLayer.css'
import { fetchBinary } from '../../api'

const props = defineProps({
  src: { type: String, required: true },
  fetchBlob: { type: Boolean, default: false },
  parameters: { type: Array, default: () => [] }
})

const loading = ref(false)
const error = ref('')
const viewerSrc = ref('')
const previewRoot = ref(null)
const PdfEmbedComponent = shallowRef(null)
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
  loading.value = true
  try {
    await ensurePdfComponent()
    if (!props.fetchBlob) {
      viewerSrc.value = props.src
      return
    }
    const blob = await fetchBinary(props.src)
    objectUrl = URL.createObjectURL(blob)
    viewerSrc.value = objectUrl
  } catch (e) {
    error.value = e.message || '加载 PDF 预览失败'
  } finally {
    loading.value = false
  }
}

async function ensurePdfComponent() {
  if (PdfEmbedComponent.value) return
  const module = await import('vue-pdf-embed')
  PdfEmbedComponent.value = module.default
}

function onRenderFailed(e) {
  error.value = e?.message || '加载 PDF 预览失败'
}

function applyParams(text, params) {
  let result = text || ''
  for (const p of params) {
    if (p?.active !== false && p?.code) {
      result = result.split(`{{${p.code}}}`).join(p.value || '')
    }
  }
  return result
}

async function applyParamReplacements() {
  await nextTick()
  const root = previewRoot.value
  if (!root) return

  const spans = root.querySelectorAll('.textLayer span')
  for (const span of spans) {
    const originalText = span.dataset.originalText || span.textContent || ''
    if (!span.dataset.originalText) {
      span.dataset.originalText = originalText
    }

    const replacedText = applyParams(originalText, props.parameters)
    span.textContent = replacedText
    span.classList.toggle('pdf-placeholder-replacement', replacedText !== originalText)
  }
}

watch(() => [props.src, props.fetchBlob], loadPdf, { immediate: true })
watch(() => JSON.stringify(props.parameters), applyParamReplacements)
onBeforeUnmount(revokeObjectUrl)
</script>

<style scoped>
.pdf-preview {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--color-bg-tertiary);
}

.pdf-preview-scroller {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-xl);
}

.pdf-preview-document {
  display: grid;
  width: 100%;
  justify-content: center;
  gap: var(--space-lg);
  min-width: 0;
}

.pdf-preview-document :deep(.vue-pdf-embed__page) {
  background: var(--color-bg);
  box-shadow: var(--shadow-md);
  max-width: 100%;
}

.pdf-preview-document :deep(canvas) {
  display: block;
  max-width: 100%;
  height: auto !important;
}

.pdf-preview-document :deep(.textLayer .pdf-placeholder-replacement) {
  color: var(--color-text) !important;
  background: var(--color-bg);
  opacity: 1;
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
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
