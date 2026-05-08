package com.bruce.docai.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service("pdfbox-parser")
@RequiredArgsConstructor
@Slf4j
class PdfDocumentParserServiceImpl implements DocumentService{

    private final VectorStore vectorStore;

    @Override
    public void processFile(MultipartFile file) throws IOException {
        String type = file.getContentType();
        if (type != null && type.contains("pdf")) {
            log.error("Parsing PDF File {}", file.getOriginalFilename());

            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(1)
                    .build();
            PagePdfDocumentReader reader = new PagePdfDocumentReader((Resource) file, config);
            log.error("Chunking PDF File {}", file.getOriginalFilename());
            vectorStore.accept(new TokenTextSplitter().apply(reader.get()));
            log.error("File Successfully loaded into the vector store");
        } else  {
            log.error("Invalid file type, file must be a PDF file");
        }
    }


}
