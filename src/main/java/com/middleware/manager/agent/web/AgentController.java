package com.middleware.manager.agent.web;

import com.middleware.manager.agent.service.AgentService;
import com.middleware.manager.agent.skill.Skill;
import com.middleware.manager.agent.skill.SkillLoader;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController("opsAgentController")
@RequestMapping("/api/ops-agent")
public class AgentController {

    private final AgentService agentService;
    private final SkillLoader skillLoader;

    public AgentController(AgentService agentService, SkillLoader skillLoader) {
        this.agentService = agentService;
        this.skillLoader = skillLoader;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest req) {
        return agentService.chat(req.getMessage(), req.getContext());
    }

    @GetMapping("/tools")
    public List<Map<String, Object>> listTools() {
        return agentService.listTools();
    }

    @GetMapping("/skills")
    public List<Skill> listSkills() {
        return skillLoader.getAll();
    }

    @PostMapping("/skills")
    public Map<String, Object> saveSkill(@RequestBody Skill skill) {
        skillLoader.save(skill);
        return Map.of("status", "ok", "name", skill.getName());
    }

    @DeleteMapping("/skills/{name}")
    public Map<String, Object> deleteSkill(@PathVariable String name) {
        skillLoader.delete(name);
        return Map.of("status", "ok");
    }

    @PostMapping("/webhook/alert")
    public Map<String, Object> alertWebhook(@RequestBody AlertRequest alert) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("service", alert.getService());
        String message = "告警：" + alert.getDescription() + "，服务：" + alert.getService() + "，请排查";
        return agentService.chat(message, context);
    }

    public static class ChatRequest {
        private String message;
        private Map<String, String> context;
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Map<String, String> getContext() { return context; }
        public void setContext(Map<String, String> context) { this.context = context; }
    }

    public static class AlertRequest {
        private String service;
        private String description;
        private String severity;
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
    }
}
