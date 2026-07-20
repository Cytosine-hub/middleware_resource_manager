import { createJobModule } from '../../shared/jobs/createJobModule.js'
import ModuleEntry from './ModuleEntry.vue'

const defaultApiBaseUrl = import.meta.env?.VITE_HOST_API_BASE_URL || '/api'

const definition = {
  id: 'host', name: '主机岗位', shortName: '主机', englishName: 'Host Operations',
  description: '承载主机资源、操作系统和运行状态相关能力。', modulePath: '/modules/host',
  apiConfigKey: 'VITE_HOST_API_BASE_URL', defaultApiBaseUrl, entryComponent: ModuleEntry, features: []
}

export function createHostModule(options) {
  return createJobModule(definition, options)
}

export const hostModule = createHostModule()
