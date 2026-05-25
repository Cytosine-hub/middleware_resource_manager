package com.middleware.manager.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StandardParameterRequest {
    private Long standardDocumentId;

    private Long parameterStandardId;

    @NotBlank
    @Size(max = 80)
    private String code;

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    @Size(max = 500)
    private String value;

    @Size(max = 60)
    private String category;

    @Size(max = 500)
    private String description;

    private boolean active = true;

    private boolean deploymentStandard;

    public Long getStandardDocumentId() {
        return standardDocumentId;
    }

    public void setStandardDocumentId(Long standardDocumentId) {
        this.standardDocumentId = standardDocumentId;
    }

    public Long getParameterStandardId() {
        return parameterStandardId;
    }

    public void setParameterStandardId(Long parameterStandardId) {
        this.parameterStandardId = parameterStandardId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDeploymentStandard() {
        return deploymentStandard;
    }

    public void setDeploymentStandard(boolean deploymentStandard) {
        this.deploymentStandard = deploymentStandard;
    }
}
