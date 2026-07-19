import { describe, it, expect, vi, beforeEach } from 'vitest'

// 隔离 api.request，使 roles.js 的加载逻辑可控（TC-02/TC-06/TC-07）
vi.mock('../api', () => ({ request: vi.fn() }))
import { request } from '../api'
import { ROLE_FALLBACK, PORTAL_API_BASE, normalizeRole, fetchRoles, apiBaseFor } from '../roles'

describe('共享岗位数据源 roles.js', () => {
  beforeEach(() => vi.mocked(request).mockReset())

  it('TC-02 兜底清单为固定顺序的五大岗位，且网络安全映射到「安全」分类', () => {
    expect(ROLE_FALLBACK.map(r => r.id)).toEqual(['middleware', 'database', 'host', 'network', 'security'])
    expect(ROLE_FALLBACK.map(r => r.label)).toEqual(['中间件', '数据库', '主机', '网络', '网络安全'])
    expect(ROLE_FALLBACK.map(r => r.category)).toEqual(['中间件', '数据库', '主机', '网络', '安全'])
    // 每个岗位默认接入门户后端
    expect(ROLE_FALLBACK.every(r => r.apiBase === PORTAL_API_BASE)).toBe(true)
  })

  it('TC-07 normalizeRole 缺省 apiBase 时回退门户后端', () => {
    expect(normalizeRole({ id: 'x', label: 'X', category: 'X' }).apiBase).toBe('/api')
    expect(normalizeRole({ id: 'x', label: 'X', category: 'X', apiBase: 'http://svc/api' }).apiBase).toBe('http://svc/api')
  })

  it('TC-02 fetchRoles 优先使用后端返回的岗位清单', async () => {
    vi.mocked(request).mockResolvedValueOnce([
      { id: 'middleware', label: '中间件', category: '中间件', apiBase: '/api' },
      { id: 'database', label: '数据库', category: '数据库', apiBase: 'http://db/api' }
    ])
    const roles = await fetchRoles()
    expect(request).toHaveBeenCalledWith('/api/public/portal/roles', { token: null })
    expect(roles).toHaveLength(2)
    expect(roles[1].apiBase).toBe('http://db/api')
  })

  it('TC-02 接口失败时 fetchRoles 回退兜底清单，保证导航一致可用', async () => {
    vi.mocked(request).mockRejectedValueOnce(new Error('boom'))
    const roles = await fetchRoles()
    expect(roles.map(r => r.id)).toEqual(['middleware', 'database', 'host', 'network', 'security'])
  })

  it('TC-06 apiBaseFor 按岗位取接入点，单岗位配置变化不影响其他岗位', () => {
    const roles = [
      { id: 'middleware', apiBase: '/api' },
      { id: 'database', apiBase: 'http://db-svc/api' }
    ]
    expect(apiBaseFor(roles, 'database')).toBe('http://db-svc/api')
    expect(apiBaseFor(roles, 'middleware')).toBe('/api')
    // 未知岗位或缺省回退门户后端
    expect(apiBaseFor(roles, 'unknown')).toBe('/api')
  })
})
