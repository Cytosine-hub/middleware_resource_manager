import { createJobModule } from '../../shared/jobs/createJobModule.js'
import ModuleEntry from './ModuleEntry.vue'

const defaultApiBaseUrl = import.meta.env?.VITE_NETWORK_SECURITY_API_BASE_URL || '/api'

const definition = {
  id: 'network-security', name: '网络安全岗位', shortName: '网络安全', englishName: 'Network Security',
  description: '承载安全基线、防护策略和网络安全运营能力。', modulePath: '/modules/network-security',
  apiConfigKey: 'VITE_NETWORK_SECURITY_API_BASE_URL', defaultApiBaseUrl, entryComponent: ModuleEntry, features: []
}

export function createNetworkSecurityModule(options) {
  return createJobModule(definition, options)
}

export const networkSecurityModule = createNetworkSecurityModule()
