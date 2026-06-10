<template>
  <section class="word-preview-page">
    <div class="word-preview-toolbar">
      <div class="toolbar-left">
        <span class="preview-title">{{ docInfo.title || effectiveOriginalFileName || '文档预览' }}</span>
        <span v-if="effectiveOriginalFileName" class="preview-file-name muted">{{ effectiveOriginalFileName }}</span>
      </div>
      <div class="toolbar-actions">
        <button class="ghost" @click="$emit('back')">返回列表</button>
        <button v-if="canManage" class="ghost" :disabled="replacing" @click="replaceFileInput.click()">{{ replacing ? '上传中...' : '替换文档' }}</button>
        <button v-if="canManage" class="ghost" @click="openInfoDialog">编辑信息</button>
        <input ref="replaceFileInput" type="file" accept=".doc,.docx,.pdf" class="hidden-file-input" @change="replaceDocument" />
      </div>
    </div>

    <div class="word-preview-layout">
      <aside v-if="documents.length" class="word-preview-dir post-dir-panel">
        <div class="post-dir-header">
          <h3>文档列表</h3>
        </div>
        <div class="post-dir-list">
          <button
            v-for="doc in documents"
            :key="doc.id"
            :class="['post-dir-item', { active: String(doc.id) === String(docInfo.id) }]"
            @click="$emit('preview', doc)"
          >{{ displayTitle(doc) }}</button>
        </div>
      </aside>

      <div class="preview-workspace">
        <PdfDocumentPreview
          v-if="isPdf"
          class="preview-body"
          :src="rawPreviewUrl"
          fetch-blob
        />
        <WordDocumentPreview
          v-else
          class="preview-body"
          :file-name="effectiveStoredFileName"
          :raw-url="rawPreviewUrl"
          :preview-url="htmlPreviewUrl"
          :parameters="parameters"
          fetch-blob
        />
      </div>
    </div>

    <!-- 文档信息弹窗 -->
    <div v-if="showInfoDialog" class="meta-dialog-backdrop" @click.self="onBackdropClick">
      <form class="meta-dialog" @submit.prevent="saveInfo">
        <h3>{{ isNewDoc ? '填写文档信息' : '编辑文档信息' }}</h3>
        <div class="meta-dialog-grid">
          <label>标题<input v-model.trim="infoForm.title" required maxlength="160" placeholder="文档标题" /></label>
          <label>类型
            <select v-model="infoForm.documentType">
              <option value="MANUAL">手册</option>
              <option value="ARTICLE">文章</option>
            </select>
          </label>
          <label>分类
            <select v-model="infoForm.category" required @change="onCategoryChange">
              <option value="">请选择</option>
              <option v-for="cat in softwareTypeCategories" :key="cat" :value="cat">{{ cat }}</option>
            </select>
          </label>
          <label>软件
            <select v-model="infoForm.software" :disabled="!infoForm.category" required @change="onSoftwareChange">
              <option value="">请选择</option>
              <option v-for="sw in softwareOptions" :key="sw" :value="sw">{{ sw }}</option>
            </select>
          </label>
          <label class="wide">关联标准
            <select v-model="infoForm.relatedStandardDocumentId" :disabled="!infoForm.software" required>
              <option value="">请选择</option>
              <option v-for="std in standardOptions" :key="std.id" :value="std.id">
                {{ [std.category, std.software, std.softwareVersion].filter(Boolean).join(' / ') }} · V{{ std.version || '-' }}
              </option>
            </select>
          </label>
          <label class="wide">摘要<input v-model.trim="infoForm.summary" maxlength="500" placeholder="文档摘要（可选）" /></label>
        </div>
        <p v-if="saveError" class="meta-error">{{ saveError }}</p>
        <div class="meta-dialog-actions">
          <button v-if="!isNewDoc" type="button" class="ghost" @click="closeInfoDialog">取消</button>
          <button v-if="isNewDoc" type="button" class="ghost danger" @click="$emit('back')">放弃</button>
          <button type="submit" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { request } from '../api'
import PdfDocumentPreview from './previews/PdfDocumentPreview.vue'
import WordDocumentPreview from './previews/WordDocumentPreview.vue'

const props = defineProps({
  storedFileName: { type: String, required: true },
  docId: { type: [String, Number], default: null },
  isNewDoc: { type: Boolean, default: false },
  initialContent: { type: String, default: '' },
  initialTitle: { type: String, default: '' },
  originalFileName: { type: String, default: '' },
  relatedStandardDocumentId: { type: [String, Number], default: null },
  canManage: { type: Boolean, default: true },
  softwareTypeCategories: { type: Array, default: () => [] },
  softwareTypes: { type: Array, default: () => [] },
  standardDocumentOptions: { type: Array, default: () => [] },
  documents: { type: Array, default: () => [] },
  notify: { type: Function, default: (msg, type) => type === 'error' ? alert(msg) : null }
})

const emit = defineEmits(['back', 'preview', 'saved', 'replaced'])

const saving = ref(false)
const saveError = ref('')
const showInfoDialog = ref(false)
const parameters = ref([])
const replacing = ref(false)
const replaceFileInput = ref(null)

// 本地副本：替换文档后更新，避免重新挂载组件
const effectiveStoredFileName = ref(props.storedFileName)
const effectiveOriginalFileName = ref(props.originalFileName)

const docInfo = reactive({ title: props.initialTitle || '', id: props.docId || null })

const infoForm = reactive({
  title: '', documentType: 'MANUAL', summary: '',
  category: '', software: '', relatedStandardDocumentId: ''
})

const isPdf = computed(() => effectiveStoredFileName.value.toLowerCase().endsWith('.pdf'))
const rawPreviewUrl = computed(() =>
  `/api/admin/standard-documents/raw?storedFileName=${encodeURIComponent(effectiveStoredFileName.value)}`
)
const htmlPreviewUrl = computed(() =>
  `/api/admin/standard-documents/preview?storedFileName=${encodeURIComponent(effectiveStoredFileName.value)}`
)

const softwareOptions = computed(() => {
  if (!infoForm.category) return []
  return [...new Set(props.softwareTypes.filter(t => t.category === infoForm.category).map(t => t.name))]
})

const standardOptions = computed(() =>
  props.standardDocumentOptions.filter(s => s.category === infoForm.category && s.software === infoForm.software)
)

function displayTitle(doc) {
  if (!doc) return ''
  return doc.title || [doc.category, doc.software, doc.softwareVersion].filter(Boolean).join(' / ') || '未命名'
}

function onCategoryChange() {
  infoForm.software = ''
  infoForm.relatedStandardDocumentId = ''
}

function onSoftwareChange() {
  infoForm.relatedStandardDocumentId = ''
}

function openInfoDialog() {
  saveError.value = ''
  if (docInfo.id) {
    loadExistingDocInfo()
  } else {
    infoForm.title = docInfo.title || props.initialTitle || ''
    infoForm.documentType = 'MANUAL'
    infoForm.summary = ''
    infoForm.category = ''
    infoForm.software = ''
    infoForm.relatedStandardDocumentId = ''
  }
  showInfoDialog.value = true
}

async function loadExistingDocInfo() {
  try {
    const doc = await request(`/api/admin/standard-documents/${docInfo.id}`)
    infoForm.title = doc.title || ''
    infoForm.documentType = doc.documentType || 'MANUAL'
    infoForm.summary = doc.summary || ''
    infoForm.category = doc.category || ''
    infoForm.software = doc.software || ''
    infoForm.relatedStandardDocumentId = doc.relatedStandardDocumentId || ''
  } catch (e) {
    props.notify(e.message || '加载文档信息失败', 'error')
  }
}

function closeInfoDialog() {
  if (!props.isNewDoc) showInfoDialog.value = false
}

function onBackdropClick() {
  if (!props.isNewDoc) closeInfoDialog()
}

async function saveInfo() {
  if (!infoForm.relatedStandardDocumentId) {
    saveError.value = '请选择关联标准'
    return
  }
  saveError.value = ''
  saving.value = true
  try {
    const body = {
      title: infoForm.title,
      documentType: infoForm.documentType,
      summary: infoForm.summary || '',
      relatedStandardDocumentId: infoForm.relatedStandardDocumentId,
      content: props.initialContent || '',
      storedFileName: effectiveStoredFileName.value,
      originalFileName: effectiveOriginalFileName.value
    }
    let result
    if (docInfo.id) {
      result = await request(`/api/admin/standard-documents/${docInfo.id}`, { method: 'PUT', body })
    } else {
      result = await request('/api/admin/standard-documents', { method: 'POST', body })
    }
    docInfo.title = result.title
    docInfo.id = result.id
    showInfoDialog.value = false
    props.notify('文档信息已保存', 'success')
    emit('saved')
  } catch (e) {
    saveError.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function loadParameters() {
  if (!props.relatedStandardDocumentId) {
    parameters.value = []
    return
  }
  try {
    parameters.value = await request(
      `/api/admin/standard-parameters?parameterStandardId=${props.relatedStandardDocumentId}`
    )
  } catch {
    parameters.value = []
  }
}

function syncPreviewState() {
  effectiveStoredFileName.value = props.storedFileName
  effectiveOriginalFileName.value = props.originalFileName
  docInfo.id = props.docId || null
  docInfo.title = props.initialTitle || ''
}

async function replaceDocument(event) {
  if (!props.canManage) return
  const file = event.target.files[0]
  if (!file) return
  replacing.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('convertToMarkdown', 'false')
    const result = await request('/api/admin/standard-documents/upload', { method: 'POST', body: fd })
    if (!result.storedFileName) { props.notify('上传失败，未获得文件名', 'error'); return }
    effectiveStoredFileName.value = result.storedFileName
    effectiveOriginalFileName.value = result.originalFileName || file.name
    if (docInfo.id) {
      if (!infoForm.relatedStandardDocumentId) await loadExistingDocInfo()
      await request(`/api/admin/standard-documents/${docInfo.id}`, {
        method: 'PUT',
        body: {
          title: infoForm.title || docInfo.title,
          documentType: infoForm.documentType || 'MANUAL',
          summary: infoForm.summary || '',
          relatedStandardDocumentId: infoForm.relatedStandardDocumentId,
          content: '',
          storedFileName: result.storedFileName,
          originalFileName: result.originalFileName || file.name
        }
      })
    }
    emit('replaced', { storedFileName: result.storedFileName, originalFileName: result.originalFileName || file.name })
    props.notify('文档已替换，正在刷新预览...', 'success')
  } catch (e) {
    props.notify(e.message || '替换失败', 'error')
  } finally {
    replacing.value = false
    event.target.value = ''
  }
}

onMounted(async () => {
  syncPreviewState()
  await loadParameters()
  if (props.canManage && props.isNewDoc) {
    infoForm.title = props.initialTitle || ''
    infoForm.documentType = 'MANUAL'
    showInfoDialog.value = true
  } else if (props.docId) {
    docInfo.id = props.docId
    docInfo.title = props.initialTitle
    if (props.canManage) {
      openInfoDialog()
    }
  }
})

watch(
  () => [
    props.storedFileName,
    props.originalFileName,
    props.docId,
    props.initialTitle,
    props.relatedStandardDocumentId
  ],
  async () => {
    syncPreviewState()
    await loadParameters()
  }
)
</script>

<style scoped>
.word-preview-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 88px);
  min-height: 0;
  background: var(--color-bg);
  overflow: hidden;
}
.word-preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}
.toolbar-left { display: flex; align-items: center; gap: 12px; }
.preview-title { font-size: var(--text-lg); font-weight: 700; color: var(--color-text); }
.preview-file-name { font-size: var(--text-sm); }
.toolbar-actions { display: flex; gap: 8px; }
.toolbar-actions button { font-size: var(--text-sm); min-height: 32px; padding: 0 14px; }
.word-preview-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: var(--space-lg);
  flex: 1 1 auto;
  min-height: 0;
  padding: var(--space-lg) clamp(18px, 4vw, 48px) var(--space-xl);
  background: var(--color-bg-page);
  overflow: hidden;
}
.word-preview-dir {
  position: static;
  max-height: 100%;
  min-height: 0;
  overflow-y: auto;
}
.preview-workspace {
  display: flex;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.preview-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}
@media (max-width: 900px) {
  .word-preview-layout {
    grid-template-columns: 1fr;
  }
  .word-preview-dir {
    display: none;
  }
}
/* 文档信息弹窗 */
.meta-dialog-backdrop {
  position: fixed; inset: 0; background: var(--color-overlay, rgba(0,0,0,0.4));
  display: flex; align-items: center; justify-content: center; z-index: 200;
}
.meta-dialog {
  background: var(--color-bg); border-radius: var(--radius-lg);
  padding: var(--space-xl); width: 520px; max-width: min(90vw, 560px);
  box-shadow: var(--shadow-lg, 0 8px 32px rgba(0,0,0,0.18));
}
.meta-dialog h3 { margin: 0 0 var(--space-lg); font-size: var(--text-lg); }
.meta-dialog-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-lg);
}
.meta-dialog-grid label { display: flex; flex-direction: column; gap: var(--space-xs); font-size: var(--text-sm); color: var(--color-text-secondary); }
.meta-dialog-grid label.wide { grid-column: 1 / -1; }
.meta-dialog-grid input, .meta-dialog-grid select {
  padding: var(--space-sm) var(--space-md); border: 1px solid var(--color-border);
  border-radius: var(--radius-md); font-size: var(--text-base);
  background: var(--color-bg); color: var(--color-text);
}
.meta-dialog-grid input:focus, .meta-dialog-grid select:focus { outline: none; border-color: var(--color-primary); }
.meta-error { color: var(--color-danger); font-size: var(--text-sm); margin: 0 0 var(--space-md); }
.meta-dialog-actions { display: flex; justify-content: flex-end; gap: var(--space-sm); }
.hidden-file-input { display: none; }
</style>
