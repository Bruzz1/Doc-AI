package com.bruce.docai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.invoker.HttpRequestValues;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service("tika-parser")
@RequiredArgsConstructor
@Slf4j
public class TikaDocumentParserServiceImpl implements DocumentService{

    private final VectorStore vectorStore;

    @Override
    public void processFile(MultipartFile file) throws IOException {
        Tika tika = new Tika();
        Metadata metadata = new Metadata();
        try (InputStream inputStream = file.getInputStream()) {
            log.error("Parsing file {}", file.getOriginalFilename());

            String content = tika.parseToString(inputStream, metadata);

            Map<String, Object> metaMap = new HashMap<>();
            metaMap.put("filename", file.getOriginalFilename());
            metaMap.put("content-type", file.getContentType());
            metaMap.put("size", file.getSize());

            for (String name : metadata.names()) {
                metaMap.put(name, metadata.get(name));
            }

            Document document = new Document(content, metaMap);
            vectorStore.accept(new TokenTextSplitter().split(document));
            log.error("File Successfully loaded into the vector store");
        } catch (TikaException e) {
            log.error("File processing failed {}", file.getOriginalFilename());
        }
    }
}
