<template>
  <section class="utility-panel type-panel">
    <div class="list-panel type-list-panel">
      <div class="filters document-filters">
        <select :value="filterStatus" @change="$emit('filterChange', $event.target.value)">
          <option value="">全部状态</option>
          <option value="PENDING">待审核</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option>
        </select>
      </div>
      <div class="type-list">
        <article v-for="record in reviews" :key="record.id" class="parameter-item document-item">
          <div>
            <strong>{{ record.documentType === 'PARAMETER_STANDARD' ? [record.category, record.software].filter(Boolean).join(' / ') : record.documentTitle }}</strong>
            <p>
              <span :class="['status', statusClass(record.status)]">{{ record.statusLabel }}</span>
              V{{ record.documentVersion || '-' }} · {{ record.category || '-' }} / {{ record.software || '-' }}
            </p>
            <p>提交人：{{ record.submitterDisplayName || record.submitterUsername }} · {{ formatTime(record.submittedAt) }}</p>
          </div>
          <button class="ghost" @click="$emit('viewDetail', record)">查看</button>
          <button v-if="record.status === 'PENDING' && canReview(record)" class="ghost" @click="$emit('viewDetail', record)">审核</button>
        </article>
        <p v-if="reviews.length === 0" class="empty-state">暂无审核记录。</p>
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
