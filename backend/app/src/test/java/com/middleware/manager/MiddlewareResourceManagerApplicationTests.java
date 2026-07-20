package com.middleware.manager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class MiddlewareResourceManagerApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("TC-APP-001 默认 profile 加载上下文且不启用 Nacos")
    void contextLoads() {
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class))
                .isFalse();
    }
}
