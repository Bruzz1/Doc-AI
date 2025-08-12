package com.bruce.docai.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentService {

    String processFile(MultipartFile file) throws IOException;
}
