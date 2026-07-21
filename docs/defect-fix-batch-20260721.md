# 缺陷修复批次说明（2026-07-21）

## 后端

### B1 审计日志 Mapper 未绑定

- 根因：审计 Mapper XML 位于 `common-web`，各部署服务各自维护 `mybatis.mapper-locations`，`ai-service` 等服务漏配后只注册了 Mapper 接口，没有加载 `insert` statement。
- 修复：`common-web` 新增 Spring Boot 自动配置，通过 `SqlSessionFactoryBeanCustomizer` 统一追加 `classpath*:mapper/ApiAuditLogMapper.xml`；各服务保留现有 `com.middleware.manager.repository` Mapper 扫描并删除重复的审计 XML 配置。
- 验证：`TC-AI-004` 启动 `ai-service`，同时断言 `ApiAuditLogMapper` Bean 存在且 MyBatis Configuration 包含 `com.middleware.manager.repository.ApiAuditLogMapper.insert`。

### B2 论坛 FULLTEXT 索引缺失

- 根因：论坛搜索执行 `MATCH(title, content) AGAINST(...)`，但 `forum_posts` 没有对应 FULLTEXT 索引。
- 修复：新增 `V20260721__add_forum_post_fulltext_index.sql`，为 `(title, content)` 创建使用 `ngram` parser 的 FULLTEXT 索引。脚本注明依赖 MySQL 全局 `ngram_token_size`，默认值为 2。
- 验证：`TC-COMMUNITY-008` 校验迁移资源、索引列、ngram parser 及参数说明。

### B3 中文文件名响应头非法

- 根因：`KnowledgeController` 将原始中文标题直接写入 `Content-Disposition` 的 `filename`，Servlet 容器无法输出非 ASCII header。
- 修复：生成 ASCII fallback，并增加 UTF-8 百分号编码的 `filename*`：`attachment; filename="..."; filename*=UTF-8''...`。
- 验证：`TC-KNOWLEDGE-001` 校验响应头只含 ASCII、包含 RFC 5987 参数且不包含原始中文字符。

### B4 查询参数异常返回 500

- 根因：缺少必填参数和参数类型转换失败未被精确处理，落入通用异常兜底。
- 修复：`ApiExceptionHandler` 增加 `MissingServletRequestParameterException`、`MethodArgumentTypeMismatchException` 处理，统一返回 `400 / PARAM_INVALID / 参数校验失败`。
- 验证：`TC-WEB-001`、`TC-WEB-002` 分别覆盖缺参和类型错误。

### B5 下载计数提前增加

- 根因：Controller 在加载文件和校验 Range 前增加下载计数，文件缺失或返回 416 时也会计数。
- 修复：两个下载入口共用响应构建逻辑；仅在文件加载、媒体类型解析和 Range 校验完成且响应为 2xx 后增加计数。
- 验证：`TC-CATALOG-001` 断言非法 Range 不计数；`TC-CATALOG-002` 断言文件校验先于计数。

## 前端

### F1 手机端论坛布局

- 根因：760px 媒体规则只调整了外层岗位导航，`.forum-body` 仍保留 `1fr 260px` 两列。
- 修复：移动端将论坛正文和标签栏改为单列，正文取消视口高度滚动限制，搜索栏允许换行。
- 验证：`TC-PORTAL-009` 校验移动端单列和正文高度规则。

### F2 论坛搜索异常提示

- 根因：文章请求失败后直接清空列表，未展示错误，也未调用统一通知。
- 修复：搜索失败时保留上一次成功结果，展示 `role="alert"` 页内错误，并通过 `notify(message, 'error')` 触发全局提示。
- 验证：`TC-PORTAL-008` 模拟 500，断言旧结果保留、空状态不出现且错误提示可见。

### F3 相对尺寸测试

- 根因：测试硬编码按钮高度 `32px`，与组件使用 `2.5em` 的相对尺寸实现冲突。
- 修复：断言改为 `em/rem` 单位及 2 至 3 的合理比例范围，保留对紧凑按钮的语义约束。
- 验证：`portal-layout-optimization.test.js` 的 `TC-01`。

### F4 npm 锁文件与 Node 版本

- 根因：锁文件缺少 Vitest 内嵌 Vite 可选平台包和嵌套 PostCSS 记录；开发 Node 主版本未锁定。
- 修复：补齐并校验 `package-lock.json`，新增 `.nvmrc`（`20`），`engines.node` 固定为 `>=20.19 <21`。
- 验证：Node 20 下执行 `npm ci`；本批在受限沙箱中已通过 `npm ci --package-lock-only --offline` 的一致性检查。

### F5 Vite 大包警告

- 根因：知识库、Wiki 和诊断面板在入口静态加载，图谱、PDF、文档预览等重依赖进入同一大 chunk。
- 修复：三个低频面板改为异步组件；Vite 按 Vue、Three.js、ForceGraph、D3、PDF、文档预览和 Markdown 分包，并将告警阈值设为适配 Three.js 单模块体积的 900 kB。
- 验证：Node 20 下执行 `npm run build`，构建日志不应再出现 chunk size 警告，并检查各 `vendor-*` chunk 独立输出。

## 验证命令

```bash
cd backend
mvn -q -DskipTests test-compile
mvn test

cd ../frontend
nvm use
npm ci
npm run test
npm run build
```

本批未修改下载资源内容、浏览器标签标题和 `middleware_commands` 数据/模型。
