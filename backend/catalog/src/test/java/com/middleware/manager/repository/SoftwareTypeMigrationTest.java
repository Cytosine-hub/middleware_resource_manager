package com.middleware.manager.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SoftwareTypeMigrationTest {

    @Test
    @DisplayName("TC-CATALOG-006 catalog 迁移按名幂等补齐七个中间件软件类型")
    void migrationUpsertsMiddlewareSoftwareTypesByName() throws IOException {
        String path = "db/migration/V20260721__upsert_middleware_software_types.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("迁移脚本应存在").isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("software_categories")
                    .contains("software_types")
                    .contains("中间件")
                    .contains("Redis", "Kafka", "Zookeeper", "RabbitMQ",
                            "RocketMQ", "Java容器", "Nacos")
                    .containsIgnoringCase("NOT EXISTS");
        }
    }
}
