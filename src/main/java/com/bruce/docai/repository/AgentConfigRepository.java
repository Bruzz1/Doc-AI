package com.bruce.docai.repository;

import com.bruce.docai.model.AgentConfig;
import com.bruce.docai.model.AgentMode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Access to the single per-tenant {@link AgentConfig} row. Follows the same
 * JdbcClient conventions as {@link RagDocumentRepository}.
 */
@Repository
@RequiredArgsConstructor
public class AgentConfigRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, organization_id, name, mode, system_prompt, model, temperature,
                   enabled_tools, top_k, similarity_threshold, max_context_chars,
                   enabled, created_at, updated_at
            """;

    private final JdbcClient jdbcClient;

    public Optional<AgentConfig> findByOrganizationId(String organizationId) {
        return jdbcClient.sql(SELECT_COLUMNS + """
                FROM agent_config
                WHERE organization_id = :organizationId
                """)
                .param("organizationId", organizationId)
                .query(this::map)
                .optional();
    }

    public void insert(AgentConfig config) {
        jdbcClient.sql("""
                INSERT INTO agent_config
                    (organization_id, name, mode, system_prompt, model, temperature,
                     enabled_tools, top_k, similarity_threshold, max_context_chars, enabled)
                VALUES (:organizationId, :name, :mode, :systemPrompt, :model, :temperature,
                        :enabledTools, :topK, :similarityThreshold, :maxContextChars, :enabled)
                """)
                .param("organizationId", config.organizationId())
                .param("name", config.name())
                .param("mode", config.mode().name())
                .param("systemPrompt", config.systemPrompt())
                .param("model", config.model())
                .param("temperature", config.temperature())
                .param("enabledTools", config.enabledTools())
                .param("topK", config.topK())
                .param("similarityThreshold", config.similarityThreshold())
                .param("maxContextChars", config.maxContextChars())
                .param("enabled", config.enabled())
                .update();
    }

    public void update(AgentConfig config) {
        jdbcClient.sql("""
                UPDATE agent_config
                SET name = :name,
                    mode = :mode,
                    system_prompt = :systemPrompt,
                    model = :model,
                    temperature = :temperature,
                    enabled_tools = :enabledTools,
                    top_k = :topK,
                    similarity_threshold = :similarityThreshold,
                    max_context_chars = :maxContextChars,
                    enabled = :enabled,
                    updated_at = CURRENT_TIMESTAMP
                WHERE organization_id = :organizationId
                """)
                .param("organizationId", config.organizationId())
                .param("name", config.name())
                .param("mode", config.mode().name())
                .param("systemPrompt", config.systemPrompt())
                .param("model", config.model())
                .param("temperature", config.temperature())
                .param("enabledTools", config.enabledTools())
                .param("topK", config.topK())
                .param("similarityThreshold", config.similarityThreshold())
                .param("maxContextChars", config.maxContextChars())
                .param("enabled", config.enabled())
                .update();
    }

    private AgentConfig map(ResultSet rs, int rowNum) throws SQLException {
        return new AgentConfig(
                rs.getObject("id", UUID.class),
                rs.getString("organization_id"),
                rs.getString("name"),
                AgentMode.valueOf(rs.getString("mode")),
                rs.getString("system_prompt"),
                rs.getString("model"),
                getNullableDouble(rs, "temperature"),
                rs.getString("enabled_tools"),
                getNullableInt(rs, "top_k"),
                getNullableDouble(rs, "similarity_threshold"),
                getNullableInt(rs, "max_context_chars"),
                rs.getBoolean("enabled"),
                toInstant(rs, "created_at"),
                toInstant(rs, "updated_at"));
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toInstant() : Instant.EPOCH;
    }
}
