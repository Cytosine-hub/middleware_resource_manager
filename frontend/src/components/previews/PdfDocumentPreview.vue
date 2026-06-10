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

function getActiveReplacements() {
  return props.parameters
    .filter(p => p?.active !== false && p?.code)
    .map(p => ({ placeholder: `{{${p.code}}}`, value: p.value || '' }))
}

function resetTextLayer(textLayer) {
  textLayer.querySelectorAll('.pdf-placeholder-cover').forEach(el => el.remove())
  textLayer.querySelectorAll('span').forEach(span => {
    if (span.dataset.originalText) {
      span.textContent = span.dataset.originalText
    } else {
      span.dataset.originalText = span.textContent || ''
    }
    span.classList.remove('pdf-placeholder-hidden')
  })
}

function findMatches(text, replacements) {
  const matches = []
  for (const replacement of replacements) {
    let fromIndex = 0
    while (fromIndex < text.length) {
      const start = text.indexOf(replacement.placeholder, fromIndex)
      if (start === -1) break
      const end = start + replacement.placeholder.length
      matches.push({ start, end, value: replacement.value })
      fromIndex = end
    }
  }
  return matches.sort((a, b) => a.start - b.start)
}

function createReplacementCover(textLayer, spans, value) {
  const layerRect = textLayer.getBoundingClientRect()
  const rects = spans
    .map(span => span.getBoundingClientRect())
    .filter(rect => rect.width > 0 && rect.height > 0)
  if (!rects.length) return

  const left = Math.min(...rects.map(rect => rect.left)) - layerRect.left
  const top = Math.min(...rects.map(rect => rect.top)) - layerRect.top
  const right = Math.max(...rects.map(rect => rect.right)) - layerRect.left
  const bottom = Math.max(...rects.map(rect => rect.bottom)) - layerRect.top
  const firstStyle = window.getComputedStyle(spans[0])
  const cover = document.createElement('span')

  cover.className = 'pdf-placeholder-cover'
  cover.textContent = value
  cover.style.left = `${left}px`
  cover.style.top = `${top}px`
  cover.style.width = `${Math.max(right - left, 1)}px`
  cover.style.height = `${Math.max(bottom - top, 1)}px`
  cover.style.fontFamily = firstStyle.fontFamily
  cover.style.fontSize = firstStyle.fontSize
  cover.style.fontWeight = firstStyle.fontWeight
  cover.style.letterSpacing = firstStyle.letterSpacing
  textLayer.appendChild(cover)
}

async function applyParamReplacements() {
  await nextTick()
  const root = previewRoot.value
  if (!root) return

  const replacements = getActiveReplacements()
  for (const textLayer of root.querySelectorAll('.textLayer')) {
    resetTextLayer(textLayer)
    if (!replacements.length) continue

    let pageText = ''
    const tokens = Array.from(textLayer.querySelectorAll('span'))
      .filter(span => !span.classList.contains('pdf-placeholder-cover'))
      .map(span => {
        const text = span.dataset.originalText || span.textContent || ''
        const token = { span, start: pageText.length, end: pageText.length + text.length }
        pageText += text
        return token
      })

    for (const match of findMatches(pageText, replacements)) {
      const matchedTokens = tokens.filter(token => token.start < match.end && token.end > match.start)
      if (!matchedTokens.length) continue

      const matchedSpans = matchedTokens.map(token => token.span)
      createReplacementCover(textLayer, matchedSpans, match.value)
      for (const span of matchedSpans) {
        span.classList.add('pdf-placeholder-hidden')
      }
    }
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

.pdf-preview-document :deep(.textLayer .pdf-placeholder-hidden) {
  visibility: hidden;
}

.pdf-preview-document :deep(.textLayer .pdf-placeholder-cover) {
  position: absolute;
  display: flex;
  align-items: center;
  color: var(--color-text) !important;
  background: var(--color-bg);
  opacity: 1;
  white-space: pre;
  z-index: 2;
  pointer-events: none;
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
