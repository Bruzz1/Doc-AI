package com.bruce.docai.service;

import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentService {

    void processFile(MultipartFile file) throws IOException;
}
