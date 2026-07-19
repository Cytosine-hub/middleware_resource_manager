<template>
  <!--
    岗位专属模块页面（中间件/数据库/主机/网络/网络安全）。
    五大岗位共用本组件（代码复用 + UI 一致），仅通过 roleId/category 区分；
    中间件岗位承接原门户「常用命令」。各岗位内容通过 category 与门户后端隔离，
    也可在此接入岗位自己的独立后端服务。
  -->
  <div class="role-module">
    <div v-if="!role" class="role-missing">
      <p>未找到对应岗位模块。</p>
      <button class="link-btn" @click="$emit('home')">返回门户首页</button>
    </div>

    <template v-else>
      <header class="role-module-header" :data-role-id="role.id">
        <div>
          <p class="eyebrow">岗位专属工作台</p>
          <h2>{{ role.label }}岗位</h2>
          <p class="role-module-sub">{{ role.label }}岗位专属入口，聚合本岗位常用命令与资源。</p>
        </div>
        <span class="role-badge">{{ role.category }}</span>
      </header>

      <nav class="role-quick-links">
        <button @click="$emit('open-downloads', role.category)">本岗位软件下载</button>
        <button @click="$emit('open-standards', role.category)">本岗位标准发布</button>
        <button @click="$emit('open-forum', role.category)">本岗位论坛</button>
      </nav>

      <section class="role-section">
        <div class="role-section-head">
          <h3>常用命令</h3>
          <span class="muted">{{ role.label }}岗位常用运维命令，可搜索、复制</span>
        </div>
        <CommonCommandPanel :category="role.category" :notify="notify" />
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { request } from '../api'
import CommonCommandPanel from './CommonCommandPanel.vue'

const props = defineProps({
  roleId: { type: String, required: true },
  auth: { type: Object, default: () => ({}) },
  notify: { type: Function, default: null }
})
defineEmits(['home', 'open-downloads', 'open-standards', 'open-forum'])

const FALLBACK = [
  { id: 'middleware', label: '中间件', category: '中间件' },
  { id: 'database', label: '数据库', category: '数据库' },
  { id: 'host', label: '主机', category: '主机' },
  { id: 'network', label: '网络', category: '网络' },
  { id: 'security', label: '网络安全', category: '安全' }
]
const roles = ref(FALLBACK)

const role = computed(() => roles.value.find(r => r.id === props.roleId) || null)

async function loadRoles() {
  try {
    const data = await request('/api/public/portal/roles', { token: null })
    if (Array.isArray(data) && data.length) roles.value = data
  } catch {
    roles.value = FALLBACK
  }
}

onMounted(loadRoles)
watch(() => props.roleId, () => {})
</script>

<style scoped>
.role-module { display: flex; flex-direction: column; gap: 20px; }
.role-module-header {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px;
  background: linear-gradient(120deg, #1a3650, #1a4a6e); color: #fff;
  padding: 28px 26px; border-radius: 10px;
}
.role-module-header .eyebrow { color: #9cc0dd; margin: 0 0 6px; }
.role-module-header h2 { margin: 0; font-size: 24px; }
.role-module-sub { margin: 8px 0 0; color: #bcd4e8; font-size: 14px; }
.role-badge {
  background: rgba(255,255,255,0.16); color: #fff; padding: 6px 14px;
  border-radius: 999px; font-size: 14px; white-space: nowrap;
}
.role-quick-links { display: flex; flex-wrap: wrap; gap: 10px; }
.role-quick-links button {
  border: 1px solid #d5ddeb; background: #fff; color: #2356a5;
  border-radius: 6px; padding: 8px 16px; font-size: 14px; cursor: pointer;
}
.role-quick-links button:hover { background: #eef4fb; }
.role-section { border: 1px solid #e3e9f1; border-radius: 10px; background: #f8fafc; padding: 18px 20px; }
.role-section-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.role-section-head h3 { margin: 0; font-size: 17px; color: #1f2d3d; }
.muted { color: #8896a7; font-size: 13px; }
.role-missing { padding: 60px 20px; text-align: center; color: #67768a; }
.link-btn { border: none; background: none; color: #2356a5; cursor: pointer; text-decoration: underline; }
</style>
