package com.middleware.manager.knowledge.agent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class ChatMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ChatMessage> rowMapper = (rs, rowNum) -> {
        ChatMessage m = new ChatMessage();
        m.setId(rs.getLong("id"));
        m.setSessionId(rs.getLong("session_id"));
        m.setRole(rs.getString("role"));
        m.setContent(rs.getString("content"));
        m.setReferencesText(rs.getString("references_text"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            m.setCreatedAt(ts.toLocalDateTime());
        }
        return m;
    };

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatMessage save(ChatMessage m) {
        if (m.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO chat_messages (session_id, role, content, references_text, created_at) " +
                                "VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setLong(1, m.getSessionId());
                ps.setString(2, m.getRole());
                ps.setString(3, m.getContent());
                ps.setString(4, m.getReferencesText());
                ps.setTimestamp(5, m.getCreatedAt() != null ? Timestamp.valueOf(m.getCreatedAt()) : Timestamp.valueOf(java.time.LocalDateTime.now()));
                return ps;
            }, keyHolder);
            m.setId(keyHolder.getKey().longValue());
        }
        return m;
    }

    public List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId) {
        return jdbcTemplate.query(
                "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY created_at ASC",
                rowMapper,
                sessionId
        );
    }
}
