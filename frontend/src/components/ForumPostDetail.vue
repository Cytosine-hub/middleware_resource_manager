<template>
  <div class="forum-detail-page">
    <div v-if="loading" class="loading-panel"><div class="spinner"></div><p>加载中...</p></div>
    <template v-else-if="post">
      <article class="post-content">
        <h1>{{ post.title }}</h1>
        <div class="post-meta">
          <span>{{ post.authorDisplayName }}</span>
          <span>发布于 {{ formatDate(post.createdAt) }}</span>
          <span>{{ post.viewCount }} 阅读</span>
        </div>
        <div class="post-tags">
          <span v-for="tag in (post.tags || [])" :key="tag" class="forum-tag">{{ tag }}</span>
        </div>
        <div class="post-body markdown-preview" v-html="renderedContent"></div>
        <div class="post-actions">
          <button :class="{ liked: post.liked }" @click="handleLike">
            👍 {{ post.likeCount }} 赞
          </button>
          <button @click="showCommentBox = !showCommentBox">💬 评论 ({{ allComments.length }})</button>
          <button v-if="auth.token && auth.user?.username === post.authorUsername" class="ghost" @click="$emit('editPost', post.id)">编辑</button>
        </div>
      </article>

      <section class="comments-section">
        <h3>评论 ({{ allComments.length }})</h3>
        <div v-if="showCommentBox && auth.token" class="comment-form">
          <textarea v-model="commentText" placeholder="写下你的评论..." rows="3"></textarea>
          <button :disabled="!commentText.trim()" @click="submitComment(null)">发表评论</button>
        </div>
        <div v-if="!auth.token" class="comment-login-hint">
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
              <p class="comment-content">{{ c.content }}</p>
              <div class="comment-actions">
                <button class="comment-btn" @click="handleCommentLike(c)">👍 {{ c.likeCount || 0 }}</button>
                <button class="comment-btn" @click="toggleReply(c.id)">💬 回复</button>
              </div>
              <div v-if="replyingTo === c.id" class="comment-form reply-form">
                <textarea v-model="replyText" placeholder="写下回复..." rows="2"></textarea>
                <div>
                  <button class="ghost" @click="replyingTo = null">取消</button>
                  <button :disabled="!replyText.trim()" @click="submitComment(c.id)">回复</button>
                </div>
              </div>
              <div v-for="reply in getReplies(c.id)" :key="reply.id" class="comment-item reply-item">
                <div class="comment-avatar small">{{ reply.authorDisplayName?.charAt(0) }}</div>
                <div class="comment-body">
                  <div class="comment-header">
                    <strong>{{ reply.authorDisplayName }}</strong>
                    <span class="comment-time">{{ formatDate(reply.createdAt) }}</span>
                  </div>
                  <p class="comment-content">{{ reply.content }}</p>
                  <div class="comment-actions">
                    <button class="comment-btn" @click="handleCommentLike(reply)">👍 {{ reply.likeCount || 0 }}</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
        <p v-if="allComments.length === 0 && auth.token" class="empty-state">暂无评论，快来发表第一条评论吧！</p>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { request } from '../api'

const props = defineProps({ auth: Object, postId: [String, Number], markdown: Object })
const emit = defineEmits(['back', 'editPost', 'login'])

const post = ref(null)
const allComments = ref([])
const commentText = ref('')
const replyText = ref('')
const replyingTo = ref(null)
const showCommentBox = ref(true)
const loading = ref(false)

const renderedContent = computed(() => {
  try { return props.markdown.render(post.value?.content || '') } catch { return post.value?.content || '' }
})

const topLevelComments = computed(() => (allComments.value || []).filter(c => !c.parentId))
const getReplies = (parentId) => (allComments.value || []).filter(c => c.parentId === parentId)

async function load() {
  loading.value = true
  post.value = null
  allComments.value = []
  try {
    const data = await request(`/api/forum/posts/${props.postId}`, { token: null })
    post.value = data || null
    if (post.value) {
      post.value.tags = post.value.tags || []
      allComments.value = Array.isArray(data.comments) ? data.comments : []
    }
  } catch { post.value = null }
  finally { loading.value = false }
}

async function handleLike() {
  if (!props.auth.token) { emit('login'); return }
  try {
    const r = await request(`/api/forum/posts/${props.postId}/like`, { method: 'POST' })
    post.value.likeCount = r.likeCount
    post.value.liked = true
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
    const data = await request(`/api/forum/posts/${props.postId}`, { token: null })
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

function formatDate(v) { return v ? String(v).slice(0, 10) : '' }

onMounted(() => load())
watch(() => props.postId, () => {
  post.value = null
  allComments.value = []
  commentText.value = ''
  replyText.value = ''
  replyingTo.value = null
  load()
})
</script>

<style scoped>
.forum-detail-page { }
.back-btn { margin-bottom: 16px; }
.post-content { border: 1px solid #e3e9f1; border-radius: 8px; padding: 24px; background: #fff; margin-bottom: 20px; }
.post-content h1 { margin: 0 0 10px; font-size: 24px; letter-spacing: 0; }
.post-meta { display: flex; gap: 14px; color: #8896a7; font-size: 13px; margin-bottom: 10px; }
.post-tags { display: flex; gap: 6px; margin-bottom: 16px; }
.forum-tag { padding: 2px 10px; border-radius: 999px; font-size: 12px; background: #eef2f7; color: #526071; }
.post-body { padding: 0; border: none; background: transparent; max-height: none; line-height: 1.8; }
.post-actions { display: flex; gap: 12px; margin-top: 20px; padding-top: 16px; border-top: 1px solid #e3e9f1; }
.post-actions button { font-size: 13px; min-height: 34px; }
.post-actions button.liked { background: #e8890c; }
.comments-section { margin-top: 24px; }
.comments-section h3 { margin: 0 0 14px; font-size: 16px; letter-spacing: 0; }
.comment-form { display: flex; flex-direction: column; gap: 8px; margin-bottom: 16px; }
.comment-form textarea { min-height: 80px; padding: 10px; border: 1px solid #d1d9e0; border-radius: 6px; resize: vertical; font-size: 14px; }
.comment-form button { align-self: flex-end; min-height: 34px; font-size: 13px; }
.comment-login-hint { color: #8896a7; padding: 12px 0; font-size: 13px; }
.comment-item { display: flex; gap: 12px; padding: 14px 0; border-bottom: 1px solid #f0f3f7; }
.comment-avatar {
  width: 36px; height: 36px; border-radius: 50%; background: #e8eef8; color: #2356a5; display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 14px; flex-shrink: 0;
}
.comment-avatar.small { width: 28px; height: 28px; font-size: 12px; }
.comment-body { flex: 1; min-width: 0; }
.comment-header { display: flex; gap: 10px; align-items: baseline; margin-bottom: 4px; }
.comment-header strong { font-size: 14px; }
.comment-time { color: #8896a7; font-size: 12px; }
.comment-content { margin: 0 0 6px; color: #39445a; line-height: 1.6; white-space: pre-wrap; }
.comment-actions { display: flex; gap: 12px; }
.comment-btn {
  min-height: auto; padding: 2px 6px; font-size: 12px; color: #8896a7; background: transparent; border: none; cursor: pointer; border-radius: 4px;
}
.comment-btn:hover { background: #f0f3f7; color: #2356a5; }
.reply-form { margin: 10px 0 0; padding-left: 0; }
.reply-form textarea { min-height: 60px; }
.reply-form div { display: flex; gap: 8px; align-self: flex-end; }
.reply-form div button { min-height: 28px; font-size: 12px; padding: 0 10px; }
.reply-item { border-bottom: none; padding: 10px 0 0; }
</style>
