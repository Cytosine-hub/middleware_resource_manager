import { request } from '../../api.js'

export function createModuleApi(baseUrl) {
  const normalizedBaseUrl = String(baseUrl || '/api').replace(/\/$/, '')
  return function moduleRequest(path, options) {
    const normalizedPath = String(path || '').startsWith('/') ? path : `/${path}`
    return request(`${normalizedBaseUrl}${normalizedPath}`, options)
  }
}
