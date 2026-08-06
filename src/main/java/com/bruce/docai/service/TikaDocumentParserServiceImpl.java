package com.bruce.docai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service("tika-parser")
@RequiredArgsConstructor
@Slf4j
public class TikaDocumentParserServiceImpl implements DocumentService{

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt");
    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Set.of(
            MediaType.application("pdf"),
            MediaType.application("msword"),
            MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document"),
            MediaType.text("plain"),
            MediaType.application("octet-stream")
    );
    private static final Pattern WHITESPACE_EXCEPT_NEWLINES = Pattern.compile("[\\t\\x0B\\f\\r ]+");
    private static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("\\n{3,}");

    private final VectorStore vectorStore;
    private final Tika tika = new Tika(TikaConfig.getDefaultConfig());

    @Value("${app.rag.chunk-size:500}")
    private int chunkSize = 500;

    @Value("${app.rag.min-chunk-size-chars:150}")
    private int minChunkSizeChars = 150;

    @Value("${app.rag.min-chunk-length-to-embed:150}")
    private int minChunkLengthToEmbed = 150;

    @Value("${app.rag.max-num-chunks:300}")
    private int maxNumChunks = 300;

    @Override
    public void processFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty. Allowed types: pdf, doc, docx, txt.");
        }

        ingest(file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream());
    }

    @Override
    public void processResource(Resource resource) throws IOException {
        if (resource == null || !resource.exists()) {
            throw new IllegalArgumentException("Seed resource does not exist.");
        }

        String filename = resource.getFilename();
        long contentLength = safeContentLength(resource);
        ingest(filename, null, contentLength, resource.getInputStream());
    }

    private void ingest(String filename, String declaredContentType, long size, InputStream sourceStream) throws IOException {
        Metadata metadata = new Metadata();
        if (filename != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        if (declaredContentType != null && !declaredContentType.isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, declaredContentType);
        }

        byte[] bytes;
        try (InputStream inputStream = new BufferedInputStream(sourceStream)) {
            bytes = inputStream.readAllBytes();
        }

        if (bytes.length == 0) {
            throw new IllegalArgumentException("File is empty. Allowed types: pdf, doc, docx, txt.");
        }

        String extension = getExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedDocumentTypeException(buildUnsupportedTypeMessage(filename, declaredContentType));
        }

        MediaType detectedMediaType = detectMediaType(bytes, metadata);
        if (!isAllowedMediaType(detectedMediaType, extension)) {
            throw new UnsupportedDocumentTypeException(buildUnsupportedTypeMessage(filename, detectedMediaType != null ? detectedMediaType.toString() : declaredContentType));
        }

        metadata.set(Metadata.CONTENT_TYPE, detectedMediaType.toString());

        try (InputStream parsingStream = new ByteArrayInputStream(bytes)) {
            log.info("Parsing file {}", filename);
            String content = tika.parseToString(parsingStream, metadata);
            String normalizedContent = normalizeContent(content);

            if (normalizedContent.isBlank()) {
                throw new IllegalArgumentException("The uploaded file does not contain readable text. Allowed types: pdf, doc, docx, txt.");
            }

            Map<String, Object> metaMap = buildMetadata(filename, declaredContentType, detectedMediaType, size, bytes.length, extension, metadata);
            Document document = new Document(normalizedContent, metaMap);
            List<Document> chunks = createTextSplitter().split(document);
            if (chunks.isEmpty()) {
                chunks = List.of(document);
            }

            vectorStore.accept(chunks);
            log.info("File successfully loaded into the vector store: {}", filename);
        } catch (TikaException e) {
            log.error("File processing failed {}", filename, e);
            throw new IOException("Unable to read the uploaded file.", e);
        }
    }

    private MediaType detectMediaType(byte[] bytes, Metadata metadata) throws IOException {
        try (InputStream detectionStream = new ByteArrayInputStream(bytes)) {
            return tika.getDetector().detect(detectionStream, metadata);
        }
    }

    private TokenTextSplitter createTextSplitter() {
        int resolvedChunkSize = Math.max(100, chunkSize);
        int resolvedMinChunkSizeChars = Math.max(1, Math.min(minChunkSizeChars, resolvedChunkSize));
        int resolvedMinChunkLengthToEmbed = Math.max(1, Math.min(minChunkLengthToEmbed, resolvedChunkSize));
        int resolvedMaxNumChunks = Math.max(1, maxNumChunks);
        return new TokenTextSplitter(
                resolvedChunkSize,
                resolvedMinChunkSizeChars,
                resolvedMinChunkLengthToEmbed,
                resolvedMaxNumChunks,
                true
        );
    }

    private boolean isAllowedMediaType(MediaType detectedMediaType, String extension) {
        if (detectedMediaType == null) {
            return false;
        }

        if ("txt".equals(extension)) {
            return MediaType.text("plain").equals(detectedMediaType)
                    || MediaType.application("octet-stream").equals(detectedMediaType);
        }

        if ("pdf".equals(extension)) {
            return MediaType.application("pdf").equals(detectedMediaType);
        }

        if ("doc".equals(extension)) {
            return MediaType.application("msword").equals(detectedMediaType)
                    || MediaType.application("x-tika-msoffice").equals(detectedMediaType);
        }

        if ("docx".equals(extension)) {
            return MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document").equals(detectedMediaType)
                    || MediaType.application("zip").equals(detectedMediaType);
        }

        return ALLOWED_MEDIA_TYPES.contains(detectedMediaType);
    }

    private Map<String, Object> buildMetadata(
            String filename,
            String declaredContentType,
            MediaType detectedMediaType,
            long declaredSize,
            int actualSize,
            String extension,
            Metadata metadata
    ) {
        Map<String, Object> metaMap = new HashMap<>();
        putIfNotNull(metaMap, "filename", filename);
        putIfNotNull(metaMap, "extension", extension);
        putIfNotNull(metaMap, "declared-content-type", declaredContentType);
        putIfNotNull(metaMap, "detected-content-type", detectedMediaType != null ? detectedMediaType.toString() : null);
        metaMap.put("size", declaredSize > 0 ? declaredSize : actualSize);
        metaMap.put("normalized", true);

        Arrays.stream(metadata.names())
                .filter(name -> metadata.get(name) != null)
                .forEach(name -> metaMap.put(name, metadata.get(name)));

        return metaMap;
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }

        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        normalized = WHITESPACE_EXCEPT_NEWLINES.matcher(normalized).replaceAll(" ");
        normalized = normalized.lines()
                .map(String::stripTrailing)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        normalized = EXCESSIVE_NEWLINES.matcher(normalized).replaceAll("\n\n");
        return normalized.strip();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }

        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String buildUnsupportedTypeMessage(String filename, String actualType) {
        String resolvedType = actualType != null && !actualType.isBlank() ? actualType : "unknown";
        return "Unsupported file '" + (filename != null ? filename : "unknown") + "' with type '" + resolvedType
                + "'. Allowed types: pdf, doc, docx, txt.";
    }

    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private long safeContentLength(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException ex) {
            log.debug("Unable to determine resource size for {}", resource.getFilename(), ex);
            return -1L;
        }
    }
}
