import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../api', () => ({ request: vi.fn(() => Promise.reject(new Error('no server'))) }))
import RoleModulePanel from '../components/RoleModulePanel.vue'
import CommonCommandPanel from '../components/CommonCommandPanel.vue'

describe('岗位专属模块页 RoleModulePanel', () => {
  it('TC-02 进入中间件岗位模块，展示正确岗位标识与内容', async () => {
    const wrapper = mount(RoleModulePanel, { props: { roleId: 'middleware' } })
    await flushPromises()
    expect(wrapper.text()).toContain('中间件岗位')
    // 岗位标识（category）
    expect(wrapper.find('.role-badge').text()).toBe('中间件')
    expect(wrapper.find('.role-module-header').attributes('data-role-id')).toBe('middleware')
  })

  it('TC-05/TC-07 中间件岗位内嵌复用的常用命令组件，并按岗位 category 传参', async () => {
    const wrapper = mount(RoleModulePanel, { props: { roleId: 'middleware' } })
    await flushPromises()
    const cmd = wrapper.findComponent(CommonCommandPanel)
    expect(cmd.exists()).toBe(true)
    expect(cmd.props('category')).toBe('中间件')
    // TC-06 默认接入门户后端
    expect(cmd.props('apiBase')).toBe('/api')
  })

  it('TC-02 网络安全岗位展示名正确、数据范围沿用「安全」分类', async () => {
    const wrapper = mount(RoleModulePanel, { props: { roleId: 'security' } })
    await flushPromises()
    expect(wrapper.text()).toContain('网络安全岗位')
    expect(wrapper.find('.role-badge').text()).toBe('安全')
  })

  it('TC-02 未知岗位不 404/白屏，展示友好兜底并可返回首页', async () => {
    const wrapper = mount(RoleModulePanel, { props: { roleId: 'unknown' } })
    await flushPromises()
    expect(wrapper.find('.role-missing').exists()).toBe(true)
    await wrapper.find('.role-missing button').trigger('click')
    expect(wrapper.emitted('home')).toBeTruthy()
  })

  it('TC-01 岗位快捷入口向公共模块透传本岗位 category', async () => {
    const wrapper = mount(RoleModulePanel, { props: { roleId: 'database' } })
    await flushPromises()
    await wrapper.findAll('.role-quick-links button')[0].trigger('click')
    expect(wrapper.emitted('open-downloads')[0]).toEqual(['数据库'])
  })
})
