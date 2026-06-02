package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiLink;
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
public class WikiLinkRepository {

    private final JdbcTemplate jdbc;

    public WikiLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WikiLink> ROW_MAPPER = (rs, i) -> {
        WikiLink l = new WikiLink();
        l.setId(rs.getLong("id"));
        l.setFromPageId(rs.getLong("from_page_id"));
        l.setToPageId(rs.getLong("to_page_id"));
        l.setLinkType(rs.getString("link_type"));
        l.setConfidence(rs.getBigDecimal("confidence"));
        l.setContext(rs.getString("context"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) l.setCreatedAt(createdAt.toLocalDateTime());
        return l;
    };

    public WikiLink save(WikiLink link) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO wiki_links (from_page_id, to_page_id, link_type, confidence, context) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, link.getFromPageId());
            ps.setLong(2, link.getToPageId());
            ps.setString(3, link.getLinkType() != null ? link.getLinkType() : "REFERENCES");
            ps.setBigDecimal(4, link.getConfidence());
            ps.setString(5, link.getContext());
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() != null) {
            link.setId(keyHolder.getKey().longValue());
        }
        return link;
    }

    public List<WikiLink> findByFromPageId(Long fromPageId) {
        return jdbc.query("SELECT * FROM wiki_links WHERE from_page_id = ?", ROW_MAPPER, fromPageId);
    }

    public List<WikiLink> findByToPageId(Long toPageId) {
        return jdbc.query("SELECT * FROM wiki_links WHERE to_page_id = ?", ROW_MAPPER, toPageId);
    }

    public List<WikiLink> findAllByPageId(Long pageId) {
        return jdbc.query("SELECT * FROM wiki_links WHERE from_page_id = ? OR to_page_id = ?", ROW_MAPPER, pageId, pageId);
    }

    public boolean exists(Long fromPageId, Long toPageId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wiki_links WHERE from_page_id = ? AND to_page_id = ?",
            Integer.class, fromPageId, toPageId
        );
        return count != null && count > 0;
    }

    public List<WikiLink> findAll() {
        return jdbc.query("SELECT * FROM wiki_links", ROW_MAPPER);
    }

    public int deleteByPageId(Long pageId) {
        return jdbc.update("DELETE FROM wiki_links WHERE from_page_id = ? OR to_page_id = ?", pageId, pageId);
    }

    public int countAll() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM wiki_links", Integer.class);
        return count != null ? count : 0;
    }
}
