// @vitest-environment node
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

// 门户首页布局内联于 App.vue（体量大、子组件多，直接挂载成本高且脆弱）。
// 这里以 SFC 源码做结构断言，验证 TC-01 的「公共区域 / 岗位专属区域」按布局分区，
// 且区域靠分组容器区分、由共享数据源驱动，而非生硬标题文案。
const appSrc = readFileSync(fileURLToPath(new URL('../App.vue', import.meta.url)), 'utf8')
// 用首页模板中唯一的区域布局类名切片，避免受 topbar 等其它 route.name 引用干扰
const publicRegion = appSrc.slice(appSrc.indexOf('portal-region portal-public'), appSrc.indexOf('portal-region portal-roles'))
// 岗位区域到下一个 route 分支（下载中心）之间即为首页区域收尾，用于校验首页只剩两大区域
const rolesRegion = appSrc.slice(appSrc.indexOf('portal-region portal-roles'), appSrc.indexOf("v-else-if=\"route.name === 'public'\""))

describe('门户首页布局（App.vue）', () => {
  it('TC-01 首页以布局容器划分公共区域与岗位专属区域', () => {
    expect(appSrc).toContain('portal-region portal-public')
    expect(appSrc).toContain('portal-region portal-roles')
    expect(publicRegion.length).toBeGreaterThan(0)
    expect(rolesRegion.length).toBeGreaterThan(0)
  })

  it('TC-01 公共区域承载软件下载/标准发布/infra论坛入口', () => {
    expect(publicRegion).toContain('goPublic')
    expect(publicRegion).toContain('goStandards')
    expect(publicRegion).toContain('goForum')
  })

  it('TC-01/TC-02 岗位专属区域由共享 roleModules 循环渲染五大岗位入口', () => {
    expect(rolesRegion).toContain('v-for="role in roleModules"')
    expect(rolesRegion).toContain('goRoleModule(role.id)')
  })

  it('TC-01 区域靠布局/分组区分，不使用生硬的分区标题文案', () => {
    // 不出现「公共区域 / 岗位专属区域」这类突兀的分区大标题
    expect(publicRegion).not.toContain('>公共区域<')
    expect(rolesRegion).not.toContain('>岗位专属区域<')
  })

  it('TC-01 首页仅保留公共区域与岗位区域两大 portal-region，无第三/第四类结构', () => {
    // 首页区域容器只有两个 portal-region
    const regionCount = (appSrc.match(/class="portal-region/g) || []).length
    expect(regionCount).toBe(2)
    // 已下线的「常用工具」占位区块不应再出现
    expect(appSrc).not.toContain('常用工具')
    expect(appSrc).not.toContain('portal-tools')
    // 「最新软件发布」归入公共区域（属于软件下载类公共内容），不再是独立第三类结构
    expect(publicRegion).toContain('最新软件发布')
    expect(rolesRegion).not.toContain('最新软件发布')
  })

  it('TC-04 软件下载列表在无数据时展示友好空状态', () => {
    expect(appSrc).toContain('publicPage.content.length === 0')
    expect(appSrc).toContain('download-empty')
  })

  it('TC-05 常用命令入口仅在岗位模块页，首页不直接挂载常用命令面板/无独立死链入口', () => {
    // 常用命令组件只经由岗位模块页（RoleModulePanel）挂载，已迁移到中间件岗位
    expect(appSrc).toContain('<RoleModulePanel')
    // App.vue 不直接内联/挂载常用命令面板，也无独立「常用命令」路由入口（原入口已归位，不产生死链）
    expect(appSrc).not.toContain('CommonCommandPanel')
    expect(appSrc).not.toContain("route.name === 'commands'")
  })
})
