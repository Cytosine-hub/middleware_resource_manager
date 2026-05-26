package com.middleware.manager.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @PostConstruct
    public void load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:skills/*.yaml");
            for (Resource resource : resources) {
                try {
                    Skill skill = yamlMapper.readValue(resource.getInputStream(), Skill.class);
                    skills.put(skill.getName(), skill);
                    log.info("[Skill] Loaded: {}", skill.getName());
                } catch (Exception e) {
                    log.warn("[Skill] Failed to load {}: {}", resource.getFilename(), e.getMessage());
                }
            }
            log.info("[Skill] Loaded {} skills", skills.size());
        } catch (IOException e) {
            log.info("[Skill] No skills found in classpath:skills/*.yaml");
        }
    }

    public Skill match(String input) {
        if (input == null || input.isEmpty()) return null;
        String upper = input.toUpperCase();
        return skills.values().stream()
                .filter(s -> s.getTrigger() != null && s.getTrigger().getKeywords() != null)
                .filter(s -> s.getTrigger().getKeywords().stream()
                        .anyMatch(kw -> upper.contains(kw.toUpperCase())))
                .findFirst().orElse(null);
    }

    public List<Skill> getAll() {
        return new ArrayList<>(skills.values());
    }

    public Skill get(String name) {
        return skills.get(name);
    }

    public void save(Skill skill) {
        skills.put(skill.getName(), skill);
        log.info("[Skill] Saved: {}", skill.getName());
    }

    public void delete(String name) {
        skills.remove(name);
        log.info("[Skill] Deleted: {}", name);
    }
}
