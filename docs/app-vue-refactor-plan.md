# App.vue 拆分方案

> 当前状态：3151 行 → 目标：≤ 500 行

---

## 一、现状分析

### 1.1 App.vue 组成（3151 行）

| 部分 | 行数 | 占比 |
|------|------|------|
| 模板（template） | ~1100 | 35% |
| 脚本（script） | ~1500 | 48% |
| 样式（style） | ~550 | 17% |

### 1.2 模板中的路由页面（~730 行）

| 页面 | 行范围 | 行数 | 是否已有独立组件 |
|------|--------|------|----------------|
| 首页门户 | 47-143 | 100 | ❌ |
| 下载中心 | 144-192 | 50 | ❌ |
| 标准发布 | 193-326 | 130 | ❌ |
| 论坛列表 | 327-334 | 8 | ✅ ForumPostList |
| 论坛详情 | 335-345 | 11 | ✅ ForumPostDetail |
| 论坛编辑 | 346-355 | 10 | ✅ ForumPostEditor |
| 论坛个人中心 | 356-365 | 10 | ✅ ForumPersonalCenter |
| 知识库 | 366 | 1 | ✅ KnowledgePanel |
| Wiki | 367 | 1 | ✅ WikiPanel |
| 智能排查 | 368 | 1 | ✅ DiagnosticsPanel |
| 常用命令 | 370-447 | 80 | ❌ |
| 管理后台 | 457-830 | 370 | ⚠️ AdminPage 壳已创建，内容未迁移 |

### 1.3 模板中的模态框（~305 行）

| 模态框 | 行数 | 已替换为共享组件 |
|--------|------|-----------------|
| 编辑资源 | 50 | ✅ FormModal |
| 批量导入 | 40 | ✅ FormModal |
| 类型/分类/标准/参数编辑 | 80 | ✅ FormModal |
| 文档预览 | 45 | ❌ 仍是内联 |
| 用户/角色管理 | 45 | ✅ FormModal |
| 修订历史/审核详情 | 45 | ✅ BaseModal |

### 1.4 脚本中的函数（~1500 行）

| 类别 | 行数 | 状态 |
|------|------|------|
| 管理后台 CRUD（useAdmin） | ~600 | ⚠️ 状态已迁移到 composable，函数仍在 App.vue |
| 公共页面加载/交互 | ~200 | ❌ 未提取 |
| 标准页面逻辑 | ~150 | ❌ 未提取 |
| 命令页面逻辑 | ~100 | ❌ 未提取 |
| 登录/登出/路由 | ~100 | ✅ 已迁移到 composables |
| 辅助工具函数 | ~100 | ⚠️ 部分已迁移到 utils |
| 修订历史/审核逻辑 | ~100 | ⚠️ 状态在 composable，函数未迁移 |
| 下载/文件处理 | ~80 | ❌ 未提取 |
| 计算属性 | ~70 | ❌ 未提取 |

### 1.5 样式（~550 行）

- 门户页面样式：~100 行
- 下载中心样式：~50 行
- 标准页面样式：~100 行
- 命令页面样式：~80 行
- 管理后台样式：~120 行
- 通用布局样式：~100 行

---

## 二、拆分策略

### 2.1 原则

1. **一个路由一个组件**：每个页面级别的路由对应一个独立的 `.vue` 文件
2. **组件只管模板和交互**：数据加载、业务逻辑放在 composable 或 service 中
3. **通过 props/events 通信**：父组件传递数据和回调，子组件通过 emit 通知父组件
4. **样式跟着组件走**：每个组件的 `<style scoped>` 只包含自己的样式
5. **渐进式迁移**：每次提取一个组件，确保构建通过后再继续

### 2.2 目标结构

```
frontend/src/
├── App.vue                          # ≤ 500 行（壳：路由 + 全局布局 + toast）
├── pages/
│   ├── HomePage.vue                 # 首页门户
│   ├── DownloadsPage.vue            # 下载中心
│   ├── StandardsPage.vue            # 标准发布
│   ├── CommandsPage.vue             # 常用命令
│   └── admin/
│       ├── AdminPage.vue            # 管理后台布局（侧边栏 + 内容区）
│       ├── FilesSection.vue         # 文件管理
│       ├── TypesSection.vue         # 类型管理
│       ├── StandardsSection.vue     # 参数标准
│       ├── DocumentsSection.vue     # 标准文档
│       ├── ReviewsSection.vue       # 审核管理
│       ├── UsersSection.vue         # 用户管理
│       └── SettingsSection.vue      # 系统设置
├── components/
│   ├── ui/                          # 共享 UI 组件（已完成）
│   ├── WikiPanel.vue
│   ├── KnowledgePanel.vue
│   ├── DiagnosticsPanel.vue
│   ├── ForumPostList.vue
│   ├── ForumPostDetail.vue
│   ├── ForumPostEditor.vue
│   └── ForumPersonalCenter.vue
├── composables/
│   ├── useAuth.js                   # ✅ 已完成
│   ├── useNotify.js                 # ✅ 已完成
│   ├── useRoute.js                  # ✅ 已完成
│   ├── useAdmin.js                  # ✅ 已完成（状态）
│   ├── usePublic.js                 # 公共页面逻辑
│   ├── useStandards.js              # 标准页面逻辑
│   └── useCommands.js               # 命令页面逻辑
├── utils/
│   └── index.js                     # ✅ 已完成
└── styles/
    └── tokens.css                   # ✅ 已完成
```

---

## 三、分步实施计划

### 第一步：提取 HomePage.vue（~100 行模板 + ~50 行脚本）

**提取内容：**
- 模板：门户英雄区、模块卡片、软件轮播、快速入口
- 脚本：`loadPublic()`、`openPortalModule()`、相关 computed
- 样式：`.portal-page`、`.portal-topbar`、`.hero`、`.module-cards` 等

**接口设计：**
```vue
<!-- HomePage.vue -->
<script setup>
defineProps({
  auth: Object,
  siteConfig: Object
})
defineEmits(['navigate'])
</script>
```

**App.vue 变化：**
```vue
<!-- 替换前：100 行模板 -->
<section v-if="route.name === 'home'" class="portal-page">
  ...（100 行）
</section>

<!-- 替换后：1 行 -->
<HomePage v-if="route.name === 'home'" :auth="auth" :siteConfig="siteConfig" @navigate="navigate" />
```

**预计减少：~150 行**

---

### 第二步：提取 DownloadsPage.vue（~50 行模板 + ~80 行脚本）

**提取内容：**
- 模板：资源列表、详情视图、筛选器
- 脚本：`loadPublic()`、`openDetail()`、`closeDetail()`、`handleDownload()`、`publicFilters`、`publicPage` 等
- 样式：`.workspace`、`.resource-card`、`.detail-panel` 等

**接口设计：**
```vue
<script setup>
const props = defineProps({ auth: Object })
const emit = defineEmits(['navigate'])
// 内部管理 publicFilters、publicPage、selectedRelease 等状态
</script>
```

**预计减少：~130 行**

---

### 第三步：提取 StandardsPage.vue（~130 行模板 + ~150 行脚本）

**提取内容：**
- 模板：标准列表、标准详情、参数表格、目录导航
- 脚本：`loadPublicStandards()`、`loadPublicStandardDetail()`、`loadPublicDocuments()`、`publicStandardParams`、`selectedPublicStandard` 等
- 样式：`.standards-page`、`.standard-toc`、`.param-table` 等

**接口设计：**
```vue
<script setup>
const props = defineProps({ auth: Object })
// 内部管理所有标准页面状态
</script>
```

**预计减少：~280 行**

---

### 第四步：提取 CommandsPage.vue（~80 行模板 + ~100 行脚本）

**提取内容：**
- 模板：命令列表、命令详情、搜索、分类筛选
- 脚本：`loadCmdTypes()`、`loadCmdCommands()`、`saveCommand()`、`deleteCommand()`、`cmdForm` 等
- 样式：`.commands-page`、`.cmd-card` 等

**接口设计：**
```vue
<script setup>
const props = defineProps({ auth: Object, notify: Function })
</script>
```

**预计减少：~180 行**

---

### 第五步：管理后台模板迁移到 AdminPage.vue（~370 行模板）

**提取内容：**
- 管理后台的完整布局（侧边栏 + 内容区 + 各 Tab 面板）
- 每个 Tab 面板提取为独立的 Section 组件

**子组件拆分：**

| 组件 | 内容 | 行数 |
|------|------|------|
| FilesSection | 文件列表 + 筛选 + 分页 | ~80 |
| TypesSection | 类型列表 + 分类管理 | ~60 |
| StandardsSection | 参数标准列表 | ~50 |
| DocumentsSection | 标准文档列表 | ~50 |
| ReviewsSection | 审核列表 + 审核操作 | ~60 |
| UsersSection | 用户列表 + 角色管理 | ~40 |
| SettingsSection | 系统设置表单 | ~30 |

**接口设计：**
```vue
<!-- AdminPage.vue -->
<script setup>
defineProps({
  adminSection: String,
  isSysAdmin: Boolean,
  // 各 Section 需要的数据通过 props 传入
})
defineEmits(['switchSection', 'logout', 'showPassword'])
</script>
```

**预计减少：~370 行**

---

### 第六步：提取 useAdmin 函数（~600 行脚本）

**当前状态：** useAdmin composable 已包含全部状态和函数签名，但函数实现与 App.vue 有差异。

**需要做的：**
1. 逐个对齐 composable 函数与 App.vue 原函数的实现
2. 确保 composable 函数使用正确的 API 路径和数据处理
3. 移除 App.vue 中的旧函数

**对齐清单：**

| 函数 | 差异点 | 优先级 |
|------|--------|--------|
| `loadStandardDocuments` | API 路径不同，App.vue 用 `standardApiBase()` + `normalizeDoc` | 高 |
| `loadStandardParameters` | App.vue 用 `fetchStandardParameters` 根据 adminSection 选择参数名 | 高 |
| `saveRelease` | App.vue 用 FormData 上传文件 | 高 |
| `saveStandard` | App.vue 有更复杂的表单处理 | 中 |
| `saveParameter` | App.vue 有 `standardDocumentId` 关联 | 中 |
| `deleteUserAccount` | App.vue 用 `confirmAction` 而非 `confirm` | 低 |

**预计减少：~600 行**

---

### 第七步：提取模态框到独立组件（~305 行模板）

**仍需提取的模态框：**

| 模态框 | 目标组件 | 行数 |
|--------|---------|------|
| 文档预览 | DocumentPreviewModal.vue | ~45 |
| 修订历史 | RevisionHistoryModal.vue | ~40 |
| 审核详情 | ReviewDetailModal.vue | ~50 |

**预计减少：~135 行**

---

### 第八步：样式迁移（~550 行）

**迁移策略：**
1. 每个页面组件的样式写在自己的 `<style scoped>` 中
2. 全局通用样式（reset、布局）保留在 App.vue 或迁移到 `styles/global.css`
3. 设计令牌变量已在 `styles/tokens.css`，所有组件必须使用

**预计减少：~500 行**

---

## 四、执行顺序和依赖关系

```
第一步 HomePage ──┐
第二步 DownloadsPage ──┤
第三步 StandardsPage ──┼── 独立并行，无依赖
第四步 CommandsPage ──┤
第五步 AdminPage 模板 ──┘
                      │
第六步 useAdmin 函数对齐 ── 依赖第五步完成
                      │
第七步 模态框提取 ── 依赖第五步完成
                      │
第八步 样式迁移 ── 依赖前面所有步骤完成
```

---

## 五、风险和注意事项

### 5.1 风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| composable 函数实现差异 | 替换后功能异常 | 逐个函数对比，写测试验证 |
| props 传递链过长 | 代码可读性差 | 使用 provide/inject 或 composable 直接在子组件中调用 |
| 样式冲突 | 页面显示异常 | 使用 scoped style，不依赖全局类名 |
| 路由切换状态丢失 | 用户体验差 | 重要状态提升到 App.vue 或 composable |

### 5.2 不动的部分

- **路由解析逻辑**：保留在 App.vue（syncRoute 函数）
- **全局认证状态**：保留在 useAuth composable
- **全局通知**：保留在 useNotify composable
- **Toast/ConfirmDialog**：保留在 App.vue 模板中

### 5.3 验证标准

每步完成后必须：
1. `npx vite build` 构建通过
2. 所有路由页面手动验证功能正常
3. 管理后台 CRUD 操作正常
4. 登录/登出正常

---

## 六、预期效果

| 指标 | 当前 | 目标 |
|------|------|------|
| App.vue 行数 | 3151 | ≤ 500 |
| 独立页面组件 | 7 | 12 |
| Composables | 4 | 7 |
| 共享 UI 组件 | 16 | 19 |

### App.vue 最终形态（≤ 500 行）

```vue
<template>
  <div class="app-shell">
    <header>导航栏</header>
    <main>
      <HomePage v-if="route.name === 'home'" />
      <DownloadsPage v-else-if="route.name === 'public'" />
      <StandardsPage v-else-if="route.name === 'standards'" />
      <ForumPostList v-else-if="route.name === 'forum'" />
      <!-- ... 其他路由 ... -->
      <AdminPage v-else-if="route.name === 'admin'" />
    </main>
    <Toast :notice="notice" />
    <ConfirmDialog v-model="confirmDialog" />
  </div>
</template>

<script setup>
import { useAuth } from './composables/useAuth'
import { useNotify } from './composables/useNotify'
import { useRoute } from './composables/useRoute'
// ... 页面组件导入 ...

const { auth, restoreAuth, isSysAdmin, canAccessAdmin } = useAuth()
const { notice, notify, confirmDialog } = useNotify()
const { route, syncRoute, navigate } = useRoute()

// 只保留全局级别的逻辑：路由同步、登录/登出、站点配置加载
</script>
```
