# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Backend (Spring Boot 2.7.18, Java 8)
mvn clean package -DskipTests        # compile
mvn spring-boot:run                  # run (starts on :8080)

# Frontend (Vue 3 + Vite)
cd frontend && npm install && npm run dev   # starts on :5173, proxies /api and /files to :8080
```

Database: MySQL 8.0 at `127.0.0.1:3306/middleware_resource_manager`, user `root`. Credentials in `~/.my.cnf`. Hibernate `ddl-auto: update` creates/alters tables automatically.

## Architecture

**Backend** — standard Spring Boot layered architecture:
- `domain/` — JPA entities mapped to MySQL tables
- `repository/` — Spring Data JPA interfaces
- `service/` — business logic
- `web/api/` — REST controllers (`/api/admin/**`, `/api/public/**`, `/api/auth/**`, `/api/forum/**`)
- `web/controller/` — Spring MVC view controllers (Thymeleaf SSR pages like `/login`, `/admin/releases`)
- `config/` — SecurityConfig, StorageProperties, WarmupRunner, AccessLogFilter
- `security/` — Role enum + PermissionService
- `web/api/dto/` — request/response DTOs, `web/form/` — form backing objects

**Frontend** — single-file Vue 3 SPA (no router library):
- `App.vue` — the entire application: hash-based routing, all admin CRUD, public download/standards pages, forum
- `components/` — DocumentEditor, ForumPostList/Detail/Editor, Pagination, MarkdownHelp
- `api.js` — thin fetch wrapper with HTTP Basic Auth (SHA-256 hashed password stored in sessionStorage)

## Auth & RBAC

HTTP Basic Auth. Password stored as `{bcrypt}...` in `admin_accounts` table. Frontend SHA-256 hashes the password before base64-encoding for the Authorization header.

**8 roles** (from `security/Role.java`):

| Role | Authority | Managed Category | Capabilities |
|------|-----------|-----------------|--------------|
| 系统管理员 | `ROLE_SYS_ADMIN` | * (all) | Full access, user management |
| 中间件管理岗 | `ROLE_MIDDLEWARE_MGR` | 中间件 | Admin CRUD for middleware category |
| 数据库管理岗 | `ROLE_DATABASE_MGR` | 数据库 | Admin CRUD for database category |
| 主机管理岗 | `ROLE_HOST_MGR` | 主机 | Admin CRUD for host category |
| 网络管理岗 | `ROLE_NETWORK_MGR` | 网络 | Admin CRUD for network category |
| 网络安全岗 | `ROLE_SECURITY_MGR` | 安全 | Admin CRUD for security category |
| 开发经理 | `ROLE_DEV_MGR` | — | Read-only, redirected to home |
| 运维经理 | `ROLE_OPS_MGR` | — | Read-only, redirected to home |

Managers are scoped to their category — they can only manage releases/types/standards within their category. `PermissionService.getManagedCategory()` enforces this.

## Key domain model relationships

- `SoftwareCategory` — simple name list (中间件, 数据库, 主机, 安全, 网络...)
- `SoftwareType` — belongs to a category, has name; unique on (category, name)
- `ReleaseAsset` — a published download file; links to SoftwareType; stored in `./storage/<middlewareName>/`
- `StandardDocument` — STANDARD, MANUAL, or ARTICLE; links to SoftwareType
- `StandardParameter` — key-value params attached to a StandardDocument (e.g., `{{JDK_VERSION}}`)
- `ForumPost` / `ForumComment` / `ForumTag` — simple forum
- `AdminAccount` — users with role, password hash, display name

## API patterns

- Admin APIs (`/api/admin/**`) require role-based access; see SecurityConfig for exact role mappings
- Public APIs (`/api/public/**`) are unauthenticated
- File downloads require login (`/files/**` → authenticated)
- Frontend fetches through Vite proxy, so API paths are relative (`/api/...`)

## Configuration

`src/main/resources/application.yml` — single config file. DB connection defaults can be overridden via env vars: `APP_DB_HOST`, `APP_DB_PORT`, `APP_DB_NAME`, `APP_DB_USERNAME`, `APP_DB_PASSWORD`.
