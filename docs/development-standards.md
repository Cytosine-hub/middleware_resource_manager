# 开发规范

本文档定义了项目的前后端开发规范，所有新代码必须遵循。

---

## 一、前端规范

### 1. 技术栈

- **框架**: Vue 3 + `<script setup>` 组合式 API
- **路由**: 自定义 hash 路由（`useRoute` 组合式函数）
- **状态管理**: 组合式函数模块级单例（无 Vuex/Pinia）
- **构建**: Vite

### 2. 目录结构

```
src/
├── App.vue                    # 根组件（路由、全局布局）
├── api.js                     # HTTP 请求封装（fetch + Bearer token）
├── composables/               # 组合式函数
│   ├── useAuth.js             # 认证状态、角色判断
│   ├── useRoute.js            # Hash 路由
│   ├── useNotify.js           # Toast 通知、确认对话框
│   └── useAdmin.js            # 后台管理 CRUD 操作
├── components/
│   ├── ui/                    # 通用 UI 组件（必须使用设计令牌）
│   │   ├── BaseButton.vue
│   │   ├── BaseModal.vue
│   │   ├── FormModal.vue
│   │   └── ...
│   └── *.vue                  # 业务组件
├── pages/
│   ├── *.vue                  # 公开页面
│   └── admin/
│       ├── AdminPage.vue      # 后台管理主页
│       └── *Section.vue       # 各管理模块
├── styles/
│   └── tokens.css             # 设计令牌（CSS 变量）
├── styles.css                 # 全局基础样式（遗留，逐步迁移）
└── utils/
    └── index.js               # 工具函数
```

### 3. 组件规范

```vue
<template>...</template>
<script setup>
import { ref, reactive, computed } from 'vue'

// Props（对象语法，含 type/default/required）
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, required: true }
})

// Emits
const emit = defineEmits(['update:modelValue', 'submit'])

// 局部状态
const count = ref(0)
const form = reactive({ name: '' })
</script>
<style scoped>
/* 使用设计令牌，禁止硬编码 */
.btn { padding: var(--space-sm) var(--space-md); }
</style>
```

**命名约定**:
- UI 组件: `Base` 前缀或描述性名词（`BaseButton`、`DataTable`）
- 业务组件: PascalCase（`DocumentEditor`、`ForumPostList`）
- 管理模块: `*Section` 后缀（`FilesSection`、`UsersSection`）

**通信方式**:
- Props 向下传递，Events 向上冒泡
- 禁止使用 `$refs`、`$parent` 直接访问
- 模态框使用 `v-model`（`modelValue` + `update:modelValue`）

### 4. 组合式函数规范

```javascript
// composables/useExample.js
import { reactive, ref } from 'vue'

// 模块级单例状态
const state = reactive({ data: [] })
const loading = ref(false)

export function useExample() {
  async function fetchData() { ... }
  function reset() { ... }

  return { state, loading, fetchData, reset }
}
```

**使用方式**:
```javascript
const { state, loading, fetchData } = useExample()
```

### 5. 样式规范

#### 设计令牌（`tokens.css`）— 新组件必须使用

```css
:root {
  /* 颜色 */
  --color-primary: #2356a5;
  --color-primary-hover: #1a4480;
  --color-primary-light: #e8f0fe;
  --color-success: #16a34a;
  --color-danger: #dc2626;
  --color-warning: #d97706;
  --color-info: #0ea5e9;

  /* 背景 */
  --color-bg: #ffffff;
  --color-bg-secondary: #f8fafc;
  --color-bg-tertiary: #f1f5f9;

  /* 文本 */
  --color-text: #1e293b;
  --color-text-secondary: #64748b;
  --color-text-tertiary: #94a3b8;

  /* 边框 */
  --color-border: #e2e8f0;

  /* 间距: --space-xs(4px) ~ --space-3xl(48px) */
  /* 圆角: --radius-sm(4px) ~ --radius-full(9999px) */
  /* 字号: --text-xs(12px) ~ --text-3xl(24px) */
  /* 阴影: --shadow-sm ~ --shadow-xl */
  /* 过渡: --transition-fast(150ms) ~ --transition-slow(300ms) */
}
```

**规则**:
- 所有 `components/ui/` 下的组件必须使用设计令牌
- 禁止在新代码中硬编码颜色值
- 修改旧代码时顺带迁移相关样式

### 6. API 请求规范

```javascript
import { request } from '../api'

// GET 请求
const data = await request('/api/public/standards')

// POST 请求
await request('/api/admin/releases', { method: 'POST', body: formData })

// 无认证请求
const data = await request('/api/public/standards', { token: null })
```

**API 路径约定**:
- 管理接口: `/api/admin/{resource}`
- 公开接口: `/api/public/{resource}`
- 认证接口: `/api/auth/*`

---

## 二、后端规范

### 1. 技术栈

- **框架**: Spring Boot 3.5.3 + Java 17
- **ORM**: MyBatis（XML 映射）
- **数据库**: MySQL 8.0
- **安全**: Spring Security + 自定义 Token 认证

### 2. 目录结构

```
com.middleware.manager/
├── domain/                    # 实体类（Lombok POJO）
├── repository/                # MyBatis Mapper 接口
├── service/                   # 业务逻辑
├── constant/                  # 常量定义（错误码、业务常量）
├── web/
│   ├── api/                   # REST 控制器
│   │   ├── dto/               # 请求/响应 DTO
│   │   └── *Controller.java
│   ├── form/                  # 文件上传表单
│   └── controller/            # 非 API 控制器
├── security/                  # 认证、权限
├── config/                    # 配置类
└── exception/                 # 自定义异常
```

### 3. 实体规范

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StandardDocument {
    private Long id;
    private String title;
    private String status = "DRAFT";
    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**约定**:
- 使用 Lombok `@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`
- 主键: `Long id`
- 时间: `LocalDateTime`
- 布尔: `boolean`（不用包装类）
- 默认值: 字段声明时设置
- 无 JPA 注解，纯 POJO

### 4. Repository 规范

```java
@Mapper
public interface StandardDocumentMapper {
    StandardDocument findById(@Param("id") Long id);
    List<StandardDocument> findAll();
    int insert(StandardDocument doc);
    int update(StandardDocument doc);
    int deleteById(@Param("id") Long id);
}
```

**约定**:
- 接口名: `{Entity}Mapper`
- 方法名: Spring Data 风格（`findById`、`findByStatus`）
- 参数: `@Param` 注解
- SQL: 写在 XML 文件中（非注解）
- 驼峰转换: `map-underscore-to-camel-case: true`

### 5. Service 规范

```java
@Service
@Slf4j
public class StandardDocumentService {
    private final StandardDocumentMapper documentMapper;

    public StandardDocumentService(StandardDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Transactional
    public StandardDocument create(StandardDocumentRequest request) {
        // 业务校验
        if (documentMapper.existsByTitle(request.getTitle())) {
            throw new BusinessException(ErrorCode.DUPLICATE_TITLE);
        }

        // 业务逻辑
        StandardDocument doc = new StandardDocument();
        doc.setTitle(request.getTitle());
        doc.setCreatedAt(LocalDateTime.now());
        documentMapper.insert(doc);

        log.info("文档创建成功 id={}", doc.getId());
        return doc;
    }
}
```

**约定**:
- 注解: `@Service` + `@Slf4j`
- 依赖注入: 构造器注入（无 `@Autowired`）
- 事务: 写操作加 `@Transactional`
- 异常: 抛出 `BusinessException`（统一业务异常）
- 时间戳: 手动设置 `LocalDateTime.now()`

### 6. Controller 规范

```java
@RestController
@RequestMapping("/api/admin/standard-documents")
public class AdminStandardDocumentApiController {
    private final StandardDocumentService service;

    public AdminStandardDocumentApiController(StandardDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<StandardDocumentResponse> list(@RequestParam(defaultValue = "") String keyword) {
        return service.list(keyword);
    }

    @PostMapping
    public StandardDocumentResponse create(@Valid @RequestBody StandardDocumentRequest request) {
        return StandardDocumentResponse.from(service.create(request));
    }
}
```

**URL 约定**:
- 管理接口: `/api/admin/{resource}`
- 公开接口: `/api/public/{resource}`
- 同一资源分两个 Controller（Admin + Public）

### 7. DTO 规范

```java
// 请求 DTO（手动 getter/setter + 校验注解）
public class StandardDocumentRequest {
    @NotBlank(message = ErrorCode.REQUIRED_TITLE)
    @Size(max = 160, message = ErrorCode.TITLE_TOO_LONG)
    private String title;

    // getter/setter...
}

// 响应 DTO（静态工厂方法）
public class StandardDocumentResponse {
    private Long id;
    private String title;

    public static StandardDocumentResponse from(StandardDocument doc) {
        StandardDocumentResponse response = new StandardDocumentResponse();
        response.setId(doc.getId());
        response.setTitle(doc.getTitle());
        return response;
    }
}
```

**命名约定**:
- 请求: `*Request`
- 响应: `*Response`
- 表单: `*Form`（文件上传）

---

## 三、异常处理规范

### 1. 统一异常类

```java
// 业务异常（用户可理解的错误）
public class BusinessException extends RuntimeException {
    private final String code;
    private final String message;

    public BusinessException(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage() + ": " + detail;
    }
}

// 资源未找到异常
public class NotFoundException extends BusinessException {
    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}

// 权限不足异常
public class ForbiddenException extends BusinessException {
    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

### 2. 错误码常量

```java
// constant/ErrorCode.java
public final class ErrorCode {
    private ErrorCode() {}

    // 通用错误
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    public static final String PARAM_INVALID = "PARAM_INVALID";

    // 认证授权
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";

    // 资源操作
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String STATUS_CONFLICT = "STATUS_CONFLICT";

    // 文件操作
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String FILE_UPLOAD_FAILED = "FILE_UPLOAD_FAILED";

    // 具体业务（示例）
    public static final String DOCUMENT_NOT_FOUND = "DOCUMENT_NOT_FOUND";
    public static final String DOCUMENT_PUBLISHED = "DOCUMENT_PUBLISHED";
    public static final String TITLE_DUPLICATE = "TITLE_DUPLICATE";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String PASSWORD_INVALID = "PASSWORD_INVALID";
}
```

### 3. 错误消息常量

```java
// constant/ErrorMessages.java
public final class ErrorMessages {
    private ErrorMessages() {}

    // 通用
    public static final String UNKNOWN_ERROR = "系统异常，请稍后重试";
    public static final String PARAM_INVALID = "参数校验失败";

    // 认证授权
    public static final String UNAUTHORIZED = "未认证，请先登录";
    public static final String FORBIDDEN = "权限不足";
    public static final String TOKEN_EXPIRED = "登录已过期，请重新登录";

    // 资源操作
    public static final String NOT_FOUND = "资源不存在";
    public static final String DUPLICATE = "数据已存在";
    public static final String STATUS_CONFLICT = "当前状态不允许此操作";

    // 文件操作
    public static final String FILE_TOO_LARGE = "上传文件超过大小限制";
    public static final String FILE_UPLOAD_FAILED = "文件上传失败";

    // 具体业务
    public static final String DOCUMENT_NOT_FOUND = "文档不存在";
    public static final String DOCUMENT_PUBLISHED = "已发布文档不能编辑，请先下架";
    public static final String TITLE_DUPLICATE = "标题已存在";
    public static final String USER_NOT_FOUND = "用户不存在";
    public static final String PASSWORD_INVALID = "密码错误";
}
```

### 4. 统一异常处理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 业务异常 → 400
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        log.warn("业务异常 code={} message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.badRequest().body(new ApiError(400, ex.getCode(), ex.getMessage()));
    }

    // 资源未找到 → 404
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        log.warn("资源未找到: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, ex.getCode(), ex.getMessage()));
    }

    // 权限不足 → 403
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        log.warn("权限不足: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(403, ex.getCode(), ex.getMessage()));
    }

    // 参数校验 → 400
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiError> handleValidation(Exception ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        // 提取字段错误...
        log.warn("参数校验失败 fields={}", fieldErrors);
        return ResponseEntity.badRequest()
                .body(new ApiError(400, ErrorCode.PARAM_INVALID, ErrorMessages.PARAM_INVALID, fieldErrors));
    }

    // 文件过大 → 413
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("文件过大: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError(413, ErrorCode.FILE_TOO_LARGE, ErrorMessages.FILE_TOO_LARGE));
    }

    // 未知异常 → 500（不暴露技术细节）
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(Exception ex) {
        log.error("系统异常", ex);  // 后端记录完整堆栈
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(500, ErrorCode.UNKNOWN_ERROR, ErrorMessages.UNKNOWN_ERROR));
    }
}
```

### 5. ApiError 响应格式

```java
public class ApiError {
    private String timestamp = LocalDateTime.now().toString();
    private int status;
    private String code;      // 错误码（前端可据此做逻辑判断）
    private String message;   // 用户友好的中文描述
    private Map<String, String> fieldErrors;  // 字段级错误（可选）
}
```

**前端收到的响应示例**:
```json
{
  "timestamp": "2026-06-06T10:30:00",
  "status": 400,
  "code": "TITLE_DUPLICATE",
  "message": "标题已存在",
  "fieldErrors": null
}
```

**禁止返回到前端的内容**:
- ❌ Java 异常类名（如 `java.lang.IllegalArgumentException`）
- ❌ 堆栈信息
- ❌ SQL 语句
- ❌ 数据库表名、字段名
- ❌ 内部 IP、端口等敏感信息

### 6. 401/403 处理

```java
// 自定义 401 入口点
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJson(
            new ApiError(401, ErrorCode.UNAUTHORIZED, ErrorMessages.UNAUTHORIZED)));
    }
}

// 自定义 403 处理器
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJson(
            new ApiError(403, ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN)));
    }
}
```

---

## 四、日志规范

### 1. Logger 声明

```java
// 推荐：Lombok @Slf4j
@Slf4j
public class UserService { }

// 备选：手动声明（统一使用小写 log）
private static final Logger log = LoggerFactory.getLogger(UserService.class);
```

### 2. 日志级别

| 级别 | 用途 | 示例 |
|------|------|------|
| `DEBUG` | 调试信息，生产关闭 | Token 创建、缓存命中 |
| `INFO` | 业务操作记录 | 资源创建/更新/删除 |
| `WARN` | 可恢复的异常 | 业务校验失败、导入单条失败 |
| `ERROR` | 需要关注的异常 | 数据库连接失败、外部服务调用失败 |

### 3. 日志格式

```java
// ✅ 正确：参数化日志
log.info("用户登录成功 username={}", username);
log.warn("导入失败 file={} reason={}", file, ex.getMessage());
log.error("数据库连接失败", ex);  // 最后一个参数自动打印堆栈

// ❌ 错误：字符串拼接
log.info("用户登录成功 username=" + username);

// ❌ 错误：不带堆栈
log.error("错误: " + ex.getMessage());
```

### 4. 敏感信息

```java
// ❌ 禁止记录
log.info("用户密码: {}", password);
log.info("Token: {}", token);

// ✅ 允许记录
log.info("用户登录 username={}", username);
log.info("Token 创建 user={} expiresAt={}", username, expiresAt);
```

---

## 五、常量管理规范

### 1. 常量分类

```
constant/
├── ErrorCode.java           # 错误码
├── ErrorMessages.java       # 错误消息（中文）
├── StatusConstants.java     # 状态常量
└── BusinessConstants.java   # 业务常量
```

### 2. 状态常量

```java
public final class StatusConstants {
    private StatusConstants() {}

    // 文档状态
    public static final String DRAFT = "DRAFT";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String MODIFYING = "MODIFYING";

    // 审核状态
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    // 包状态
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
}
```

### 3. 业务常量

```java
public final class BusinessConstants {
    private BusinessConstants() {}

    // 分页
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // 文件
    public static final long MAX_FILE_SIZE = 2048 * 1024 * 1024;  // 2GB
    public static final String[] ALLOWED_FILE_EXTENSIONS = {".zip", ".tar.gz", ".rpm"};

    // Token
    public static final long TOKEN_EXPIRE_HOURS = 2;
    public static final String TOKEN_PREFIX = "Bearer ";
}
```

### 4. 使用方式

```java
// Service 中
if (StatusConstants.PUBLISHED.equals(doc.getStatus())) {
    throw new BusinessException(ErrorCode.DOCUMENT_PUBLISHED, ErrorMessages.DOCUMENT_PUBLISHED);
}

// Controller 中
@RequestParam(defaultValue = "0") int page,
@RequestParam(defaultValue = "20") int size
```

---

## 六、安全规范

### 1. 认证流程

```
前端登录 → HTTP Basic Auth → 后端验证 → 返回 Token
前端请求 → Bearer Token → TokenAuthenticationFilter → SecurityContext
```

### 2. 权限控制

```java
// URL 级别（SecurityConfig）
.requestMatchers("/api/admin/users/**").hasRole("SYS_ADMIN")
.requestMatchers("/api/admin/**").hasAnyRole("SYS_ADMIN", "...")

// 方法级别（PermissionService）
if (!permissionService.canManageCategory(authentication, category)) {
    throw new ForbiddenException(ErrorCode.FORBIDDEN);
}
```

### 3. 密码处理

- 前端: SHA-256 哈希
- 后端: BCrypt 加密存储
- 传输: HTTPS + Basic Auth（Base64）

---

## 七、前端错误处理规范

### 1. API 错误处理

```javascript
try {
  const data = await request('/api/admin/documents', { method: 'POST', body })
  notify('保存成功', 'success')
} catch (error) {
  // error.message 是后端返回的中文描述
  notify(error.message || '操作失败', 'error')

  // 可选：根据 error.code 做特定处理
  if (error.code === 'UNAUTHORIZED') {
    // 跳转登录
  }
}
```

### 2. 错误提示

```javascript
// 使用 notify 显示后端返回的中文消息
notify(error.message, 'error')

// 禁止显示技术性错误
notify(error.stack, 'error')  // ❌ 错误
notify(error.toString(), 'error')  // ❌ 错误
```

---

## 八、代码风格

### 1. 命名规范

| 类型 | 前端 | 后端 |
|------|------|------|
| 变量/函数 | camelCase | camelCase |
| 组件/类 | PascalCase | PascalCase |
| 常量 | UPPER_SNAKE_CASE | UPPER_SNAKE_CASE |
| CSS 类 | kebab-case | - |
| 包名 | - | 小写 |

### 2. 语言规范

- **UI 文本**: 中文
- **代码注释**: 中文
- **变量/函数名**: 英文
- **Git 提交**: 中文

### 3. 缩进

- **前端**: 2 空格
- **后端**: 4 空格

---

## 九、检查清单

### 新增功能

- [ ] 实体使用 Lombok 注解
- [ ] Service 使用构造器注入
- [ ] 写操作加 `@Transactional`
- [ ] 异常使用 `BusinessException` + `ErrorCode`
- [ ] 日志使用参数化格式
- [ ] 常量定义在 `constant/` 包
- [ ] 响应 DTO 使用静态工厂方法
- [ ] 前端组件使用设计令牌
- [ ] API 错误显示中文描述

### 代码审查

- [ ] 无硬编码魔法值
- [ ] 无堆栈信息返回前端
- [ ] 无敏感信息日志
- [ ] 异常处理完整
- [ ] 常量引用正确
