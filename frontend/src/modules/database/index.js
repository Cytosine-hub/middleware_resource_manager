import { createModuleApi } from '../../shared/api/createModuleApi.js'

const apiBaseUrl = import.meta.env?.VITE_DATABASE_API_BASE_URL || '/api'

export const databaseModule = {
  id: 'database', name: '数据库岗位', shortName: '数据库', englishName: 'Database Operations',
  description: '承载数据库标准、迁移与日常运维能力。', modulePath: '/modules/database',
  apiConfigKey: 'VITE_DATABASE_API_BASE_URL', apiBaseUrl, request: createModuleApi(apiBaseUrl),
  features: [{ id: 'data-migration', name: '数据迁移', description: '查看数据库迁移方案与能力设计。', icon: '迁' }]
}
