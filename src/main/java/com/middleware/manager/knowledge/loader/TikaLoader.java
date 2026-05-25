package com.middleware.manager.knowledge.loader;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class TikaLoader implements DocumentLoader {

    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(
            Arrays.asList(".pdf", ".doc", ".docx", ".xls", ".xlsx")
    );

    private final AutoDetectParser parser;

    public TikaLoader() {
        this.parser = new AutoDetectParser();
    }

    public TikaLoader(AutoDetectParser parser) {
        this.parser = parser;
    }

    @Override
    public String load(InputStream inputStream, String fileName) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        parser.parse(inputStream, handler, metadata);
        return handler.toString();
    }

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
