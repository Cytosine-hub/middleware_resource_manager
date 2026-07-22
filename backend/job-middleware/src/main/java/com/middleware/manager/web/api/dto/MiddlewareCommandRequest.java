package com.middleware.manager.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MiddlewareCommandRequest {
    @NotNull
    private Long softwareTypeId;

    @NotBlank
    private String commandFormat;

    @NotBlank
    @Size(max = 500)
    private String briefDescription;
    private String detailedDescription;
    private String categories;
    private int sortOrder;
}
