package com.middleware.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("门户流水线联调验证文档")
class PortalPilotDocTest {

    private static final Path PORTAL_PILOT = Path.of("PORTAL_PILOT.md");

    @Test
    @DisplayName("TC-PORTAL-001 PORTAL_PILOT.md 存在且含指定文案")
    void portalPilotExistsWithRequiredContent() throws IOException {
        assertTrue(Files.exists(PORTAL_PILOT), "仓库根目录应存在 PORTAL_PILOT.md");

        String content = Files.readString(PORTAL_PILOT, StandardCharsets.UTF_8);
        assertTrue(content.contains("# 门户流水线联调验证"),
                "应包含一级标题「# 门户流水线联调验证」");
        assertTrue(content.contains("本文件由集成中心需求交付门户的 GitLab 流水线自动生成，用于端到端联调验证。"),
                "应包含指定说明文案");
        assertTrue(content.contains("验证项：Issue → 本地开发 → push → MR → 自动审查 → 合并"),
                "末尾应包含验证项说明行");
    }
}
