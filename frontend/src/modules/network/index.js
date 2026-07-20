import { createJobModule } from '../../shared/jobs/createJobModule.js'
import ModuleEntry from './ModuleEntry.vue'

const defaultApiBaseUrl = import.meta.env?.VITE_NETWORK_API_BASE_URL || '/api'

const definition = {
  id: 'network', name: '网络岗位', shortName: '网络', englishName: 'Network Operations',
  description: '承载网络配置、连通性和基础网络运维能力。', modulePath: '/modules/network',
  apiConfigKey: 'VITE_NETWORK_API_BASE_URL', defaultApiBaseUrl, entryComponent: ModuleEntry, features: []
}

export function createNetworkModule(options) {
  return createJobModule(definition, options)
}

export const networkModule = createNetworkModule()
