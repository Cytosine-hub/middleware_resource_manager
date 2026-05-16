package com.middleware.manager;

import com.middleware.manager.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class MiddlewareResourceManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiddlewareResourceManagerApplication.class, args);
    }
}
