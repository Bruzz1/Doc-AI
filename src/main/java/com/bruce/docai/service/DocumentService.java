package com.bruce.docai.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentService {

    void processFile(MultipartFile file) throws IOException;

    void processResource(Resource resource) throws IOException;
}
