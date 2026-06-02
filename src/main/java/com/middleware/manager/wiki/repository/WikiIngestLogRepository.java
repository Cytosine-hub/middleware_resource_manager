package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiIngestLog;
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
public class WikiIngestLogRepository {

    private final JdbcTemplate jdbc;

    public WikiIngestLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WikiIngestLog> ROW_MAPPER = (rs, i) -> {
        WikiIngestLog log = new WikiIngestLog();
        log.setId(rs.getLong("id"));
        log.setSourceId(rs.getLong("source_id"));
        log.setOperatorId(rs.getLong("operator_id"));
        log.setPagesCreated(rs.getInt("pages_created"));
        log.setPagesUpdated(rs.getInt("pages_updated"));
        log.setLinksCreated(rs.getInt("links_created"));
        log.setContradictionsFound(rs.getInt("contradictions_found"));
        log.setLlmModel(rs.getString("llm_model"));
        log.setLlmTokensUsed(rs.getInt("llm_tokens_used"));
        log.setDurationMs(rs.getInt("duration_ms"));
        log.setStatus(rs.getString("status"));
        log.setErrorDetail(rs.getString("error_detail"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) log.setCreatedAt(createdAt.toLocalDateTime());
        return log;
    };

    public WikiIngestLog save(WikiIngestLog log) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO wiki_ingest_log (source_id, operator_id, pages_created, pages_updated, links_created, contradictions_found, llm_model, llm_tokens_used, duration_ms, status, error_detail) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, log.getSourceId());
            ps.setLong(2, log.getOperatorId());
            ps.setInt(3, log.getPagesCreated());
            ps.setInt(4, log.getPagesUpdated());
            ps.setInt(5, log.getLinksCreated());
            ps.setInt(6, log.getContradictionsFound());
            ps.setString(7, log.getLlmModel());
            ps.setInt(8, log.getLlmTokensUsed());
            ps.setInt(9, log.getDurationMs());
            ps.setString(10, log.getStatus());
            ps.setString(11, log.getErrorDetail());
            return ps;
        }, keyHolder);
        log.setId(keyHolder.getKey().longValue());
        return log;
    }

    public List<WikiIngestLog> findBySourceId(Long sourceId) {
        return jdbc.query("SELECT * FROM wiki_ingest_log WHERE source_id = ? ORDER BY created_at DESC", ROW_MAPPER, sourceId);
    }

    public List<WikiIngestLog> findRecent(int limit) {
        return jdbc.query("SELECT * FROM wiki_ingest_log ORDER BY created_at DESC LIMIT ?", ROW_MAPPER, limit);
    }
}
