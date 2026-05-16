<template>
  <div v-if="page.totalElements > 0" class="pagination">
    <button :disabled="page.first" @click="$emit('change', page.page - 1)">上一页</button>
    <span>第 {{ page.page + 1 }} / {{ Math.max(page.totalPages, 1) }} 页，共 {{ page.totalElements }} 条</span>
    <button :disabled="page.last" @click="$emit('change', page.page + 1)">下一页</button>
  </div>
</template>

<script setup>
defineProps({
  page: {
    type: Object,
    required: true
  }
})

defineEmits(['change'])
</script>
