import { request } from './api'

function insertAtCursor(textarea, text) {
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  textarea.value = textarea.value.slice(0, start) + text + textarea.value.slice(end)
  const newCursor = start + text.length
  textarea.selectionStart = newCursor
  textarea.selectionEnd = newCursor
  textarea.dispatchEvent(new Event('input', { bubbles: true }))
}

export function handleEditorKeydown(event) {
  if (event.key === 'Enter') {
    const ta = event.target
    const start = ta.selectionStart
    const lineStart = ta.value.lastIndexOf('\n', start - 1) + 1
    const currentLine = ta.value.slice(lineStart, start)
    const match = currentLine.match(/^(\s*)([-*+]|\d+\.)\s+(.*)$/)
    if (match) {
      event.preventDefault()
      const indent = match[1]
      const prefix = match[2]
      const rest = match[3]
      if (rest === '') {
        // 空列表项 → 结束列表
        ta.value = ta.value.slice(0, lineStart) + '\n' + ta.value.slice(start)
        ta.selectionStart = ta.selectionEnd = lineStart + 1
        ta.dispatchEvent(new Event('input', { bubbles: true }))
      } else {
        insertAtCursor(ta, '\n' + indent + prefix + ' ')
      }
      return
    }
  }

  if (event.key === 'Tab') {
    event.preventDefault()
    insertAtCursor(event.target, '    ')
  }
}

export async function handleEditorPaste(event) {
  const items = event.clipboardData?.items
  if (!items) return

  for (const item of items) {
    if (item.type.startsWith('image/')) {
      event.preventDefault()
      const file = item.getAsFile()
      if (!file) continue

      try {
        const formData = new FormData()
        formData.append('file', file)
        const result = await request('/api/admin/images/upload', { method: 'POST', body: formData })
        insertAtCursor(event.target, `![](${result.url})`)
      } catch (err) {
        alert('图片上传失败: ' + (err.message || '未知错误'))
      }
      return
    }
  }
}
