-- 前置条件：先执行 catalog/V20260721__upsert_middleware_software_types.sql。
-- 通过 information_schema 判定当前形态，使本脚本在迁移完成后可再次执行。

DROP PROCEDURE IF EXISTS migrate_middleware_command_types;
DELIMITER //
CREATE PROCEDURE migrate_middleware_command_types()
BEGIN
    DECLARE command_table_count INT DEFAULT 0;
    DECLARE software_column_count INT DEFAULT 0;
    DECLARE legacy_column_count INT DEFAULT 0;
    DECLARE legacy_table_count INT DEFAULT 0;
    DECLARE software_index_count INT DEFAULT 0;
    DECLARE unresolved_count INT DEFAULT 0;
    DECLARE command_column_count INT DEFAULT 0;
    DECLARE name_column_count INT DEFAULT 0;

    SELECT COUNT(*) INTO command_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'middleware_commands';

    IF command_table_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'middleware_commands 表不存在';
    END IF;

    SELECT COUNT(*) INTO software_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'middleware_commands'
      AND column_name = 'software_type_id';

    IF software_column_count = 0 THEN
        ALTER TABLE middleware_commands
            ADD COLUMN software_type_id BIGINT NULL COMMENT 'catalog 软件类型ID' AFTER id;
    END IF;

    SELECT COUNT(*) INTO legacy_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'middleware_commands'
      AND column_name = 'middleware_type_id';

    SELECT COUNT(*) INTO legacy_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'middleware_types';

    IF legacy_column_count > 0 AND legacy_table_count > 0 THEN
        UPDATE middleware_commands command_row
        INNER JOIN middleware_types legacy_type
                ON legacy_type.id = command_row.middleware_type_id
        INNER JOIN software_types catalog_type
                ON LOWER(TRIM(catalog_type.category)) = LOWER('中间件')
               AND LOWER(TRIM(catalog_type.name)) = LOWER(TRIM(legacy_type.name))
        SET command_row.software_type_id = catalog_type.id
        WHERE command_row.software_type_id IS NULL;
    END IF;

    SELECT COUNT(*) INTO unresolved_count
    FROM middleware_commands
    WHERE software_type_id IS NULL;

    IF unresolved_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '存在无法按类型名解析的中间件命令，迁移已停止';
    END IF;

    ALTER TABLE middleware_commands
        MODIFY COLUMN software_type_id BIGINT NOT NULL COMMENT 'catalog 软件类型ID';

    SELECT COUNT(*) INTO software_index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'middleware_commands'
      AND index_name = 'idx_software_type';

    IF software_index_count = 0 THEN
        ALTER TABLE middleware_commands ADD INDEX idx_software_type (software_type_id);
    END IF;

    IF legacy_column_count > 0 THEN
        ALTER TABLE middleware_commands DROP COLUMN middleware_type_id;
    END IF;

    DROP TABLE IF EXISTS middleware_types;

    -- 已知种子纠错：nginx 版本命令应归 nginx，而不是 Redis。
    UPDATE middleware_commands command_row
    INNER JOIN software_types redis_type
            ON LOWER(TRIM(redis_type.category)) = LOWER('中间件')
           AND LOWER(TRIM(redis_type.name)) = LOWER('Redis')
    INNER JOIN software_types nginx_type
            ON LOWER(TRIM(nginx_type.category)) = LOWER('中间件')
           AND LOWER(TRIM(nginx_type.name)) = LOWER('nginx')
    SET command_row.software_type_id = nginx_type.id
    WHERE command_row.software_type_id = redis_type.id
      AND LOWER(TRIM(command_row.command_format)) = 'nginx -v';

    UPDATE middleware_commands
    SET command_format = REPLACE(command_format, 'rewrite rewrite', 'config rewrite')
    WHERE command_format LIKE '%rewrite rewrite%';

    SELECT COUNT(*) INTO command_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'middleware_commands'
      AND column_name = 'command';

    IF command_column_count > 0 THEN
        UPDATE middleware_commands
        SET command = REPLACE(command, 'rewrite rewrite', 'config rewrite')
        WHERE command LIKE '%rewrite rewrite%';
    END IF;

    UPDATE middleware_commands command_row
    INNER JOIN software_types rabbit_type
            ON rabbit_type.id = command_row.software_type_id
           AND LOWER(TRIM(rabbit_type.category)) = LOWER('中间件')
           AND LOWER(TRIM(rabbit_type.name)) = LOWER('RabbitMQ')
    SET command_row.brief_description = '【删除用户】'
    WHERE command_row.command_format = 'rabbitmqctl delete_user userName'
      AND command_row.brief_description IN ('【删除用户(】', '【删除用户】');

    SELECT COUNT(*) INTO name_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'middleware_commands'
      AND column_name = 'name';

    IF name_column_count > 0 THEN
        UPDATE middleware_commands command_row
        INNER JOIN software_types rabbit_type
                ON rabbit_type.id = command_row.software_type_id
               AND LOWER(TRIM(rabbit_type.category)) = LOWER('中间件')
               AND LOWER(TRIM(rabbit_type.name)) = LOWER('RabbitMQ')
        SET command_row.name = '【删除用户】'
        WHERE command_row.command_format = 'rabbitmqctl delete_user userName'
          AND command_row.name IN ('【删除用户(】', '【删除用户】');
    END IF;
END//
DELIMITER ;

CALL migrate_middleware_command_types();
DROP PROCEDURE IF EXISTS migrate_middleware_command_types;
