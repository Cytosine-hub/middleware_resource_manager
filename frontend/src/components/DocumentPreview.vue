<template>
  <div v-if="document" class="modal-backdrop doc-preview-backdrop">
    <div class="doc-preview-full">
      <div class="doc-preview-toolbar">
        <h3>文档预览</h3>
        <button type="button" class="ghost" @click="$emit('close')">返回列表</button>
      </div>
      <div class="doc-preview-layout">
        <aside class="post-dir-panel">
          <div class="post-dir-header">
            <h3>文档列表</h3>
          </div>
          <div class="post-dir-list">
            <button
              v-for="doc in documents"
              :key="doc.id"
              :class="['post-dir-item', { active: String(doc.id) === String(document?.id) }]"
              @click="$emit('preview', doc)"
            >{{ displayTitle(doc) }}</button>
          </div>
        </aside>
        <article ref="previewMain" class="doc-preview-main">
          <div class="post-article">
            <h1 class="post-title">{{ displayTitle(document) }}</h1>
            <div class="post-author-line">
              <span class="post-date">{{ documentTypeLabel(document.documentType) }}</span>
              <span class="post-date">{{ document.category || '-' }} / {{ document.software || '-' }}</span>
              <span class="post-date">{{ formatDate(document.updatedAt) }}</span>
            </div>
            <p v-if="document.summary" class="description" style="margin-bottom:16px">{{ document.summary }}</p>
            <MarkdownDocumentPreview class="post-body markdown-preview" variant="article" :html="renderedHtml" />
          </div>
        </article>
        <aside class="post-toc-panel" v-if="tocItems.length">
          <h4 class="toc-title">文档大纲</h4>
          <button
            v-for="item in tocItems"
            :key="item.id"
            :class="['toc-link', { active: activeTocId === item.id }]"
            :style="{ '--toc-level': item.level - 1 }"
            @click="scrollTo(item.id)"
          >{{ item.text }}</button>
        </aside>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { formatDate, renderMarkdown, documentTypeLabel } from '../utils'
import MarkdownDocumentPreview from './previews/MarkdownDocumentPreview.vue'

const props = defineProps({
  document: { type: Object, default: null },
  documents: { type: Array, default: () => [] },
  parameters: { type: Array, default: () => [] }
})
defineEmits(['close', 'preview'])

const activeTocId = ref('')
const previewMain = ref(null)
let scrollHandler = null

const renderedHtml = computed(() => {
  if (!props.document) return ''
  let rendered = props.document.renderedContent || props.document.content || ''
  for (const param of props.parameters) {
    rendered = rendered.split(`{{${param.code}}}`).join(param.value)
  }
  let html = renderMarkdown(rendered)
  let idx = 0
  html = html.replace(/<(h[1-3])([^>]*)>([\s\S]*?)<\/\1>/g, (_, tag, attrs, inner) => {
    if (/id=/.test(attrs)) return `<${tag}${attrs}>${inner}</${tag}>`
    return `<${tag}${attrs} id="pv-toc-${idx++}">${inner}</${tag}>`
  })
  return html
})

const tocItems = computed(() => {
  if (!props.document) return []
  const items = []
  const re = /<(h[1-3])[^>]*id="([^"]*)"[^>]*>([\s\S]*?)<\/\1>/g
  let m
  while ((m = re.exec(renderedHtml.value))) {
    const level = parseInt(m[1][1])
    const id = m[2]
    const text = m[3].replace(/<[^>]+>/g, '').trim()
    if (text) items.push({ level, id, text })
  }
  return items
})

function displayTitle(doc) {
  if (!doc) return ''
  return doc.title || [doc.category, doc.software, doc.softwareVersion].filter(Boolean).join(' / ') || '未命名'
}

function scrollTo(id) {
  const target = document.getElementById(id)
  const container = previewMain.value
  if (!target || !container) return
  container.scrollTo({
    top: target.offsetTop - container.offsetTop - 16,
    behavior: 'smooth'
  })
}

function initScrollSpy() {
  destroyScrollSpy()
  scrollHandler = () => {
    const items = tocItems.value
    if (!items.length) return
    let current = ''
    for (const item of items) {
      const el = document.getElementById(item.id)
      if (el && previewMain.value && el.offsetTop - previewMain.value.scrollTop <= 160) current = item.id
    }
    activeTocId.value = current
  }
  previewMain.value?.addEventListener('scroll', scrollHandler, { passive: true })
}

function destroyScrollSpy() {
  if (scrollHandler) {
    previewMain.value?.removeEventListener('scroll', scrollHandler)
    scrollHandler = null
  }
}

watch(() => props.document, (newDoc) => {
  if (newDoc) {
    setTimeout(initScrollSpy, 100)
  } else {
    destroyScrollSpy()
  }
})

onBeforeUnmount(destroyScrollSpy)
</script>
