import { ref } from 'vue'
import { normalizeJobId } from './jobFilter.js'

const STORAGE_KEY = 'mrm.publicJobFilter'

function readStoredJob() {
  if (typeof window === 'undefined') return 'all'
  return normalizeJobId(window.localStorage.getItem(STORAGE_KEY))
}

export function useJobFilter() {
  const selectedJob = ref(readStoredJob())

  function selectJob(jobId) {
    selectedJob.value = normalizeJobId(jobId)
    if (typeof window !== 'undefined') window.localStorage.setItem(STORAGE_KEY, selectedJob.value)
  }

  return { selectedJob, selectJob }
}
