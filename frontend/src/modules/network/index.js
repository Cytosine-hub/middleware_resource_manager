import { createModuleApi } from '../../shared/api/createModuleApi.js'

const apiBaseUrl = import.meta.env?.VITE_NETWORK_API_BASE_URL || '/api'

export const networkModule = {
  id: 'network', name: '网络岗位', shortName: '网络', englishName: 'Network Operations',
  description: '承载网络配置、连通性和基础网络运维能力。', modulePath: '/modules/network',
  apiConfigKey: 'VITE_NETWORK_API_BASE_URL', apiBaseUrl, request: createModuleApi(apiBaseUrl), features: []
}
