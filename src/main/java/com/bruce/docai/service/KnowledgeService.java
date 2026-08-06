package com.bruce.docai.service;

import com.bruce.docai.model.KnowledgeEntry;
import com.bruce.docai.model.KnowledgeUploadPayload;
import com.bruce.docai.model.User;
import com.bruce.docai.repository.KnowledgeEntryRepository;
import com.bruce.docai.repository.KnowledgeUploadPayloadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

	private final DocumentService documentService;
	private final KnowledgeEntryRepository knowledgeEntryRepository;
	private final KnowledgeUploadPayloadRepository knowledgeUploadPayloadRepository;
	private final JdbcTemplate jdbcTemplate;

	@Transactional(readOnly = true)
	public List<KnowledgeEntry> listKnowledge(User user) {
		return knowledgeEntryRepository.findByOrganizationIdOrderByCreatedAtDesc(user.getOrganizationId());
	}

	@Transactional(rollbackFor = Exception.class)
	public KnowledgeEntry addKnowledge(MultipartFile file, User user) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Please select a document to upload.");
		}

		String knowledgeId = UUID.randomUUID().toString();
		documentService.processFile(file, new IngestionMetadataContext(knowledgeId, user.getOrganizationId(), user.getEmail()));

		KnowledgeEntry entry = new KnowledgeEntry();
		entry.setKnowledgeId(knowledgeId);
		entry.setFilename(resolveFileName(file));
		entry.setContentType(file.getContentType());
		entry.setOrganizationId(user.getOrganizationId());
		entry.setUploadedBy(user.getEmail());
		KnowledgeEntry saved = knowledgeEntryRepository.save(entry);

		KnowledgeUploadPayload payload = new KnowledgeUploadPayload();
		payload.setKnowledgeId(knowledgeId);
		payload.setFilename(saved.getFilename());
		payload.setStoragePath(null);
		knowledgeUploadPayloadRepository.save(payload);

		return saved;
	}

	@Transactional
	public void removeKnowledge(String knowledgeId, User user) {
		KnowledgeEntry entry = knowledgeEntryRepository.findByKnowledgeIdAndOrganizationId(knowledgeId, user.getOrganizationId())
				.orElseThrow(() -> new IllegalArgumentException("Knowledge not found."));

		jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'knowledge-id' = ?", entry.getKnowledgeId());
		knowledgeUploadPayloadRepository.deleteById(entry.getKnowledgeId());
		knowledgeEntryRepository.delete(entry);
	}

	private String resolveFileName(MultipartFile file) {
		return file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
				? "uploaded-document"
				: file.getOriginalFilename();
	}
}


