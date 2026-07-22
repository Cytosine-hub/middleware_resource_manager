-- 常用命令模块全新安装 DDL。
-- software_type_id 是对 catalog.software_types 的逻辑关联，不创建跨服务物理外键。

CREATE TABLE IF NOT EXISTS middleware_commands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    software_type_id BIGINT NOT NULL COMMENT 'catalog 软件类型ID',
    name VARCHAR(500) NOT NULL COMMENT '兼容名称字段',
    command TEXT NOT NULL COMMENT '兼容命令字段',
    command_format TEXT NOT NULL COMMENT '命令格式',
    brief_description VARCHAR(500) COMMENT '简要说明',
    detailed_description TEXT COMMENT '详细说明',
    categories TEXT COMMENT '命令级分类标签JSON数组',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_software_type (software_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常用命令';
