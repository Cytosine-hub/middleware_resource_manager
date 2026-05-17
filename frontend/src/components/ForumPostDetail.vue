<template>
  <div class="forum-detail-page">
    <div v-if="loading" class="loading-panel"><div class="spinner"></div><p>加载中...</p></div>
    <template v-else-if="post">
      <div class="forum-detail-layout">
        <!-- 左侧栏：文章目录 -->
        <aside class="post-dir-panel">
          <div class="post-dir-header">
            <h3>文章目录</h3>
            <button @click="$emit('back')">返回列表</button>
          </div>
          <div class="post-dir-list">
            <button
              v-for="item in postList"
              :key="item.id"
              :class="['post-dir-item', { active: String(item.id) === String(postId) }]"
              @click="navigatePost(item.id)"
            >{{ item.title }}</button>
          </div>
        </aside>

        <!-- 中间：文章内容 -->
        <div class="detail-main">
          <article class="post-article">
            <h1 class="post-title">{{ post.title }}</h1>
            <div class="post-author-line">
              <span class="author-avatar" :style="avatarStyle">{{ post.authorDisplayName?.charAt(0) }}</span>
              <span class="author-name">{{ post.authorDisplayName }}</span>
              <span class="post-date">发布于 {{ formatRelativeDate(post.createdAt) }}</span>
            </div>
            <div class="post-tags">
              <span v-for="tag in (post.tags || [])" :key="tag" class="tag-pill">{{ tag }}</span>
            </div>
            <div class="post-body markdown-preview" ref="postBody" v-html="renderedContent"></div>
            <div class="post-bottom-actions">
              <button :class="['post-action-btn', { liked: post.liked }]" @click="handleLike">
                <svg width="16" height="16" viewBox="0 0 24 24" :fill="post.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                </svg>
                {{ post.likeCount }} 赞
              </button>
              <button class="post-action-btn" @click="scrollToComments">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
                {{ allComments.length }} 评论
              </button>
              <button v-if="auth.token && auth.user?.username === post.authorUsername" class="post-action-btn" @click="$emit('editPost', post.id)">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
                编辑
              </button>
            </div>
          </article>

          <!-- 评论区 -->
          <section class="comments-section" ref="commentsSection">
            <h3 class="comments-title">评论 ({{ allComments.length }})</h3>
            <div v-if="auth.token" class="comment-form">
              <textarea v-model="commentText" placeholder="写下你的评论..." rows="3"></textarea>
              <button :disabled="!commentText.trim()" @click="submitComment(null)">发表评论</button>
            </div>
            <div v-else class="comment-login-hint">
              请先<a href="#" @click.prevent="$emit('login')">登录</a>后发表评论
            </div>
            <template v-for="c in topLevelComments" :key="c.id">
              <div class="comment-item">
                <div class="comment-avatar">{{ c.authorDisplayName?.charAt(0) }}</div>
                <div class="comment-body">
                  <div class="comment-header">
                    <strong>{{ c.authorDisplayName }}</strong>
                    <span class="comment-time">{{ formatDate(c.createdAt) }}</span>
                  </div>
                  <p class="comment-text">{{ c.content }}</p>
                  <div class="comment-actions">
                    <button class="comment-btn" @click="handleCommentLike(c)">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z"/><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                      {{ c.likeCount || 0 }}
                    </button>
                    <button class="comment-btn" @click="toggleReply(c.id)">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                      回复
                    </button>
                  </div>
                  <div v-if="replyingTo === c.id" class="reply-form">
                    <textarea v-model="replyText" placeholder="写下回复..." rows="2"></textarea>
                    <div class="reply-actions">
                      <button class="ghost" @click="replyingTo = null">取消</button>
                      <button :disabled="!replyText.trim()" @click="submitComment(c.id)">回复</button>
                    </div>
                  </div>
                  <div v-for="reply in getReplies(c.id)" :key="reply.id" class="reply-item">
                    <div class="comment-avatar small">{{ reply.authorDisplayName?.charAt(0) }}</div>
                    <div class="comment-body">
                      <div class="comment-header">
                        <strong>{{ reply.authorDisplayName }}</strong>
                        <span class="comment-time">{{ formatDate(reply.createdAt) }}</span>
                      </div>
                      <p class="comment-text">{{ reply.content }}</p>
                      <div class="comment-actions">
                        <button class="comment-btn" @click="handleCommentLike(reply)">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z"/><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
                          {{ reply.likeCount || 0 }}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </template>
            <p v-if="allComments.length === 0" class="empty-state">暂无评论，快来发表第一条评论吧！</p>
          </section>
        </div>

        <!-- 右侧栏：文档大纲 -->
        <aside class="post-toc-panel" v-if="tocItems.length">
          <h4 class="toc-title">文档大纲</h4>
          <button
            v-for="item in tocItems"
            :key="item.id"
            :class="['toc-link', { active: activeHeadingId === item.id }]"
            :style="{ '--toc-level': item.level - 1 }"
            @click="scrollToHeading(item.id)"
          >{{ item.text }}</button>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { request } from '../api'

const props = defineProps({ auth: Object, postId: [String, Number], markdown: Object })
const emit = defineEmits(['back', 'editPost', 'login'])

const post = ref(null)
const postList = ref([])
const allComments = ref([])
const commentText = ref('')
const replyText = ref('')
const replyingTo = ref(null)
const loading = ref(false)
const commentsSection = ref(null)
const postBody = ref(null)
const activeHeadingId = ref('')

// 渲染 markdown 并注入 heading id
const renderedContent = computed(() => {
  try {
    let html = props.markdown.render(post.value?.content || '')
    let idx = 0
    html = html.replace(/<(h[1-3])([^>]*)>([\s\S]*?)<\/\1>/g, (_, tag, attrs, inner) => {
      if (/id=/.test(attrs)) return `<${tag}${attrs}>${inner}</${tag}>`
      const id = 'toc-' + (idx++)
      return `<${tag}${attrs} id="${id}">${inner}</${tag}>`
    })
    return html
  } catch { return post.value?.content || '' }
})

// 提取 TOC 条目
const tocItems = computed(() => {
  const items = []
  const re = /<(h[1-3])[^>]*id="([^"]*)"[^>]*>([\s\S]*?)<\/\1>/g
  let m
  while ((m = re.exec(renderedContent.value))) {
    const level = parseInt(m[1][1])
    const id = m[2]
    const text = m[3].replace(/<[^>]+>/g, '').trim()
    if (text) items.push({ level, id, text })
  }
  return items
})

const topLevelComments = computed(() => (allComments.value || []).filter(c => !c.parentId))
const getReplies = (parentId) => (allComments.value || []).filter(c => c.parentId === parentId)

const avatarGradients = [
  'linear-gradient(135deg, #667eea, #764ba2)',
  'linear-gradient(135deg, #f093fb, #f5576c)',
  'linear-gradient(135deg, #4facfe, #00f2fe)',
  'linear-gradient(135deg, #43e97b, #38f9d7)',
  'linear-gradient(135deg, #fa709a, #fee140)',
]
const avatarStyle = computed(() => {
  const name = post.value?.authorDisplayName || ''
  const idx = name.split('').reduce((s, c) => s + c.charCodeAt(0), 0) % avatarGradients.length
  return { background: avatarGradients[idx] }
})

async function loadPostList() {
  try {
    const data = await request('/api/forum/posts?size=50', { token: null })
    postList.value = Array.isArray(data?.content) ? data.content : []
  } catch { postList.value = [] }
}

async function load() {
  loading.value = true
  post.value = null
  allComments.value = []
  try {
    const data = await request(`/api/forum/posts/${props.postId}`)
    post.value = data || null
    if (post.value) {
      post.value.tags = post.value.tags || []
      allComments.value = Array.isArray(data.comments) ? data.comments : []
    }
  } catch { post.value = null }
  finally { loading.value = false }
}

function navigatePost(id) {
  window.location.hash = '#/forum/post/' + id
}

async function handleLike() {
  if (!props.auth.token) { emit('login'); return }
  try {
    const r = await request(`/api/forum/posts/${props.postId}/like`, { method: 'POST' })
    post.value.likeCount = r.likeCount
    post.value.liked = r.liked
  } catch { }
}

async function submitComment(parentId) {
  const text = parentId ? replyText.value.trim() : commentText.value.trim()
  if (!text) return
  try {
    await request(`/api/forum/posts/${props.postId}/comments`, {
      method: 'POST', body: { content: text, parentId: parentId || null }
    })
    if (parentId) { replyText.value = ''; replyingTo.value = null }
    else commentText.value = ''
    const data = await request(`/api/forum/posts/${props.postId}`)
    allComments.value = data.comments || []
  } catch (e) { alert(e.message || '评论失败') }
}

function toggleReply(commentId) {
  replyingTo.value = replyingTo.value === commentId ? null : commentId
  replyText.value = ''
}

async function handleCommentLike(comment) {
  if (!props.auth.token) { emit('login'); return }
  try {
    const r = await request(`/api/forum/comments/${comment.id}/like`, { method: 'POST' })
    comment.likeCount = r.likeCount
  } catch {}
}

function scrollToComments() {
  commentsSection.value?.scrollIntoView({ behavior: 'smooth' })
}

function scrollToHeading(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

// scroll-spy
let scrollTimer = null
function onScroll() {
  if (scrollTimer) return
  scrollTimer = setTimeout(() => { scrollTimer = null }, 100)
  const items = tocItems.value
  if (!items.length) return
  let current = ''
  for (const item of items) {
    const el = document.getElementById(item.id)
    if (el && el.getBoundingClientRect().top <= 120) current = item.id
  }
  activeHeadingId.value = current
}

onMounted(() => {
  load()
  loadPostList()
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  if (scrollTimer) clearTimeout(scrollTimer)
})
watch(() => props.postId, () => {
  post.value = null; allComments.value = []; commentText.value = ''
  replyText.value = ''; replyingTo.value = null; activeHeadingId.value = ''
  load()
})

function formatDate(v) { return v ? String(v).slice(0, 10) : '' }
function formatRelativeDate(v) {
  if (!v) return ''
  const diff = Date.now() - new Date(v).getTime()
  const days = Math.floor(diff / 86400000)
  if (days === 0) return '今天'
  if (days === 1) return '1天前'
  if (days < 30) return days + '天前'
  return String(v).slice(0, 10)
}
</script>

<style scoped>
.forum-detail-page { }

/* 中间主内容 */
.detail-main { min-width: 0; }

/* 文章卡片 */
.post-article {
  background: #fff; border: 1px solid #e8ecf2; border-radius: 10px;
  padding: 28px 32px; margin-bottom: 20px;
}
.post-title {
  font-size: 26px; font-weight: 700; color: #1a1a2e; margin: 0 0 16px;
  line-height: 1.35; letter-spacing: -0.3px;
}
.post-author-line {
  display: flex; align-items: center; gap: 10px; margin-bottom: 14px;
}
.author-avatar {
  width: 32px; height: 32px; border-radius: 50%; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 14px; flex-shrink: 0;
}
.author-name { font-size: 14px; color: #39445a; font-weight: 500; }
.post-date { font-size: 13px; color: #8896a7; }
.post-tags { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 20px; }
.tag-pill {
  padding: 3px 12px; border-radius: 999px; font-size: 12px; font-weight: 500;
  background: #e8eef8; color: #2356a5;
}
.post-body {
  padding: 0; border: none; background: transparent; max-height: none;
  line-height: 1.85; font-size: 15px; color: #1a1a2e;
}

/* 评论区 */
.comments-section { margin-top: 8px; }
.comments-title {
  font-size: 17px; font-weight: 600; color: #1a202c; margin: 0 0 16px;
  padding-bottom: 12px; border-bottom: 1px solid #e8ecf2;
}
.comment-form { display: flex; flex-direction: column; gap: 8px; margin-bottom: 20px; }
.comment-form textarea {
  min-height: 80px; padding: 12px; border: 1px solid #d1d9e0; border-radius: 8px;
  resize: vertical; font-size: 14px; line-height: 1.6; font-family: inherit;
}
.comment-form textarea:focus { outline: none; border-color: #5b8cce; box-shadow: 0 0 0 2px rgba(91,140,206,.15); }
.comment-form button {
  align-self: flex-end; min-height: 34px; font-size: 13px; padding: 0 18px;
  border-radius: 6px; border: none; background: #2356a5; color: #fff; cursor: pointer; font-weight: 500;
}
.comment-form button:disabled { opacity: .5; cursor: not-allowed; }
.comment-form button:hover:not(:disabled) { background: #1d4a91; }
.comment-login-hint { color: #8896a7; padding: 12px 0; font-size: 13px; }
.comment-login-hint a { color: #2356a5; text-decoration: none; }

.comment-item { display: flex; gap: 12px; padding: 16px 0; border-bottom: 1px solid #f0f3f7; }
.comment-avatar {
  width: 36px; height: 36px; border-radius: 50%; background: #e8eef8; color: #2356a5;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 14px; flex-shrink: 0;
}
.comment-avatar.small { width: 28px; height: 28px; font-size: 12px; }
.comment-body { flex: 1; min-width: 0; }
.comment-header { display: flex; gap: 10px; align-items: baseline; margin-bottom: 6px; }
.comment-header strong { font-size: 14px; color: #39445a; }
.comment-time { color: #8896a7; font-size: 12px; }
.comment-text { margin: 0 0 8px; color: #39445a; line-height: 1.65; white-space: pre-wrap; font-size: 14px; }
.comment-actions { display: flex; gap: 14px; }
.comment-btn {
  display: inline-flex; align-items: center; gap: 4px;
  min-height: auto; padding: 2px 8px; font-size: 12px; color: #8896a7;
  background: transparent; border: none; cursor: pointer; border-radius: 4px;
}
.comment-btn:hover { background: #f0f3f7; color: #2356a5; }

.reply-form { margin: 10px 0 0; }
.reply-form textarea { min-height: 60px; width: 100%; padding: 10px; border: 1px solid #d1d9e0; border-radius: 6px; resize: vertical; font-size: 13px; font-family: inherit; }
.reply-form textarea:focus { outline: none; border-color: #5b8cce; }
.reply-actions { display: flex; gap: 8px; margin-top: 6px; justify-content: flex-end; }
.reply-actions button { min-height: 28px; font-size: 12px; padding: 0 12px; border-radius: 4px; border: 1px solid #d0d7de; background: #fff; color: #4a5568; cursor: pointer; }
.reply-actions button:not(.ghost) { background: #2356a5; color: #fff; border-color: #2356a5; }
.reply-actions button:not(.ghost):disabled { opacity: .5; cursor: not-allowed; }
.reply-actions button.ghost:hover { background: #f0f3f7; }

.reply-item { display: flex; gap: 10px; padding: 12px 0 0; }
.empty-state { color: #8896a7; font-size: 14px; padding: 24px 0; text-align: center; }
</style>
