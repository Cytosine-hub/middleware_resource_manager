package com.middleware.manager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenService.class);
    private static final long TOKEN_EXPIRY_HOURS = 2;

    private final JdbcTemplate jdbcTemplate;

    public TokenService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String createToken(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);

        jdbcTemplate.update(
                "INSERT INTO user_tokens (token, username, expires_at) VALUES (?, ?, ?)",
                token, username, expiresAt);

        LOGGER.info("token created for user={} expiresAt={}", username, expiresAt);
        return token;
    }

    public String validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        // 清理过期 token
        jdbcTemplate.update("DELETE FROM user_tokens WHERE expires_at < NOW()");

        String username = jdbcTemplate.queryForObject(
                "SELECT username FROM user_tokens WHERE token = ? AND expires_at >= NOW()",
                String.class, token);

        if (username != null) {
            // 滑动续期
            LocalDateTime newExpiry = LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);
            jdbcTemplate.update("UPDATE user_tokens SET expires_at = ? WHERE token = ?",
                    newExpiry, token);
        }

        return username;
    }

    public void deleteToken(String token) {
        if (token != null) {
            int deleted = jdbcTemplate.update("DELETE FROM user_tokens WHERE token = ?", token);
            LOGGER.info("token deleted rows={}", deleted);
        }
    }

    public void deleteAllTokensForUser(String username) {
        jdbcTemplate.update("DELETE FROM user_tokens WHERE username = ?", username);
        LOGGER.info("all tokens deleted for user={}", username);
    }
}
