package com.bruce.docai.controller;

import com.bruce.docai.model.KnowledgeEntry;
import com.bruce.docai.model.User;
import com.bruce.docai.service.KnowledgeService;
import com.bruce.docai.service.UnsupportedDocumentTypeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class KnowledgeController {

	private final KnowledgeService knowledgeService;

	@PostMapping("/upload")
	public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {
		User user = (User) authentication.getPrincipal();
		KnowledgeEntry entry = knowledgeService.addKnowledge(file, user);
		return ResponseEntity.ok("Uploaded: " + entry.getFilename());
	}

	@GetMapping("/api/admin/knowledge")
	@PreAuthorize("hasRole('ADMIN')")
	public List<KnowledgeResponse> listKnowledge(Authentication authentication) {
		User user = (User) authentication.getPrincipal();
		return knowledgeService.listKnowledge(user)
				.stream()
				.map(KnowledgeResponse::from)
				.toList();
	}

	@PostMapping("/api/admin/knowledge")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Map<String, String>> addKnowledge(
			@RequestParam("file") MultipartFile file,
			Authentication authentication
	) throws IOException {
		User user = (User) authentication.getPrincipal();
		KnowledgeEntry entry = knowledgeService.addKnowledge(file, user);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(Map.of("message", "Knowledge added", "knowledgeId", entry.getKnowledgeId()));
	}

	@DeleteMapping("/api/admin/knowledge/{knowledgeId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Map<String, String>> removeKnowledge(
			@PathVariable String knowledgeId,
			Authentication authentication
	) {
		User user = (User) authentication.getPrincipal();
		knowledgeService.removeKnowledge(knowledgeId, user);
		return ResponseEntity.ok(Map.of("message", "Knowledge removed"));
	}

	@ExceptionHandler({IllegalArgumentException.class, UnsupportedDocumentTypeException.class})
	public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException exception) {
		return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
	}

	@ExceptionHandler(IOException.class)
	public ResponseEntity<Map<String, String>> handleIo(IOException exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", exception.getMessage()));
	}

	public record KnowledgeResponse(
			String knowledgeId,
			String filename,
			String contentType,
			String uploadedBy,
			String createdAt
	) {
		public static KnowledgeResponse from(KnowledgeEntry entry) {
			return new KnowledgeResponse(
					entry.getKnowledgeId(),
					entry.getFilename(),
					entry.getContentType(),
					entry.getUploadedBy(),
					entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null
			);
		}
	}
}


