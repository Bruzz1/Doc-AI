package com.bruce.docai.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentService {

    void processFile(MultipartFile file, IngestionMetadataContext context) throws IOException;

    void processResource(Resource resource, IngestionMetadataContext context) throws IOException;

    default void processFile(MultipartFile file) throws IOException {
        processFile(file, null);
    }

    default void processResource(Resource resource) throws IOException {
        processResource(resource, null);
    }
}
