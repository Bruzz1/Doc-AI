package com.bruce.docai.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface DocumentParserService {

    String parse(MultipartFile file) throws IOException;
}
