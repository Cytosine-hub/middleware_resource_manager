package com.middleware.manager.web.api;

import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.service.StandardDocumentService;
import com.middleware.manager.web.api.dto.PublicStandardDocumentResponse;
import com.middleware.manager.web.api.dto.StandardDocumentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/standards")
public class PublicStandardDocumentApiController {
    private final StandardDocumentService service;

    public PublicStandardDocumentApiController(StandardDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<PublicStandardDocumentResponse> list(@RequestParam(defaultValue = "") String category) {
        return service.listPublishedStandards(category).stream()
                .map(standard -> PublicStandardDocumentResponse.from(
                        standard,
                        null,
                        service.listPublishedRelatedDocuments(standard.getId()).stream()
                                .map(document -> StandardDocumentResponse.from(document, null))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    @GetMapping("/all")
    public List<PublicStandardDocumentResponse> listAllPublished(@RequestParam(defaultValue = "") String category) {
        return service.listAllPublic(category).stream()
                .map(doc -> PublicStandardDocumentResponse.from(doc, null, java.util.Collections.emptyList()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public PublicStandardDocumentResponse detail(@PathVariable Long id) {
        try {
            StandardDocument document = service.get(id);
            if (!"PUBLISHED".equals(document.getStatus()) && !"MODIFYING".equals(document.getStatus())) {
                throw new IllegalArgumentException("文档不存在或未发布");
            }
            if ("MODIFYING".equals(document.getStatus()) && document.getPreviousContent() != null) {
                document.setContent(document.getPreviousContent());
                document.setRenderedContent(null);
            }
            Long standardId = "STANDARD".equals(document.getDocumentType())
                    ? document.getId()
                    : document.getRelatedStandardDocumentId();
            List<StandardDocumentResponse> relatedDocuments = standardId == null
                    ? java.util.Collections.emptyList()
                    : service.listPublishedRelatedDocuments(standardId).stream()
                            .map(related -> StandardDocumentResponse.from(related, null))
                            .collect(Collectors.toList());
            return PublicStandardDocumentResponse.from(document, service.render(document), relatedDocuments);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}
