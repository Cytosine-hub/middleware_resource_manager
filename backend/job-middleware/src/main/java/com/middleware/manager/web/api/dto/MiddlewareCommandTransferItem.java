package com.middleware.manager.web.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiddlewareCommandTransferItem {
    @NotBlank
    @Size(max = 100)
    @JsonAlias("softwareTypeCategory")
    private String categoryName;

    @NotBlank
    @Size(max = 200)
    private String softwareTypeName;

    @NotBlank
    private String commandFormat;

    @NotBlank
    @Size(max = 500)
    private String briefDescription;
    private String detailedDescription;

    @Valid
    private List<@NotBlank @Size(max = 80) String> categories = new ArrayList<>();

    private int sortOrder;
}
