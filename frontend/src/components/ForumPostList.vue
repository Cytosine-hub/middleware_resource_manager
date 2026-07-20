<template>
  <div class="forum-page public-module-layout">
    <JobNavigation :model-value="selectedJob" @update:model-value="selectJob" />
    <div class="public-module-content">
    <div class="forum-hero">
      <p>沉淀基础设施实践经验，支持问题讨论、方案交流和知识共享</p>
      <div class="forum-hero-bar">
        <input v-model.trim="keyword" placeholder="搜索文章标题或内容..." @keyup.enter="search" />
        <button @click="search">搜索</button>
        <button v-if="auth.token" class="primary-btn" @click="$emit('newPost')">发表文章</button>
        <button v-if="auth.token" class="ghost" @click="$emit('goMine')">个人中心</button>
      </div>
    </div>

    <div class="forum-body">
      <div class="forum-main" ref="scrollContainer" @scroll="onScroll">
        <template v-if="posts.length > 0 || loading">
          <article v-for="post in posts" :key="post.id" class="forum-card" @click="$emit('openPost', post.id)">
            <div class="forum-card-body">
              <h3>{{ post.title }}</h3>
              <p class="forum-card-summary">{{ post.summary }}</p>
              <div class="forum-card-tags">
                <span v-for="tag in (post.tags || [])" :key="tag" class="forum-tag" @click.stop="filterByTag(tag)">{{ tag }}</span>
              </div>
            </div>
            <div class="forum-card-meta">
              <span>{{ post.authorDisplayName }}</span>
              <span>{{ formatDate(post.createdAt) }}</span>
              <span>{{ post.viewCount }} 阅读</span>
              <span>{{ post.likeCount }} 赞</span>
              <span>{{ post.commentCount }} 评论</span>
            </div>
          </article>
          <div v-if="loadingMore" class="forum-loading-more">
            <div class="spinner"></div>
            <span>加载中...</span>
          </div>
          <p v-if="!hasMore && posts.length > 0" class="forum-no-more">— 已加载全部文章 —</p>
        </template>
        <div v-if="loading && posts.length === 0" class="loading-panel"><div class="spinner"></div><p>加载中...</p></div>
        <EmptyState v-if="!loading && posts.length === 0" message="当前岗位暂无文章，可切换其他岗位或发表第一篇内容。" />
      </div>

      <aside class="forum-sidebar">
        <div class="forum-sidebar-card">
          <h4>热门标签</h4>
          <div class="forum-tag-cloud">
            <button v-for="tag in tags" :key="tag.name" :class="['forum-tag', { active: activeTag === tag.name }]"
                    @click="filterByTag(tag.name)">
              {{ tag.name }} <small>{{ tag.postCount }}</small>
            </button>
          </div>
        </div>
      </aside>
    </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { request } from '../api'
import EmptyState from './ui/EmptyState.vue'
import JobNavigation from '../shared/jobs/JobNavigation.vue'
import { getJobCategory } from '../shared/jobs/jobFilter.js'
import { useJobFilter } from '../shared/jobs/useJobFilter.js'

const PAGE_SIZE = 10
const SCROLL_THRESHOLD = 100
const HERO_HEIGHT_OFFSET = 260

const props = defineProps({ auth: Object })
const emit = defineEmits(['openPost', 'newPost', 'goMine'])

const posts = ref([])
const tags = ref([])
const keyword = ref('')
const activeTag = ref('')
const page = ref(0)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const scrollContainer = ref(null)
const { selectedJob, selectJob: persistSelectedJob } = useJobFilter()

async function loadPosts(reset = false) {
  if (reset) {
    page.value = 0
    hasMore.value = true
    posts.value = []
  }
  if (!hasMore.value) return

  if (page.value === 0) loading.value = true
  else loadingMore.value = true

  try {
    const params = new URLSearchParams({
      keyword: keyword.value,
      tag: activeTag.value,
      job: getJobCategory(selectedJob.value),
      page: page.value,
      size: PAGE_SIZE
    })
    const data = await request(`/api/forum/posts?${params}`, { token: null })
    const newPosts = Array.isArray(data?.content) ? data.content : []
    if (page.value === 0) {
      posts.value = newPosts
    } else {
      posts.value = [...posts.value, ...newPosts]
    }
    hasMore.value = !data?.last
  } catch {
    if (page.value === 0) posts.value = []
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadTags() {
  try { tags.value = await request('/api/forum/tags', { token: null }) } catch { tags.value = [] }
}

function search() { loadPosts(true) }
function filterByTag(tag) { activeTag.value = activeTag.value === tag ? '' : tag; loadPosts(true) }
function selectJob(jobId) { persistSelectedJob(jobId); loadPosts(true) }

function onScroll() {
  const el = scrollContainer.value
  if (!el || loadingMore.value || !hasMore.value) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - SCROLL_THRESHOLD) {
    page.value++
    loadPosts()
  }
}

function formatDate(v) { return v ? String(v).slice(0, 10) : '' }

onMounted(() => { loadPosts(); loadTags() })
onUnmounted(() => { scrollContainer.value?.removeEventListener('scroll', onScroll) })
watch(() => props.auth.token, () => { loadPosts(true) })
</script>

<style scoped>
.forum-page { padding-top: var(--space-xl); }
.public-module-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 6fr); gap: var(--space-xl); }
.public-module-content { min-width: 0; }
.forum-hero {
  background: linear-gradient(120deg, #1a3650, #1a4a6e);
  color: #fff; padding: var(--space-xl) var(--space-lg) var(--space-lg); border-radius: var(--radius-lg); margin-bottom: var(--space-lg);
}
.forum-hero h2 { margin: 0 0 var(--space-sm); font-size: var(--text-2xl); letter-spacing: 0; }
.forum-hero p { margin: 0 0 var(--space-md); color: #a0c4e0; }
.forum-hero-bar { display: flex; gap: var(--space-sm); }
.forum-hero-bar input { flex: 1; min-height: 40px; border-radius: var(--radius-md); border: none; padding: 0 var(--space-md); font-size: var(--text-base); }
.forum-hero-bar input:focus { outline: none; }
.forum-hero-bar button { min-height: 40px; white-space: nowrap; }
.primary-btn { background: #e8890c; }
.primary-btn:hover { background: #c7740a; }
.forum-body { display: grid; grid-template-columns: 1fr 260px; gap: var(--space-lg); align-items: start; }
.forum-main {
  min-width: 0; max-height: calc(100vh - 260px); overflow-y: auto;
}
.forum-card {
  border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: var(--space-md) var(--space-lg); margin-bottom: var(--space-md);
  background: var(--color-bg); cursor: pointer; transition: box-shadow 0.15s;
}
.forum-card:hover { box-shadow: 0 4px 16px rgba(35,58,93,0.08); }
.forum-card h3 { margin: 0 0 var(--space-sm); font-size: var(--text-lg); letter-spacing: 0; }
.forum-card-summary { color: var(--color-text-secondary); font-size: var(--text-sm); line-height: 1.6; margin: 0 0 var(--space-sm); }
.forum-card-tags { display: flex; flex-wrap: wrap; gap: var(--space-sm); margin-bottom: var(--space-sm); }
.forum-tag {
  display: inline-block; padding: 2px 10px; border-radius: var(--radius-full); font-size: var(--text-xs);
  background: var(--color-bg-secondary); color: var(--color-text-secondary); border: none; cursor: pointer;
}
.forum-tag:hover, .forum-tag.active { background: var(--color-primary); color: #fff; }
.forum-card-meta { display: flex; gap: var(--space-md); font-size: var(--text-xs); color: var(--color-text-tertiary); }
.forum-sidebar-card { border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: var(--space-md); background: var(--color-bg); }
.forum-sidebar-card h4 { margin: 0 0 var(--space-sm); font-size: var(--text-sm); letter-spacing: 0; }
.forum-tag-cloud { display: flex; flex-wrap: wrap; gap: var(--space-sm); }
.forum-tag-cloud .forum-tag small { color: var(--color-text-tertiary); margin-left: 2px; }
.forum-loading-more {
  display: flex; align-items: center; justify-content: center; gap: var(--space-sm);
  padding: var(--space-lg) 0; color: var(--color-text-tertiary); font-size: var(--text-sm);
}
.forum-loading-more .spinner {
  width: 20px; height: 20px; border: 2px solid var(--color-border); border-top-color: var(--color-primary);
  border-radius: 50%; animation: spin 0.6s linear infinite;
}
.forum-no-more {
  text-align: center; padding: var(--space-lg) 0; color: var(--color-text-tertiary); font-size: var(--text-sm);
}
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 760px) { .public-module-layout { grid-template-columns: 1fr; } }
</style>
