package com.middleware.manager.module.common.command.dto;

import com.middleware.manager.module.common.command.CommonCommand;
import java.time.LocalDateTime;

public class CommonCommandResponse {

    private Long id;
    private String category;
    private String title;
    private String command;
    private String description;
    private String tag;
    private LocalDateTime updatedAt;

    public static CommonCommandResponse from(CommonCommand entity) {
        CommonCommandResponse resp = new CommonCommandResponse();
        resp.id = entity.getId();
        resp.category = entity.getCategory();
        resp.title = entity.getTitle();
        resp.command = entity.getCommand();
        resp.description = entity.getDescription();
        resp.tag = entity.getTag();
        resp.updatedAt = entity.getUpdatedAt();
        return resp;
    }

    public Long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getCommand() {
        return command;
    }

    public String getDescription() {
        return description;
    }

    public String getTag() {
        return tag;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
