package com.bruce.docai.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader {

    private final VectorStore vectorStore;
    private final JdbcClient jdbcClient;

    @Value("classpath:/RJBRUCE_CV.pdf")
    private Resource pdfResource;

    @PostConstruct
    public void init() {
        Integer count = jdbcClient.sql("select COUNT(*) from vector_store")
                .query(Integer.class)
                .single();

        System.out.println("No of Records in the PG Vectore Store: " +count);
        if (count == 0) {
            System.out.println("Loading personal resume");
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(1)
                    .build();
            PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource, config);


            var textSplitter = new TokenTextSplitter();
            vectorStore.accept(textSplitter.apply(reader.get()));

            System.out.println("Application is ready to serve the request");
        }
    }
}
