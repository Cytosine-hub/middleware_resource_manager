---
allowed-tools: Bash(mvn *), Bash(npm *), Bash(pkill *), Bash(lsof *), Bash(kill *), Bash(sleep *), Bash(curl *), Bash(grep *), Bash(awk *), Bash(cd *)
description: 重启前后端服务
---

# 重启项目服务

重启前后端开发服务并验证可用性。

## 当前状态

- 项目根目录: /Users/zhushihao/Projects/middleware_resource_manager
- 当前日期: !`date +%Y-%m-%d`

## 执行步骤

### 第一步：编译后端

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
mvn clean package -DskipTests -q
```

如果编译失败，输出错误信息并停止。

### 第二步：停止旧进程

```bash
lsof -ti:8080 | xargs kill -9 2>/dev/null
pkill -f "middleware_resource_manager" 2>/dev/null
pkill -f "vite" 2>/dev/null
sleep 2
```

### 第三步：启动后端

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager
nohup mvn spring-boot:run > /tmp/app.log 2>&1 &
```

等待启动完成（约 15 秒），检查日志：

```bash
sleep 15
grep "Started MiddlewareResourceManagerApplication" /tmp/app.log
```

如果启动失败，输出错误：

```bash
grep -A10 "APPLICATION FAILED" /tmp/app.log
```

### 第四步：启动前端

```bash
cd /Users/zhushihao/Projects/middleware_resource_manager/frontend
nohup npm run dev > /tmp/frontend.log 2>&1 &
```

等待启动完成：

```bash
sleep 5
grep "ready\|Local" /tmp/frontend.log
```

### 第五步：验证服务

```bash
curl -s -o /dev/null -w "后端: %{http_code}\n" http://localhost:8080/api/public/config
curl -s -o /dev/null -w "前端: %{http_code}\n" http://localhost:5173/
```

输出启动结果摘要：版本号、端口、状态。
