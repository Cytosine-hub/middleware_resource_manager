package com.middleware.ai;

import com.middleware.manager.config.ModuleProperties;
import com.middleware.manager.config.StorageProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.middleware.ai", "com.middleware.manager"})
@EnableDiscoveryClient
@EnableAsync
@MapperScan({
        "com.middleware.manager.repository",
        "com.middleware.manager.wiki.repository",
        "com.middleware.manager.knowledge.agent",
        "com.middleware.manager.knowledge.repository",
        "com.middleware.manager.agent.repository"
})
@EnableConfigurationProperties({StorageProperties.class, ModuleProperties.class})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
