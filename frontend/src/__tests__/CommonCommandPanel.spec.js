import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../api', () => ({ request: vi.fn() }))
import { request } from '../api'
import CommonCommandPanel from '../components/CommonCommandPanel.vue'

describe('可复用的常用命令组件 CommonCommandPanel', () => {
  beforeEach(() => vi.mocked(request).mockReset())

  it('TC-04/TC-07 无数据时展示友好空状态，不报错', async () => {
    vi.mocked(request).mockResolvedValue({ content: [] })
    const wrapper = mount(CommonCommandPanel, { props: { category: '主机' } })
    await flushPromises()
    expect(wrapper.find('.cmd-empty').text()).toContain('该岗位暂无常用命令')
    expect(wrapper.findAll('.cmd-item')).toHaveLength(0)
  })

  it('TC-05/TC-07 有数据时渲染命令列表并可复制', async () => {
    vi.mocked(request).mockResolvedValue({
      content: [{ id: 1, title: '查看Nginx进程', command: 'ps -ef | grep nginx', description: '定位', tag: 'nginx' }]
    })
    const wrapper = mount(CommonCommandPanel, { props: { category: '中间件' } })
    await flushPromises()
    expect(wrapper.findAll('.cmd-item')).toHaveLength(1)
    expect(wrapper.find('.cmd-title').text()).toBe('查看Nginx进程')
  })

  it('TC-06 使用传入的 apiBase 拼接后端接入点，支持岗位独立后端', async () => {
    vi.mocked(request).mockResolvedValue({ content: [] })
    mount(CommonCommandPanel, { props: { category: '数据库', apiBase: 'http://db-svc:9000/api' } })
    await flushPromises()
    const calledUrl = vi.mocked(request).mock.calls[0][0]
    expect(calledUrl).toContain('http://db-svc:9000/api/module/commands')
    expect(calledUrl).toContain('category=%E6%95%B0%E6%8D%AE%E5%BA%93') // 数据库
  })

  it('TC-06 默认接入门户后端 /api', async () => {
    vi.mocked(request).mockResolvedValue({ content: [] })
    mount(CommonCommandPanel, { props: { category: '中间件' } })
    await flushPromises()
    expect(vi.mocked(request).mock.calls[0][0]).toContain('/api/module/commands')
  })
})
