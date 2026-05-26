package com.middleware.manager.web.api;

import com.middleware.manager.config.ModuleProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicConfigController {
    private final ModuleProperties moduleProperties;

    public PublicConfigController(ModuleProperties moduleProperties) {
        this.moduleProperties = moduleProperties;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "knowledgeEnabled", moduleProperties.isKnowledgeEnabled(),
                "diagnosticsEnabled", moduleProperties.isDiagnosticsEnabled()
        );
    }
}
