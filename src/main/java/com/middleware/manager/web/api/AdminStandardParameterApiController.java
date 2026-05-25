package com.middleware.manager.web.api;

import com.middleware.manager.service.StandardParameterService;
import com.middleware.manager.web.api.dto.StandardParameterRequest;
import com.middleware.manager.web.api.dto.StandardParameterResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/standard-parameters")
public class AdminStandardParameterApiController {
    private final StandardParameterService service;

    public AdminStandardParameterApiController(StandardParameterService service) {
        this.service = service;
    }

    @GetMapping
    public List<StandardParameterResponse> list(@RequestParam(defaultValue = "") String keyword,
                                                @RequestParam(defaultValue = "") String category,
                                                @RequestParam(required = false) Boolean active,
                                                @RequestParam(required = false) Long standardDocumentId,
                                                @RequestParam(required = false) Long parameterStandardId) {
        return service.list(keyword, category, active, standardDocumentId, parameterStandardId).stream()
                .map(StandardParameterResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping
    public StandardParameterResponse create(@Valid @RequestBody StandardParameterRequest request) {
        return StandardParameterResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    public StandardParameterResponse update(@PathVariable Long id,
                                            @Valid @RequestBody StandardParameterRequest request) {
        return StandardParameterResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
