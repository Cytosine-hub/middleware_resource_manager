import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

import { publicFeatures } from '../src/config/portalFeatures.js'
import { jobModules } from '../src/modules/index.js'
import { filterItemsByJob, normalizeJobId } from '../src/shared/jobs/jobFilter.js'
import { parseHashRoute } from '../src/composables/useRoute.js'

const readSource = (path) => readFile(new URL(path, import.meta.url), 'utf8')

test('TC-PORTAL-001 (TC-01) 首页自然划分公共功能与五大岗位入口', () => {
  assert.deepEqual(publicFeatures.map(({ id }) => id), ['downloads', 'standards', 'forum'])
  assert.deepEqual(jobModules.map(({ id }) => id), ['middleware', 'database', 'host', 'network', 'network-security'])
})

test('TC-PORTAL-002 (TC-02) 五大岗位入口均解析到独立岗位模块', () => {
  for (const job of jobModules) {
    assert.deepEqual(parseHashRoute(`/jobs/${job.id}`), { name: 'jobModule', jobId: job.id, feature: null })
  }
})

test('TC-PORTAL-003 (TC-03) 三个公共模块统一使用左侧岗位导航', async () => {
  const sources = await Promise.all([
    readSource('../src/pages/DownloadsPage.vue'),
    readSource('../src/pages/StandardsPage.vue'),
    readSource('../src/components/ForumPostList.vue')
  ])
  for (const source of sources) {
    assert.match(source, /JobNavigation/)
    assert.match(source, /public-module-layout/)
  }
})

test('TC-PORTAL-004 (TC-04) 岗位筛选正确处理无数据、单条数据与全部数据', () => {
  const items = [
    { id: 1, category: '中间件' },
    { id: 2, category: '数据库' }
  ]
  assert.deepEqual(filterItemsByJob(items, 'host', (item) => item.category), [])
  assert.deepEqual(filterItemsByJob(items, 'database', (item) => item.category), [items[1]])
  assert.deepEqual(filterItemsByJob(items, 'all', (item) => item.category), items)
  assert.equal(normalizeJobId('unknown'), 'all')
})

test('TC-PORTAL-005 (TC-05) 常用命令迁移到中间件岗位且旧入口兼容', () => {
  const middleware = jobModules.find(({ id }) => id === 'middleware')
  assert.ok(middleware.features.some(({ id }) => id === 'commands'))
  assert.deepEqual(parseHashRoute('/jobs/middleware/commands'), { name: 'jobModule', jobId: 'middleware', feature: 'commands' })
  assert.deepEqual(parseHashRoute('/commands'), { name: 'jobModule', jobId: 'middleware', feature: 'commands', legacy: true })
})

test('TC-PORTAL-006 (TC-06) 五个岗位模块边界独立且接口配置互不影响', () => {
  assert.equal(new Set(jobModules.map(({ modulePath }) => modulePath)).size, 5)
  assert.equal(new Set(jobModules.map(({ apiConfigKey }) => apiConfigKey)).size, 5)
  for (const job of jobModules) {
    assert.match(job.modulePath, new RegExp(`/modules/${job.id.replace('-', '\\-')}$`))
    assert.equal(typeof job.request, 'function')
  }
})

test('TC-PORTAL-007 (TC-07) 通用岗位 UI 与筛选逻辑由共享模块提供', async () => {
  const [workspace, navigation, commands] = await Promise.all([
    readSource('../src/shared/jobs/JobWorkspace.vue'),
    readSource('../src/shared/jobs/JobNavigation.vue'),
    readSource('../src/pages/CommandsPage.vue')
  ])
  assert.match(workspace, /JobNavigation/)
  assert.match(navigation, /jobModules/)
  assert.match(commands, /moduleRequest\(/)
  assert.doesNotMatch(commands, /fetch\(/)
})

test('TC-PORTAL-008 (TC-08) agent.md 明确模块独立、代码复用和 UI 一致要求', async () => {
  const agent = await readSource('../../agent.md')
  assert.match(agent, /岗位模块编码独立/)
  assert.match(agent, /通用能力代码复用/)
  assert.match(agent, /UI.*一致/)
})
