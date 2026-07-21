package com.middleware.manager.web.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MiddlewareCommandImportResult {
    private int total;
    private int created;
    private int updated;
    private int skipped;
}
