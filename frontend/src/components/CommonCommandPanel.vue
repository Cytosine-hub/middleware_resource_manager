<template>
  <!-- 可复用的「常用命令」通用能力：各岗位模块按 category 复用同一组件，保证 UI 一致 -->
  <section class="cmd-panel">
    <div class="cmd-toolbar">
      <input
        v-model.trim="keyword"
        class="cmd-search"
        placeholder="搜索命令标题、内容或说明"
        @keyup.enter="reload"
      />
      <button class="cmd-btn" @click="reload">查询</button>
    </div>

    <div v-if="loading" class="cmd-empty">加载中…</div>
    <template v-else>
      <ul v-if="commands.length" class="cmd-list">
        <li v-for="cmd in commands" :key="cmd.id" class="cmd-item">
          <div class="cmd-item-head">
            <h4 class="cmd-title">{{ cmd.title }}</h4>
            <button class="cmd-copy" @click="copy(cmd)">{{ copiedId === cmd.id ? '已复制' : '复制' }}</button>
          </div>
          <pre class="cmd-code">{{ cmd.command }}</pre>
          <p v-if="cmd.description" class="cmd-desc">{{ cmd.description }}</p>
          <span v-if="cmd.tag" class="cmd-tag">{{ cmd.tag }}</span>
        </li>
      </ul>
      <div v-else class="cmd-empty">
        {{ keyword ? '未找到匹配的常用命令。' : '该岗位暂无常用命令。' }}
      </div>
    </template>
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { request } from '../api'

const props = defineProps({
  category: { type: String, required: true },
  notify: { type: Function, default: null }
})

const commands = ref([])
const keyword = ref('')
const loading = ref(false)
const copiedId = ref(null)

async function reload() {
  loading.value = true
  try {
    const params = new URLSearchParams({ category: props.category, keyword: keyword.value, size: 100 })
    const data = await request(`/api/module/commands?${params}`, { token: null })
    commands.value = Array.isArray(data?.content) ? data.content : []
  } catch {
    commands.value = []
  } finally {
    loading.value = false
  }
}

async function copy(cmd) {
  try {
    await navigator.clipboard.writeText(cmd.command)
    copiedId.value = cmd.id
    setTimeout(() => { if (copiedId.value === cmd.id) copiedId.value = null }, 1500)
    props.notify && props.notify('已复制到剪贴板')
  } catch {
    props.notify && props.notify('复制失败，请手动复制', 'error')
  }
}

onMounted(reload)
watch(() => props.category, () => { keyword.value = ''; reload() })
</script>

<style scoped>
.cmd-panel { display: flex; flex-direction: column; gap: 14px; }
.cmd-toolbar { display: flex; gap: 10px; }
.cmd-search { flex: 1; min-height: 38px; border: 1px solid #d5ddeb; border-radius: 6px; padding: 0 12px; font-size: 14px; }
.cmd-btn { min-height: 38px; padding: 0 18px; border: none; border-radius: 6px; background: #2356a5; color: #fff; cursor: pointer; }
.cmd-btn:hover { background: #1c4788; }
.cmd-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
.cmd-item { border: 1px solid #e3e9f1; border-radius: 8px; padding: 14px 16px; background: #fff; }
.cmd-item-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.cmd-title { margin: 0; font-size: 15px; color: #1f2d3d; }
.cmd-copy { border: 1px solid #d5ddeb; background: #f4f7fb; color: #33465c; border-radius: 6px; padding: 4px 12px; font-size: 13px; cursor: pointer; }
.cmd-copy:hover { background: #e6edf6; }
.cmd-code {
  margin: 10px 0 6px; padding: 10px 12px; background: #0f1b2d; color: #d6e3f5;
  border-radius: 6px; font-size: 13px; overflow-x: auto; white-space: pre-wrap; word-break: break-all;
}
.cmd-desc { margin: 6px 0 0; color: #67768a; font-size: 13px; }
.cmd-tag { display: inline-block; margin-top: 8px; padding: 2px 10px; border-radius: 999px; background: #eef2f7; color: #526071; font-size: 12px; }
.cmd-empty { padding: 32px; text-align: center; color: #8896a7; border: 1px dashed #d5ddeb; border-radius: 8px; background: #fbfcfe; }
</style>
