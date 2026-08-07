package com.bruce.docai.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface DocumentService {

    void processFile(MultipartFile file, String organizationId, UUID documentId) throws IOException;

    void processResource(Resource resource) throws IOException;

    default void processResource(Resource resource, String organizationId, UUID documentId) throws IOException {
        processResource(resource);
    }
}
