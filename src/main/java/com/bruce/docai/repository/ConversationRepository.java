package com.bruce.docai.repository;

import com.bruce.docai.model.Channel;
import com.bruce.docai.model.Conversation;
import com.bruce.docai.model.ConversationMessage;
import com.bruce.docai.model.MessageRole;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for conversation memory (conversations + messages), following the
 * same JdbcClient conventions as {@link RagDocumentRepository}.
 */
@Repository
@RequiredArgsConstructor
public class ConversationRepository {

    private static final String CONVERSATION_COLUMNS = """
            SELECT id, organization_id, channel, user_ref, created_at, last_activity_at
            """;

    private static final String MESSAGE_COLUMNS = """
            SELECT id, conversation_id, organization_id, role, content, tool_name, created_at
            """;

    private final JdbcClient jdbcClient;

    public Optional<Conversation> findById(UUID id, String organizationId) {
        return jdbcClient.sql(CONVERSATION_COLUMNS + """
                FROM conversations
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", id)
                .param("organizationId", organizationId)
                .query(this::mapConversation)
                .optional();
    }

    /**
     * Resolve the single conversation for {@code (organizationId, channel, userRef)},
     * creating it if it does not yet exist. Safe under concurrent first-messages via
     * {@code ON CONFLICT DO NOTHING} followed by a read.
     */
    public Conversation findOrCreate(String organizationId, Channel channel, String userRef) {
        jdbcClient.sql("""
                INSERT INTO conversations (organization_id, channel, user_ref)
                VALUES (:organizationId, :channel, :userRef)
                ON CONFLICT (organization_id, channel, user_ref) DO NOTHING
                """)
                .param("organizationId", organizationId)
                .param("channel", channel.name())
                .param("userRef", userRef)
                .update();

        return jdbcClient.sql(CONVERSATION_COLUMNS + """
                FROM conversations
                WHERE organization_id = :organizationId AND channel = :channel AND user_ref = :userRef
                """)
                .param("organizationId", organizationId)
                .param("channel", channel.name())
                .param("userRef", userRef)
                .query(this::mapConversation)
                .single();
    }

    public void touch(UUID conversationId) {
        jdbcClient.sql("UPDATE conversations SET last_activity_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", conversationId)
                .update();
    }

    /**
     * Remove all messages for a conversation while keeping the conversation row, and
     * reset its activity clock. Used to start a fresh context (idle timeout or an
     * explicit user "new chat") without violating the one-row-per-user unique index.
     */
    public void clearMessages(UUID conversationId) {
        jdbcClient.sql("DELETE FROM messages WHERE conversation_id = :id")
                .param("id", conversationId)
                .update();
        jdbcClient.sql("UPDATE conversations SET last_activity_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", conversationId)
                .update();
    }

    /**
     * Load up to {@code limit} most recent messages for a conversation, returned in
     * chronological (oldest-first) order for prompt construction.
     */
    public List<ConversationMessage> findRecentMessages(UUID conversationId, int limit) {
        List<ConversationMessage> recentFirst = jdbcClient.sql(MESSAGE_COLUMNS + """
                FROM messages
                WHERE conversation_id = :conversationId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """)
                .param("conversationId", conversationId)
                .param("limit", Math.max(1, limit))
                .query(this::mapMessage)
                .list();

        List<ConversationMessage> chronological = new ArrayList<>(recentFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    public void insertMessage(ConversationMessage message) {
        jdbcClient.sql("""
                INSERT INTO messages (conversation_id, organization_id, role, content, tool_name)
                VALUES (:conversationId, :organizationId, :role, :content, :toolName)
                """)
                .param("conversationId", message.conversationId())
                .param("organizationId", message.organizationId())
                .param("role", message.role().name())
                .param("content", message.content())
                .param("toolName", message.toolName())
                .update();
    }

    private Conversation mapConversation(ResultSet rs, int rowNum) throws SQLException {
        return new Conversation(
                rs.getObject("id", UUID.class),
                rs.getString("organization_id"),
                Channel.valueOf(rs.getString("channel")),
                rs.getString("user_ref"),
                toInstant(rs, "created_at"),
                toInstant(rs, "last_activity_at"));
    }

    private ConversationMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new ConversationMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getString("organization_id"),
                MessageRole.valueOf(rs.getString("role")),
                rs.getString("content"),
                rs.getString("tool_name"),
                toInstant(rs, "created_at"));
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toInstant() : Instant.EPOCH;
    }
}
