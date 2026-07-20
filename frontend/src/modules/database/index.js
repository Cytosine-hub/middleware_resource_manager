import { createJobModule } from '../../shared/jobs/createJobModule.js'
import ModuleEntry from './ModuleEntry.vue'
import DataMigrationPage from './pages/DataMigrationPage.vue'

const defaultApiBaseUrl = import.meta.env?.VITE_DATABASE_API_BASE_URL || '/api'

const definition = {
  id: 'database', name: '数据库岗位', shortName: '数据库', englishName: 'Database Operations',
  description: '承载数据库标准、迁移与日常运维能力。', modulePath: '/modules/database',
  apiConfigKey: 'VITE_DATABASE_API_BASE_URL', defaultApiBaseUrl, entryComponent: ModuleEntry,
  features: [{ id: 'data-migration', name: '数据迁移', description: '查看数据库迁移方案与能力设计。', icon: '迁', component: DataMigrationPage }]
}

export function createDatabaseModule(options) {
  return createJobModule(definition, options)
}

export const databaseModule = createDatabaseModule()
