package com.middleware.manager.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.web.api.dto.MiddlewareCommandTransferItem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MiddlewareCommandMigrationTest {

    @Test
    @DisplayName("TC-COMMAND-002 命令迁移按类型名回填并移除旧字段和旧类型表")
    void commandMigrationBackfillsByNameAndDropsLegacyModel() throws IOException {
        String sql = resource("db/migration/V20260721__link_commands_to_software_types.sql");

        assertThat(sql).contains("software_type_id")
                .contains("middleware_type_id")
                .contains("middleware_types")
                .contains("software_types")
                .containsIgnoringCase("DROP TABLE")
                .containsIgnoringCase("information_schema")
                .contains("nginx -v")
                .contains("config rewrite")
                .contains("SET command = REPLACE")
                .contains("SET command_row.name = '【删除用户】'")
                .contains("【删除用户】");
    }

    @Test
    @DisplayName("TC-COMMAND-005 无 ID 命令种子使用 catalog 类型名且已修正已知错误")
    void commandSeedUsesCatalogNamesAndCorrectedContent() throws IOException {
        List<MiddlewareCommandTransferItem> items = new ObjectMapper().readValue(
                resource("commands/commands.json"), new TypeReference<>() { });

        assertThat(items).hasSize(47)
                .noneMatch(item -> item.getCommandFormat().contains("rewrite rewrite"))
                .noneMatch(item -> "Docker".equals(item.getSoftwareTypeName()));
        assertThat(items).anySatisfy(item -> {
            assertThat(item.getSoftwareTypeName()).isEqualTo("Java容器");
            assertThat(item.getCommandFormat()).isEqualTo("docker ps");
        });
        assertThat(items).anySatisfy(item -> {
            assertThat(item.getSoftwareTypeName()).isEqualTo("nginx");
            assertThat(item.getCommandFormat()).isEqualTo("nginx -v");
        });
        assertThat(items).anySatisfy(item -> {
            assertThat(item.getSoftwareTypeName()).isEqualTo("RabbitMQ");
            assertThat(item.getCommandFormat()).isEqualTo("rabbitmqctl delete_user userName");
            assertThat(item.getBriefDescription()).isEqualTo("【删除用户】");
        });
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("迁移脚本应存在: %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
