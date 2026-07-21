package com.middleware.manager.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SoftwareTypeResolveRequest {
    @NotBlank
    @Size(max = 100)
    private String categoryName;

    @NotBlank
    @Size(max = 200)
    private String softwareTypeName;
}
