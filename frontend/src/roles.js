// 门户五大岗位的统一前端数据源（岗位专属入口 + 公共模块左侧岗位导航共用）。
// 首页入口、RoleNav、RoleModulePanel 等一律从这里取数，避免顺序/名称/category 在多处硬编码漂移。
// 与后端 PortalRole 契约对齐：网络安全岗展示名「网络安全」，数据范围沿用「安全」分类。
import { request } from './api'

// 门户后端默认接入点。各岗位模块默认连门户后端，也可由后端按岗位配置为独立后端服务（apiBase）。
export const PORTAL_API_BASE = '/api'

// 兜底岗位清单：接口不可用时仍保证五大岗位的顺序、标识、名称、数据范围一致可用。
export const ROLE_FALLBACK = Object.freeze([
  { id: 'middleware', label: '中间件', category: '中间件', apiBase: PORTAL_API_BASE },
  { id: 'database', label: '数据库', category: '数据库', apiBase: PORTAL_API_BASE },
  { id: 'host', label: '主机', category: '主机', apiBase: PORTAL_API_BASE },
  { id: 'network', label: '网络', category: '网络', apiBase: PORTAL_API_BASE },
  { id: 'security', label: '网络安全', category: '安全', apiBase: PORTAL_API_BASE }
])

// 归一化后端返回的单个岗位项，缺省 apiBase 回退门户后端。
export function normalizeRole(role) {
  return {
    id: role.id,
    label: role.label,
    category: role.category,
    apiBase: role.apiBase || PORTAL_API_BASE
  }
}

// 拉取门户岗位清单；失败或空则回退兜底。所有公共模块与岗位入口共用同一份数据。
export async function fetchRoles() {
  try {
    const data = await request('/api/public/portal/roles', { token: null })
    if (Array.isArray(data) && data.length) return data.map(normalizeRole)
  } catch {
    // 忽略：接口不可用时回退兜底
  }
  return ROLE_FALLBACK.map(normalizeRole)
}

// 取某岗位的后端接入点；单个岗位配置变化不影响其他岗位（TC-06）。
export function apiBaseFor(roles, roleId) {
  const role = (roles || []).find(r => r.id === roleId)
  return (role && role.apiBase) || PORTAL_API_BASE
}
