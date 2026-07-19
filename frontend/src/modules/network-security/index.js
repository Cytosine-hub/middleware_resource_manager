import { createModuleApi } from '../../shared/api/createModuleApi.js'

const apiBaseUrl = import.meta.env?.VITE_NETWORK_SECURITY_API_BASE_URL || '/api'

export const networkSecurityModule = {
  id: 'network-security', name: '网络安全岗位', shortName: '网络安全', englishName: 'Network Security',
  description: '承载安全基线、防护策略和网络安全运营能力。', modulePath: '/modules/network-security',
  apiConfigKey: 'VITE_NETWORK_SECURITY_API_BASE_URL', apiBaseUrl, request: createModuleApi(apiBaseUrl), features: []
}
