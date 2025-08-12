package com.bruce.docai.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
class DocumentParserServiceImpl implements DocumentService{

    @Override
    public String processFile(MultipartFile file) throws IOException {
        String type = file.getContentType();
        if (type != null && type.contains("pdf")) {
//            return parsePdf(file);
        } else if (type != null && type.contains("word")) {
            return parseWord(file);
        }
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    private String parseWord(MultipartFile file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            return doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }

//    private String parsePdf(MultipartFile file) throws IOException {
//        try (PDDocument doc = PDDocument (file.getInputStream())) {
//            return new PDFTextStripper().getText(doc);
//        }
//    }
}
