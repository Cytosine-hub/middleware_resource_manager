package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.MiddlewareCommand;
import lombok.Data;

@Data
public class MiddlewareCommandResponse {
    private Long id;
    private Long softwareTypeId;
    private String name;
    private String command;
    private String commandFormat;
    private String briefDescription;
    private String detailedDescription;
    private String categories;
    private int sortOrder;

    public static MiddlewareCommandResponse from(MiddlewareCommand command) {
        MiddlewareCommandResponse response = new MiddlewareCommandResponse();
        response.setId(command.getId());
        response.setSoftwareTypeId(command.getSoftwareTypeId());
        response.setName(command.getName());
        response.setCommand(command.getCommand());
        response.setCommandFormat(command.getCommandFormat());
        response.setBriefDescription(command.getBriefDescription());
        response.setDetailedDescription(command.getDetailedDescription());
        response.setCategories(command.getCategories());
        response.setSortOrder(command.getSortOrder());
        return response;
    }
}
