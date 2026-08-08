package com.bruce.docai.controller;

import com.bruce.docai.model.RagDocument;
import com.bruce.docai.model.User;
import com.bruce.docai.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDocumentController {

    private final KnowledgeService knowledgeService;

    @GetMapping
    public List<RagDocument> list(Authentication authentication) {
        return knowledgeService.list(organizationId(authentication));
    }

    @PostMapping
    public ResponseEntity<RagDocument> add(@RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(knowledgeService.add(file, user.getOrganizationId(), user.getEmail()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> remove(@PathVariable UUID id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        knowledgeService.remove(id, user.getOrganizationId(), user.getEmail());
        return ResponseEntity.ok(Map.of("message", "Document removed from the RAG knowledge base."));
    }

    private String organizationId(Authentication authentication) {
        return ((User) authentication.getPrincipal()).getOrganizationId();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({IllegalArgumentException.class, IOException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage() != null
                ? exception.getMessage() : "Unable to process the document."));
    }
}


