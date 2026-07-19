import { createModuleApi } from '../../shared/api/createModuleApi.js'

const apiBaseUrl = import.meta.env?.VITE_MIDDLEWARE_API_BASE_URL || '/api'

export const middlewareModule = {
  id: 'middleware',
  name: '中间件岗位',
  shortName: '中间件',
  englishName: 'Middleware Operations',
  description: '集中维护中间件软件、运维命令、知识与诊断能力。',
  modulePath: '/modules/middleware',
  apiConfigKey: 'VITE_MIDDLEWARE_API_BASE_URL',
  apiBaseUrl,
  request: createModuleApi(apiBaseUrl),
  features: [{ id: 'commands', name: '常用命令', description: '查询、查看和复制中间件运维命令。', icon: '令' }]
}
