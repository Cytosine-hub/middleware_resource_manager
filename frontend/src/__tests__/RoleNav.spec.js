import { describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// 接口不可用 → RoleNav 走兜底清单，保证渲染确定
vi.mock('../api', () => ({ request: vi.fn(() => Promise.reject(new Error('no server'))) }))
import RoleNav from '../components/RoleNav.vue'

describe('公共模块左侧岗位导航 RoleNav', () => {
  it('TC-03 左侧提供「全部岗位」+ 五大岗位导航项', async () => {
    const wrapper = mount(RoleNav, { props: { modelValue: '' } })
    await flushPromises()
    // 统一的左侧导航容器
    expect(wrapper.find('aside.role-nav').exists()).toBe(true)
    const labels = wrapper.findAll('.role-nav-item').map(b => b.text())
    expect(labels).toEqual(['全部岗位', '中间件', '数据库', '主机', '网络', '网络安全'])
  })

  it('TC-03 点击岗位触发筛选事件（v-model + change），交互一致', async () => {
    const wrapper = mount(RoleNav, { props: { modelValue: '' } })
    await flushPromises()
    // 点击「数据库」→ 对应 category「数据库」
    await wrapper.findAll('.role-nav-item')[2].trigger('click')
    expect(wrapper.emitted('update:modelValue')[0]).toEqual(['数据库'])
    expect(wrapper.emitted('change')[0]).toEqual(['数据库'])
  })

  it('TC-03 当前选中项高亮，切换回全部岗位清除筛选', async () => {
    const wrapper = mount(RoleNav, { props: { modelValue: '网络' } })
    await flushPromises()
    const active = wrapper.find('.role-nav-item.active')
    expect(active.text()).toBe('网络')
    // 点击「全部岗位」→ category 为空
    await wrapper.findAll('.role-nav-item')[0].trigger('click')
    expect(wrapper.emitted('update:modelValue')[0]).toEqual([''])
  })
})
