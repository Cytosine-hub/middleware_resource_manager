package com.middleware.manager.knowledge.loader;

import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.repository.StandardDocumentRepository;
import org.springframework.stereotype.Component;

@Component
public class StandardDocumentLoader {

    private final StandardDocumentRepository standardDocumentRepository;

    public StandardDocumentLoader(StandardDocumentRepository standardDocumentRepository) {
        this.standardDocumentRepository = standardDocumentRepository;
    }

    public String loadFromStandardDocument(Long docId) {
        StandardDocument doc = standardDocumentRepository.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("StandardDocument not found: " + docId));
        return doc.getContent();
    }

    public StandardDocument getStandardDocument(Long docId) {
        return standardDocumentRepository.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("StandardDocument not found: " + docId));
    }
}
