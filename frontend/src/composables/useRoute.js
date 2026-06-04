/**
 * Hash 路由管理 composable
 * 替代 App.vue 中分散的 route 逻辑
 */
import { reactive } from 'vue'

const routeNames = {
  HOME: 'home',
  PUBLIC: 'public',
  STANDARDS: 'standards',
  FORUM: 'forum',
  FORUM_DETAIL: 'forumDetail',
  FORUM_EDITOR: 'forumEditor',
  FORUM_MINE: 'forumMine',
  KNOWLEDGE: 'knowledge',
  WIKI: 'wiki',
  DIAGNOSTICS: 'diagnostics',
  COMMANDS: 'commands',
  ADMIN: 'admin',
  DOCUMENT_EDITOR: 'documentEditor'
}

function parseRoute() {
  const hash = window.location.hash.replace(/^#\/?/, '')
  if (hash.startsWith('document/')) return { name: routeNames.DOCUMENT_EDITOR, documentId: hash.split('/')[1] }
  if (hash.startsWith('forum/post/')) return { name: routeNames.FORUM_DETAIL, postId: hash.split('/')[2] }
  if (hash.startsWith('forum/edit')) return { name: routeNames.FORUM_EDITOR }
  if (hash === 'forum/mine') return { name: routeNames.FORUM_MINE }
  if (hash.startsWith('standards')) {
    const parts = hash.split('/')
    return { name: routeNames.STANDARDS, standardType: parts[1] || null }
  }
  const validRoutes = Object.values(routeNames)
  const name = hash || routeNames.HOME
  return { name: validRoutes.includes(name) ? name : routeNames.HOME }
}

const route = reactive(Object.assign({ documentId: null, postId: null, standardType: null }, parseRoute()))

export function useRoute() {
  function syncRoute() {
    const parsed = parseRoute()
    Object.assign(route, parsed)
  }

  function navigate(name) {
    window.location.hash = '#/' + name
  }

  return { route, routeNames, syncRoute, navigate }
}
