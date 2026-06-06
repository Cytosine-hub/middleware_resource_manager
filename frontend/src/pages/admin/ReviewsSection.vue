<template>
  <section class="utility-panel type-panel">
    <div class="section-toolbar">
      <div class="filters">
        <select :value="filterStatus" @change="$emit('filterChange', $event.target.value)">
          <option value="">全部状态</option>
          <option value="PENDING">待审核</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option>
        </select>
      </div>
      <div class="actions"></div>
    </div>
    <div class="list-panel">
      <div class="table-wrap">
        <table class="resource-table">
          <thead>
            <tr>
              <th>文档名称</th>
              <th>类型</th>
              <th>版本</th>
              <th>分类 / 软件</th>
              <th>状态</th>
              <th>提交人</th>
              <th>提交时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in reviews" :key="record.id">
              <td>{{ record.documentType === 'PARAMETER_STANDARD' ? [record.category, record.software].filter(Boolean).join(' / ') : record.documentTitle }}</td>
              <td>{{ record.documentType === 'PARAMETER_STANDARD' ? '参数标准' : '标准文档' }}</td>
              <td>V{{ record.documentVersion || '-' }}</td>
              <td>{{ record.category || '-' }} / {{ record.software || '-' }}</td>
              <td><span :class="['status', statusClass(record.status)]">{{ record.statusLabel }}</span></td>
              <td>{{ record.submitterDisplayName || record.submitterUsername }}</td>
              <td>{{ formatTime(record.submittedAt) }}</td>
              <td class="row-actions">
                <button class="ghost" @click="$emit('viewDetail', record)">查看</button>
                <button v-if="record.status === 'PENDING' && canReview(record)" class="ghost" @click="$emit('viewDetail', record)">审核</button>
              </td>
            </tr>
            <tr v-if="reviews.length === 0">
              <td colspan="8" class="empty-state">暂无审核记录</td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination :page="pageInfo" @change="(p) => $emit('changePage', p)" />
    </div>
  </section>
</template>

<script setup>
import Pagination from '../../components/Pagination.vue'
import { formatTime } from '../../utils'

const props = defineProps({
  reviews: { type: Array, default: () => [] },
  filterStatus: { type: String, default: '' },
  pageInfo: { type: Object, default: () => ({}) },
  isSysAdmin: Boolean,
  isCategoryAdmin: Boolean,
  managedCategory: String
})
defineEmits(['filterChange', 'viewDetail', 'changePage'])

function statusClass(status) {
  if (status === 'PENDING') return 'warn'
  if (status === 'APPROVED') return 'ok'
  if (status === 'REJECTED') return 'off'
  return ''
}

function canReview(record) {
  if (props.isSysAdmin) return true
  if (props.isCategoryAdmin && props.managedCategory === record.category) return true
  return false
}
</script>
