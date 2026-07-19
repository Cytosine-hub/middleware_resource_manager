<template>
  <section class="workspace job-workspace">
    <JobNavigation :model-value="job.id" @update:model-value="openJob" />
    <main class="job-workspace-main">
      <header class="job-workspace-header">
        <div class="job-mark">{{ job.shortName }}</div>
        <div>
          <p class="eyebrow">{{ job.englishName }}</p>
          <h2>{{ job.name }}</h2>
          <p>{{ job.description }}</p>
        </div>
      </header>

      <slot v-if="feature" :feature="feature" />
      <div v-else class="job-feature-grid">
        <article v-for="item in job.features" :key="item.id" class="job-feature-card" @click="openFeature(item.id)">
          <span>{{ item.icon }}</span>
          <div><h3>{{ item.name }}</h3><p>{{ item.description }}</p></div>
          <BaseButton variant="ghost">进入</BaseButton>
        </article>
        <EmptyState v-if="job.features.length === 0" message="该岗位能力正在建设中，可按独立接口配置接入后端服务。" />
      </div>
    </main>
  </section>
</template>

<script setup>
import JobNavigation from './JobNavigation.vue'
import BaseButton from '../../components/ui/BaseButton.vue'
import EmptyState from '../../components/ui/EmptyState.vue'

const props = defineProps({ job: { type: Object, required: true }, feature: { type: String, default: null } })
const emit = defineEmits(['navigate'])

function openJob(jobId) {
  if (jobId === 'all') emit('navigate', 'home')
  else emit('navigate', `jobs/${jobId}`)
}

function openFeature(featureId) {
  emit('navigate', `jobs/${props.job.id}/${featureId}`)
}
</script>

<style scoped>
.job-workspace { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: var(--space-xl); padding-top: var(--space-xl); }
.job-workspace-main { display: grid; align-content: start; gap: var(--space-xl); min-width: 0; }
.job-workspace-header { display: flex; align-items: center; gap: var(--space-lg); border: 1px solid var(--color-border); border-radius: var(--radius-xl); padding: var(--space-xl); background: var(--color-bg); }
.job-workspace-header h2, .job-workspace-header p { margin: 0; }
.job-workspace-header p:last-child { margin-top: var(--space-sm); color: var(--color-text-secondary); }
.job-mark { display: grid; place-items: center; width: 64px; height: 64px; border-radius: var(--radius-lg); color: var(--color-text-inverse); background: var(--color-primary); font-size: var(--text-xl); font-weight: 800; }
.job-feature-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-lg); }
.job-feature-card { display: grid; grid-template-columns: auto 1fr auto; gap: var(--space-md); align-items: center; border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: var(--space-xl); background: var(--color-bg); cursor: pointer; }
.job-feature-card:hover { border-color: var(--color-primary-200); box-shadow: var(--shadow-md); }
.job-feature-card h3, .job-feature-card p { margin: 0; }
.job-feature-card p { margin-top: var(--space-xs); color: var(--color-text-secondary); }
.job-feature-card > span { font-size: var(--text-2xl); }
@media (max-width: 760px) {
  .job-workspace { grid-template-columns: 1fr; }
  .job-feature-grid { grid-template-columns: 1fr; }
}
</style>
