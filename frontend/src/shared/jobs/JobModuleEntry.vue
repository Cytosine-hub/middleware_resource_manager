<template>
  <JobWorkspace :job="job" :feature="feature" @navigate="$emit('navigate', $event)">
    <component
      :is="activeFeature.component"
      v-if="activeFeature"
      v-bind="featureProps"
    />
    <EmptyState v-else-if="feature" message="该岗位功能不存在或暂未开放。" />
  </JobWorkspace>
</template>

<script setup>
import { computed } from 'vue'
import EmptyState from '../../components/ui/EmptyState.vue'
import JobWorkspace from './JobWorkspace.vue'

const props = defineProps({
  job: { type: Object, required: true },
  feature: { type: String, default: null },
  context: { type: Object, default: () => ({}) }
})

defineEmits(['navigate'])

const activeFeature = computed(() => props.feature ? props.job.resolveFeature(props.feature) : null)
const featureProps = computed(() => activeFeature.value?.getProps?.({
  context: props.context,
  job: props.job
}) || {})
</script>
