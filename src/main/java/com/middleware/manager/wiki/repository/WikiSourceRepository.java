package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class WikiSourceRepository {

    private final JdbcTemplate jdbc;

    public WikiSourceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WikiSource> ROW_MAPPER = (rs, i) -> {
        WikiSource s = new WikiSource();
        s.setId(rs.getLong("id"));
        s.setTitle(rs.getString("title"));
        s.setSourceType(rs.getString("source_type"));
        s.setFilePath(rs.getString("file_path"));
        s.setContentHash(rs.getString("content_hash"));
        s.setContent(rs.getString("content"));
        s.setCategory(rs.getString("category"));
        s.setSoftware(rs.getString("software"));
        s.setIngested(rs.getBoolean("ingested"));
        Timestamp ingestedAt = rs.getTimestamp("ingested_at");
        if (ingestedAt != null) s.setIngestedAt(ingestedAt.toLocalDateTime());
        s.setCreatedBy(rs.getLong("created_by"));
        if (rs.wasNull()) s.setCreatedBy(null);
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) s.setCreatedAt(createdAt.toLocalDateTime());
        return s;
    };

    public WikiSource save(WikiSource source) {
        if (source.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(conn -> {
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO wiki_sources (title, source_type, file_path, content_hash, content, category, software, created_by) VALUES (?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, source.getTitle());
                ps.setString(2, source.getSourceType());
                ps.setString(3, source.getFilePath());
                ps.setString(4, source.getContentHash());
                ps.setString(5, source.getContent());
                ps.setString(6, source.getCategory());
                ps.setString(7, source.getSoftware());
                ps.setObject(8, source.getCreatedBy());
                return ps;
            }, keyHolder);
            source.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update(
                "UPDATE wiki_sources SET title=?, source_type=?, file_path=?, content_hash=?, content=?, category=?, software=?, ingested=?, ingested_at=? WHERE id=?",
                source.getTitle(), source.getSourceType(), source.getFilePath(), source.getContentHash(),
                source.getContent(), source.getCategory(), source.getSoftware(), source.isIngested(),
                source.getIngestedAt() != null ? Timestamp.valueOf(source.getIngestedAt()) : null,
                source.getId()
            );
        }
        return source;
    }

    public Optional<WikiSource> findById(Long id) {
        List<WikiSource> results = jdbc.query("SELECT * FROM wiki_sources WHERE id = ?", ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    public List<WikiSource> findAll() {
        return jdbc.query("SELECT * FROM wiki_sources ORDER BY created_at DESC", ROW_MAPPER);
    }

    public List<WikiSource> findByIngested(boolean ingested) {
        return jdbc.query("SELECT * FROM wiki_sources WHERE ingested = ? ORDER BY created_at DESC", ROW_MAPPER, ingested);
    }

    public Optional<WikiSource> findByContentHash(String hash) {
        List<WikiSource> results = jdbc.query("SELECT * FROM wiki_sources WHERE content_hash = ?", ROW_MAPPER, hash);
        return results.stream().findFirst();
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM wiki_sources WHERE id = ?", id);
    }
}
