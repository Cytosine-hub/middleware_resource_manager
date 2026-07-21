# 常用命令与软件类型/分类关联 + 数据迁移说明

## 目标模型

- `middleware_commands.software_type_id` 逻辑关联 catalog 拥有的 `software_types.id`。
- 类型的分类名来自 `software_types.category`，分类目录由 `software_categories` 管理。
- 命令自身的 `categories` 继续保存 JSON 标签数组，例如 `["查询","操作"]`。
- 旧表 `middleware_types` 和旧列 `middleware_type_id` 在迁移完成后删除。
- 不创建跨服务物理外键，也不在 middleware-service 中查询或写入 catalog 表。

middleware-service 通过 common-core 的 `SoftwareTypeLookup` 解析类型。默认 profile 直连
`${CORE_SERVICE_URL:http://127.0.0.1:8084}`；`cloud` profile 使用
`http://core-service` 和 Spring Cloud LoadBalancer。内部请求使用
`GATEWAY_SIGNING_SECRET` 生成 HMAC，两个服务必须配置相同且至少 32 UTF-8 字节的密钥。

## 数据库迁移

迁移前先备份数据库，并暂停常用命令写入。以下脚本当前由运维手工执行，不会由应用启动自动执行。

必须按顺序执行：

```bash
cd backend
mysql middleware_resource_manager \
  < catalog/src/main/resources/db/migration/V20260721__upsert_middleware_software_types.sql
mysql middleware_resource_manager \
  < job-middleware/src/main/resources/db/migration/V20260721__link_commands_to_software_types.sql
```

第一段脚本按名确保“中间件”分类存在，并补齐 Redis、Kafka、Zookeeper、RabbitMQ、
RocketMQ、Java容器和 Nacos。已有同名分类或类型不会被覆盖。

第二段脚本执行以下操作：

1. 按需增加可空的 `software_type_id`。
2. 通过 `middleware_types.name` 和 `software_types.name` 回填关联。
3. 检查是否仍有无法解析的命令；存在时主动报错并保留旧模型。
4. 将新列改为非空，创建索引，删除旧列和 `middleware_types`。
5. 修正 nginx 版本命令归类、Redis `config rewrite` 和 RabbitMQ 删除用户标题。

两个脚本都通过名称和 `information_schema` 判定当前状态，迁移成功后可再次执行。旧表删除不可原地回滚；需要回退时使用迁移前数据库备份，或用下述 JSON 导出重新导入。

迁移后检查：

```sql
SELECT COUNT(*) AS unresolved
FROM middleware_commands command_row
LEFT JOIN software_types software_type ON software_type.id = command_row.software_type_id
WHERE software_type.id IS NULL;

SELECT COUNT(*) AS legacy_tables
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'middleware_types';
```

两个结果都应为 `0`。

## 导出接口

系统管理员通过 Gateway 调用：

```http
GET /api/middleware-commands/export
Authorization: Bearer <token>
```

响应是 JSON 数组，不包含命令 ID 或软件类型 ID：

```json
[
  {
    "categoryName": "中间件",
    "softwareTypeName": "Redis",
    "commandFormat": "redis-cli -h $HOST -p $PORT INFO",
    "briefDescription": "查看服务器信息",
    "detailedDescription": "输出 Redis 服务器信息。",
    "categories": ["查询"],
    "sortOrder": 0
  }
]
```

类型信息由 catalog 批量解析；任何命令关联不到类型，或标签不是 JSON 数组时，导出会返回明确的业务错误。

## 导入接口

系统管理员将同格式数组提交到：

```http
POST /api/middleware-commands/import
Authorization: Bearer <token>
Content-Type: application/json
```

catalog 按 `categoryName + softwareTypeName` 解析类型；不存在时由 catalog 创建分类和类型。
middleware-service 再按 `software_type_id + commandFormat` 查找命令：不存在则新增，内容变化则更新，完全一致则跳过。

返回示例：

```json
{
  "total": 47,
  "created": 5,
  "updated": 2,
  "skipped": 40
}
```

单条失败时响应会指出数组中的条目序号和原因，例如“第 3 条：软件目录服务暂不可用”。命令写入在本地事务中回滚；catalog 已落地的缺失类型是幂等数据，可由后续重试复用。

## test 到 prod

1. 分别备份 test 和 prod 数据库，暂停两个环境的常用命令写入。
2. 两个环境都按上述顺序执行 catalog 和 middleware 迁移脚本。
3. 先部署或重启 core-service，再部署或重启 middleware-service，确认两者共享同一个 `GATEWAY_SIGNING_SECRET`。
4. 使用 test 的系统管理员 Token 导出无 ID 数据。
5. 使用 prod 的系统管理员 Token 导入同一个文件。
6. 再导入一次；第二次应全部进入 `skipped`，且 `created`、`updated` 都为 `0`。

示例命令：

```bash
curl -fsS \
  -H "Authorization: Bearer ${TEST_ADMIN_TOKEN}" \
  "${TEST_GATEWAY_URL}/api/middleware-commands/export" \
  -o /tmp/middleware-commands.json

curl -fsS \
  -H "Authorization: Bearer ${PROD_ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  --data-binary @/tmp/middleware-commands.json \
  "${PROD_GATEWAY_URL}/api/middleware-commands/import"
```

Token 和服务地址仅通过环境变量传入，仓库不提供真实默认值。仓库内的
`backend/job-middleware/src/main/resources/commands/commands.json` 是无 ID 的初始化示例；旧的固定 ID SQL 种子和根目录旧格式导出已退役。
