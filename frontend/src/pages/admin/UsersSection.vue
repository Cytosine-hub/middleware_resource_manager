<template>
  <section class="utility-panel type-panel">
    <div class="section-toolbar">
      <div class="filters"></div>
      <div class="actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="list-panel">
      <div class="table-wrap">
        <table class="resource-table">
          <thead>
            <tr>
              <th>用户名</th>
              <th>账号</th>
              <th>角色</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>{{ user.displayName || user.username }}</td>
              <td>{{ user.username }}</td>
              <td>{{ user.role }}</td>
              <td>{{ formatDate(user.createdAt) }}</td>
              <td class="row-actions">
                <button class="ghost" @click="$emit('changeRole', user)">改角色</button>
                <button class="ghost" @click="$emit('resetPassword', user)">重置密码</button>
                <button v-if="user.role !== '系统管理员' || adminCount > 1" class="ghost danger" @click="$emit('deleteUser', user)">删除</button>
              </td>
            </tr>
            <tr v-if="users.length === 0">
              <td colspan="5" class="empty-state">暂无用户</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { formatDate } from '../../utils'

const props = defineProps({ users: { type: Array, default: () => [] } })
defineEmits(['changeRole', 'resetPassword', 'deleteUser'])

const adminCount = computed(() => props.users.filter(u => u.role === '系统管理员').length)
</script>
