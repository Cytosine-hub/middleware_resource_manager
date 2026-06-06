package com.middleware.manager.web.api;

import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.service.StandardDocumentService;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.web.api.dto.PublicStandardDocumentResponse;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.web.api.dto.StandardDocumentResponse;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import org.springframework.web.bind.annotation.PathVariable;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import org.springframework.web.bind.annotation.RequestMapping;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import org.springframework.web.bind.annotation.RestController;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import org.springframework.web.server.ResponseStatusException;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;

import java.util.List;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import java.util.stream.Collectors;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;

@RestController
@RequestMapping("/api/public/standards")
public class PublicStandardDocumentApiController {
    private final StandardDocumentService service;

    public PublicStandardDocumentApiController(StandardDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<PublicStandardDocumentResponse> list() {
        return service.listPublishedStandards().stream()
                .map(standard -> PublicStandardDocumentResponse.from(
                        standard,
                        null,
                        service.listPublishedRelatedDocuments(standard.getId()).stream()
                                .map(document -> StandardDocumentResponse.from(document, null))
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    @GetMapping("/all")
    public List<PublicStandardDocumentResponse> listAllPublished() {
        return service.listAllPublic().stream()
                .map(doc -> PublicStandardDocumentResponse.from(doc, null, java.util.Collections.emptyList()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public PublicStandardDocumentResponse detail(@PathVariable Long id) {
        try {
            StandardDocument document = service.get(id);
            if (!"PUBLISHED".equals(document.getStatus()) && !"MODIFYING".equals(document.getStatus())) {
                throw new NotFoundException(ErrorCode.DOCUMENT_NOT_FOUND, ErrorMessages.DOCUMENT_NOT_FOUND);
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
