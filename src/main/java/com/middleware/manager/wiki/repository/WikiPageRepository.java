package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiPage;
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
public class WikiPageRepository {

    private final JdbcTemplate jdbc;

    public WikiPageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WikiPage> ROW_MAPPER = (rs, i) -> {
        WikiPage p = new WikiPage();
        p.setId(rs.getLong("id"));
        p.setTitle(rs.getString("title"));
        p.setPageType(rs.getString("page_type"));
        p.setCategory(rs.getString("category"));
        p.setSoftware(rs.getString("software"));
        p.setVersion(rs.getString("version"));
        p.setContent(rs.getString("content"));
        p.setSummary(rs.getString("summary"));
        p.setSourceRefs(rs.getString("source_refs"));
        p.setStatus(rs.getString("status"));
        p.setContradictionNote(rs.getString("contradiction_note"));
        p.setCompiledBy(rs.getString("compiled_by"));
        Timestamp compiledAt = rs.getTimestamp("compiled_at");
        if (compiledAt != null) p.setCompiledAt(compiledAt.toLocalDateTime());
        p.setReviewedBy(rs.getLong("reviewed_by"));
        if (rs.wasNull()) p.setReviewedBy(null);
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        if (reviewedAt != null) p.setReviewedAt(reviewedAt.toLocalDateTime());
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) p.setCreatedAt(createdAt.toLocalDateTime());
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) p.setUpdatedAt(updatedAt.toLocalDateTime());
        return p;
    };

    public WikiPage save(WikiPage page) {
        if (page.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(conn -> {
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO wiki_pages (title, page_type, category, software, version, content, summary, source_refs, status, compiled_by, compiled_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, page.getTitle());
                ps.setString(2, page.getPageType());
                ps.setString(3, page.getCategory());
                ps.setString(4, page.getSoftware());
                ps.setString(5, page.getVersion());
                ps.setString(6, page.getContent());
                ps.setString(7, page.getSummary());
                ps.setString(8, page.getSourceRefs());
                ps.setString(9, page.getStatus() != null ? page.getStatus() : "DRAFT");
                ps.setString(10, page.getCompiledBy());
                ps.setTimestamp(11, page.getCompiledAt() != null ? Timestamp.valueOf(page.getCompiledAt()) : null);
                return ps;
            }, keyHolder);
            page.setId(keyHolder.getKey().longValue());
        } else {
            jdbc.update(
                "UPDATE wiki_pages SET title=?, page_type=?, category=?, software=?, version=?, content=?, summary=?, source_refs=?, status=?, contradiction_note=?, compiled_by=?, compiled_at=?, reviewed_by=?, reviewed_at=?, updated_at=NOW() WHERE id=?",
                page.getTitle(), page.getPageType(), page.getCategory(), page.getSoftware(), page.getVersion(),
                page.getContent(), page.getSummary(), page.getSourceRefs(), page.getStatus(),
                page.getContradictionNote(), page.getCompiledBy(),
                page.getCompiledAt() != null ? Timestamp.valueOf(page.getCompiledAt()) : null,
                page.getReviewedBy(),
                page.getReviewedAt() != null ? Timestamp.valueOf(page.getReviewedAt()) : null,
                page.getId()
            );
        }
        return page;
    }

    public Optional<WikiPage> findById(Long id) {
        List<WikiPage> results = jdbc.query("SELECT * FROM wiki_pages WHERE id = ?", ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    public Optional<WikiPage> findByTitleAndType(String title, String pageType) {
        List<WikiPage> results = jdbc.query("SELECT * FROM wiki_pages WHERE title = ? AND page_type = ?", ROW_MAPPER, title, pageType);
        return results.stream().findFirst();
    }

    public List<WikiPage> findAll() {
        return jdbc.query("SELECT * FROM wiki_pages ORDER BY updated_at DESC", ROW_MAPPER);
    }

    public List<WikiPage> findByCategory(String category) {
        return jdbc.query("SELECT * FROM wiki_pages WHERE category = ? ORDER BY title", ROW_MAPPER, category);
    }

    public List<WikiPage> findBySoftware(String software) {
        return jdbc.query("SELECT * FROM wiki_pages WHERE software = ? ORDER BY title", ROW_MAPPER, software);
    }

    public List<WikiPage> findByStatus(String status) {
        return jdbc.query("SELECT * FROM wiki_pages WHERE status = ? ORDER BY updated_at DESC", ROW_MAPPER, status);
    }

    public List<WikiPage> fulltextSearch(String query, int limit) {
        return jdbc.query(
            "SELECT *, MATCH(title, summary, content) AGAINST(? IN BOOLEAN MODE) AS relevance FROM wiki_pages WHERE MATCH(title, summary, content) AGAINST(? IN BOOLEAN MODE) ORDER BY relevance DESC LIMIT ?",
            ROW_MAPPER, query, query, limit
        );
    }

    public List<WikiPage> findByTitleContaining(String keyword, int limit) {
        return jdbc.query(
            "SELECT * FROM wiki_pages WHERE title LIKE ? ORDER BY updated_at DESC LIMIT ?",
            ROW_MAPPER, "%" + keyword + "%", limit
        );
    }

    public int countByStatus(String status) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM wiki_pages WHERE status = ?", Integer.class, status);
        return count != null ? count : 0;
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM wiki_pages WHERE id = ?", id);
    }

    public List<WikiPage> findOrphanPages() {
        return jdbc.query(
            "SELECT p.* FROM wiki_pages p LEFT JOIN wiki_links l ON p.id = l.to_page_id WHERE l.id IS NULL AND p.status = 'ACTIVE' AND p.page_type != 'OVERVIEW'",
            ROW_MAPPER
        );
    }

    public List<WikiPage> findStalePages(int daysSinceUpdate) {
        return jdbc.query(
            "SELECT * FROM wiki_pages WHERE status = 'ACTIVE' AND updated_at < DATE_SUB(NOW(), INTERVAL ? DAY)",
            ROW_MAPPER, daysSinceUpdate
        );
    }
}
