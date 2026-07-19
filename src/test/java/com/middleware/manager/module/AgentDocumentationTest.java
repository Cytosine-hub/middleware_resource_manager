package com.middleware.manager.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * TC-08：项目 agent.md 明确记录岗位模块编码独立、通用能力复用、UI 风格一致三项要求。
 */
class AgentDocumentationTest {

    private String readAgentDoc() throws IOException {
        Path path = Paths.get("agent.md");
        assertThat(Files.exists(path))
                .as("项目根目录应存在 agent.md 开发说明书")
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void TC_08_agent文档记录模块独立_代码复用_UI一致要求() throws IOException {
        String doc = readAgentDoc();
        // 岗位模块编码独立
        assertThat(doc).contains("岗位模块");
        assertThat(doc).contains("编码独立");
        // 通用能力代码复用
        assertThat(doc).contains("代码复用");
        // UI 风格一致
        assertThat(doc).contains("UI");
        assertThat(doc).contains("一致");
        // 独立后端或门户后端接入
        assertThat(doc).contains("门户后端");
    }
}
