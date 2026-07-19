import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('../api', () => ({ request: vi.fn() }))
import { request } from '../api'
import ForumPostEditor from '../components/ForumPostEditor.vue'

const markdown = { render: () => '<p>preview</p>' }

function mountEditor(props = {}) {
  return mount(ForumPostEditor, {
    props: { auth: { token: 't' }, markdown, notify: () => {}, ...props }
  })
}

describe('论坛发帖/编辑 ForumPostEditor', () => {
  beforeEach(() => {
    vi.mocked(request).mockReset()
    // 岗位清单接口不可用 → 走兜底五大岗位；其余请求默认成功
    vi.mocked(request).mockImplementation((path) => {
      if (path.includes('portal/roles')) return Promise.reject(new Error('no server'))
      return Promise.resolve({})
    })
  })

  it('TC-03 发帖表单提供岗位选择（不限岗位 + 五大岗位）', async () => {
    const wrapper = mountEditor()
    await flushPromises()
    const options = wrapper.find('select.meta-category').findAll('option').map(o => o.text())
    expect(options).toEqual(['不限岗位', '中间件', '数据库', '主机', '网络', '网络安全'])
  })

  it('TC-03 保存新帖时提交所选岗位 category，使其可被左侧岗位导航筛出', async () => {
    const wrapper = mountEditor()
    await flushPromises()
    await wrapper.find('input.meta-title').setValue('中间件调优实践')
    await wrapper.find('textarea.editor-textarea').setValue('正文内容')
    await wrapper.find('select.meta-category').setValue('中间件')
    await wrapper.findAll('.toolbar-actions button').at(-1).trigger('click')
    await flushPromises()

    const postCall = vi.mocked(request).mock.calls.find(c => c[0] === '/api/forum/posts')
    expect(postCall).toBeTruthy()
    expect(postCall[1].method).toBe('POST')
    expect(postCall[1].body.category).toBe('中间件')
  })

  it('TC-03/TC-04 编辑改选“不限岗位”提交 category=null 以清空原岗位', async () => {
    vi.mocked(request).mockImplementation((path) => {
      if (path.includes('portal/roles')) return Promise.reject(new Error('no server'))
      if (path === '/api/forum/posts/9') {
        return Promise.resolve({ title: '旧帖', content: '内容', category: '数据库', tags: [] })
      }
      return Promise.resolve({})
    })
    const wrapper = mountEditor({ postId: 9 })
    await flushPromises()
    // 回显原岗位后改选“不限岗位”（value=""）
    await wrapper.find('select.meta-category').setValue('')
    await wrapper.findAll('.toolbar-actions button').at(-1).trigger('click')
    await flushPromises()

    const putCall = vi.mocked(request).mock.calls.find(c => c[0] === '/api/forum/posts/9' && c[1] && c[1].method === 'PUT')
    expect(putCall).toBeTruthy()
    expect(putCall[1].body.category).toBeNull()
  })

  it('TC-03 编辑时回显帖子岗位到岗位选择框', async () => {
    vi.mocked(request).mockImplementation((path) => {
      if (path.includes('portal/roles')) return Promise.reject(new Error('no server'))
      if (path === '/api/forum/posts/9') {
        return Promise.resolve({ title: '旧帖', content: '内容', category: '数据库', tags: ['sql'] })
      }
      return Promise.resolve({})
    })
    const wrapper = mountEditor({ postId: 9 })
    await flushPromises()
    expect(wrapper.find('select.meta-category').element.value).toBe('数据库')
  })
})
