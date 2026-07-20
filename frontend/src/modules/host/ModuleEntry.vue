<template>
  <JobModuleEntry :job="job" :feature="feature" :context="context" @navigate="$emit('navigate', $event)" />
</template>

<script setup>
import JobModuleEntry from '../../shared/jobs/JobModuleEntry.vue'

defineProps({ job: { type: Object, required: true }, feature: { type: String, default: null }, context: { type: Object, default: () => ({}) } })
defineEmits(['navigate'])
</script>
