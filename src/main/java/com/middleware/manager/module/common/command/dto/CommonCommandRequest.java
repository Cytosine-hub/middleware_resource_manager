package com.middleware.manager.module.common.command.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommonCommandRequest {

    @NotBlank(message = "岗位分类不能为空")
    @Size(max = 40)
    private String category;

    @NotBlank(message = "命令标题不能为空")
    @Size(max = 120)
    private String title;

    @NotBlank(message = "命令内容不能为空")
    private String command;

    @Size(max = 500)
    private String description;

    @Size(max = 60)
    private String tag;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}
