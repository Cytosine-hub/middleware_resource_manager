package com.middleware.manager.security;

import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import com.middleware.manager.security.gateway.IdentityHeaderCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> SKIP_PATHS = List.of(
            "/api/public/**",
            "/api/auth/login",
            "/api/auth/introspect",
            "/files/**");

    private final GatewaySignatureService signatureService;

    public GatewayHeaderAuthenticationFilter(GatewaySignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SKIP_PATHS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        // 头里的 user/displayName/category 是网关 Base64(URL-safe) 编码后的值（防中文在头传输中损坏）。
        // 验签必须用编码后的原始头值（与网关签名一致），验签通过后再解码回真实值构建认证。
        String usernameHeader = request.getHeader(GatewayIdentityHeaders.USER);
        String displayNameHeader = request.getHeader(GatewayIdentityHeaders.DISPLAY_NAME);
        String rolesHeader = request.getHeader(GatewayIdentityHeaders.ROLES);
        String categoryHeader = request.getHeader(GatewayIdentityHeaders.CATEGORY);
        String categoryAdmin = request.getHeader(GatewayIdentityHeaders.CATEGORY_ADMIN);
        String signature = request.getHeader(GatewayIdentityHeaders.SIGNATURE);

        if (!StringUtils.hasText(usernameHeader) || !StringUtils.hasText(rolesHeader)
                || !("true".equals(categoryAdmin) || "false".equals(categoryAdmin))) {
            return;
        }
        if (!signatureService.verifyIdentityHeaders(
                usernameHeader, displayNameHeader, rolesHeader, categoryHeader, categoryAdmin, signature)) {
            return;
        }

        List<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (roles.isEmpty()) {
            return;
        }

        String username = IdentityHeaderCodec.decode(usernameHeader);
        String displayName = IdentityHeaderCodec.decode(displayNameHeader);
        String category = IdentityHeaderCodec.decode(categoryHeader);
        if (!StringUtils.hasText(username)) {
            return;
        }

        GatewayAuthenticationToken authentication = GatewayAuthenticationToken.authenticated(
                username,
                displayName,
                roles,
                StringUtils.hasText(category) ? category : null,
                Boolean.parseBoolean(categoryAdmin));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
