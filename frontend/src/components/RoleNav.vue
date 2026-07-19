<template>
  <!-- 公共区域（软件下载/标准发布/infra论坛）统一复用的左侧岗位导航，保证样式/位置/交互一致 -->
  <aside class="role-nav">
    <div class="role-nav-header">岗位导航</div>
    <nav class="role-nav-list">
      <button
        type="button"
        :class="['role-nav-item', { active: modelValue === '' }]"
        @click="select('')"
      >全部岗位</button>
      <button
        v-for="role in roles"
        :key="role.id"
        type="button"
        :class="['role-nav-item', { active: modelValue === role.category }]"
        @click="select(role.category)"
      >{{ role.label }}</button>
    </nav>
  </aside>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ROLE_FALLBACK, fetchRoles } from '../roles'

const props = defineProps({
  // 当前选中的岗位分类，'' 表示全部
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'change'])

// 岗位清单统一来自共享数据源（roles.js），三个公共模块共用，保证顺序/名称/交互一致
const roles = ref([...ROLE_FALLBACK])

function select(category) {
  if (category === props.modelValue) return
  emit('update:modelValue', category)
  emit('change', category)
}

onMounted(async () => {
  roles.value = await fetchRoles()
})
</script>

<style scoped>
.role-nav {
  position: sticky;
  top: 18px;
  align-self: start;
  border: 1px solid #e3e9f1;
  border-radius: 8px;
  background: #fff;
  padding: 14px 12px;
}
.role-nav-header {
  font-size: 13px;
  font-weight: 600;
  color: #8896a7;
  padding: 0 8px 10px;
  letter-spacing: 1px;
}
.role-nav-list { display: flex; flex-direction: column; gap: 4px; }
.role-nav-item {
  text-align: left;
  padding: 9px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #33465c;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.role-nav-item:hover { background: #eef2f7; }
.role-nav-item.active { background: #2356a5; color: #fff; font-weight: 500; }

@media (max-width: 900px) {
  .role-nav { position: static; }
  .role-nav-list { flex-direction: row; flex-wrap: wrap; }
}
</style>
