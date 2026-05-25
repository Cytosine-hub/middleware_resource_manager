# Vue 前端 Nginx 部署手册

本文档用于将 `frontend` 目录下的 Vue 前端部署到 Nginx，并通过 Nginx 反向代理 Spring Boot 后端接口。

## 1. 部署结构

推荐结构：

```text
/opt/middleware-resource-manager/
  backend/
    middleware-resource-manager-0.0.1-SNAPSHOT.jar
  frontend/
    dist/
      index.html
      assets/
      favicon.svg
```

端口规划：

- Nginx：`80`
- Spring Boot 后端：`8080`
- MySQL：`3306`

## 2. 构建前端

在项目根目录执行：

```powershell
cd .\frontend
npm.cmd install
npm.cmd run build
```

构建产物在：

```text
frontend/dist
```

将 `frontend/dist` 整个目录上传到服务器，例如：

```text
/opt/middleware-resource-manager/frontend/dist
```

## 3. 启动后端

确保后端已在服务器启动，并监听 `8080`。

示例：

```bash
cd /opt/middleware-resource-manager/backend
java -jar middleware-resource-manager-0.0.1-SNAPSHOT.jar
```

验证后端：

```bash
curl http://127.0.0.1:8080/api/public/releases
```

返回 JSON 即表示后端正常。

## 4. Nginx 配置

新建配置文件：

```bash
sudo vi /etc/nginx/conf.d/middleware-resource-manager.conf
```

写入：

```nginx
server {
    listen 80;
    server_name _;

    root /opt/middleware-resource-manager/frontend/dist;
    index index.html;

    client_max_body_size 2048m;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;
    }

    location /files/ {
        proxy_pass http://127.0.0.1:8080/files/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|svg|ico|woff2?)$ {
        expires 30d;
        add_header Cache-Control "public";
        try_files $uri =404;
    }
}
```

说明：

- `/`：服务 Vue 单页应用。
- `try_files $uri $uri/ /index.html`：支持刷新页面后仍能进入 Vue 路由。
- `/api/`：代理后端接口。
- `/files/`：代理文件下载接口。
- `client_max_body_size 2048m`：支持大文件上传。

## 5. 检查并重载 Nginx

检查配置：

```bash
sudo nginx -t
```

重载：

```bash
sudo systemctl reload nginx
```

如果 Nginx 未启动：

```bash
sudo systemctl start nginx
sudo systemctl enable nginx
```

## 6. 访问验证

浏览器访问：

```text
http://服务器IP/
```

页面：

- 门户首页：`http://服务器IP/#/home`
- 下载中心：`http://服务器IP/#/downloads`
- 标准发布：`http://服务器IP/#/standards`
- 论坛：`http://服务器IP/#/forum`
- 知识库：`http://服务器IP/#/knowledge`
- 智能排查：`http://服务器IP/#/diagnostics`
- 管理后台：`http://服务器IP/#/admin`

接口验证：

```bash
curl http://服务器IP/api/public/releases
```

## 7. 更新前端

本地重新构建：

```powershell
cd .\frontend
npm.cmd run build
```

将新的 `dist` 覆盖服务器目录：

```text
/opt/middleware-resource-manager/frontend/dist
```

然后重载 Nginx：

```bash
sudo systemctl reload nginx
```

## 8. 常见问题

### 8.1 刷新页面 404

确认 Nginx 配置中有：

```nginx
try_files $uri $uri/ /index.html;
```

### 8.2 前端能打开，但接口失败

检查后端是否启动：

```bash
curl http://127.0.0.1:8080/api/public/releases
```

检查 Nginx 是否代理 `/api/`：

```bash
curl http://服务器IP/api/public/releases
```

### 8.3 上传大文件失败

确认 Nginx 配置包含：

```nginx
client_max_body_size 2048m;
```

同时确认后端 `application.yml` 中有：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2048MB
      max-request-size: 2048MB
```

### 8.4 下载文件失败

确认 Nginx 配置包含 `/files/` 代理：

```nginx
location /files/ {
    proxy_pass http://127.0.0.1:8080/files/;
}
```

### 8.5 登录后接口 401

确认浏览器访问的是 Nginx 地址，不要混用：

```text
前端：http://服务器IP/
后端代理：http://服务器IP/api/
```

不要让浏览器页面访问 `localhost:8080`，否则在远程机器上会指向用户自己的电脑。

