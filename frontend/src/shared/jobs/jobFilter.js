export const ALL_JOBS_ID = 'all'

export const jobAliases = {
  middleware: ['middleware', '中间件'],
  database: ['database', '数据库', 'db'],
  host: ['host', '主机', 'server', '服务器'],
  network: ['network', '网络'],
  'network-security': ['network-security', 'network security', '网络安全', '安全']
}

export function normalizeJobId(jobId) {
  return jobId === ALL_JOBS_ID || Object.hasOwn(jobAliases, jobId) ? jobId : ALL_JOBS_ID
}

export function matchesJob(value, jobId) {
  if (normalizeJobId(jobId) === ALL_JOBS_ID) return true
  const values = Array.isArray(value) ? value : [value]
  const aliases = jobAliases[jobId].map((alias) => alias.toLowerCase())
  return values.some((item) => {
    const normalized = String(item || '').trim().toLowerCase()
    return aliases.includes(normalized)
  })
}

export function filterItemsByJob(items, jobId, categoryAccessor) {
  const normalizedJobId = normalizeJobId(jobId)
  if (normalizedJobId === ALL_JOBS_ID) return items
  return items.filter((item) => matchesJob(categoryAccessor(item), normalizedJobId))
}
