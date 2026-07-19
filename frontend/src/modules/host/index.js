import { createModuleApi } from '../../shared/api/createModuleApi.js'

const apiBaseUrl = import.meta.env?.VITE_HOST_API_BASE_URL || '/api'

export const hostModule = {
  id: 'host', name: '主机岗位', shortName: '主机', englishName: 'Host Operations',
  description: '承载主机资源、操作系统和运行状态相关能力。', modulePath: '/modules/host',
  apiConfigKey: 'VITE_HOST_API_BASE_URL', apiBaseUrl, request: createModuleApi(apiBaseUrl), features: []
}
