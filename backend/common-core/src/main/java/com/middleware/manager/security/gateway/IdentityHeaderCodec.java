package com.middleware.manager.security.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 身份头值编解码。
 *
 * <p>网关注入的身份头（用户名、显示名、岗位分类）可能含非 ASCII 字符（如中文 displayName、
 * category）。HTTP 头按 ISO-8859-1 传输，中文原样放入会在网关(Netty)→下游(Tomcat)之间被
 * 破坏，导致下游读到的值与网关签名时的值不一致、HMAC 验签失败、所有受保护接口返回 401。
 *
 * <p>因此这些值统一用 Base64(URL-safe, 无填充) 编码为纯 ASCII 后再进头并参与签名；
 * 下游验签后再解码回原值。roles 为 {@code ROLE_*} 逗号串、categoryAdmin 为布尔串，均为
 * ASCII，不编码。
 */
public final class IdentityHeaderCodec {

    private IdentityHeaderCodec() {
    }

    /** 原值 → 头安全的 Base64(URL-safe)；null 视为空串。 */
    public static String encode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Base64(URL-safe) → 原值；空或非法编码返回空串（验签已保证来源可信，这里仅防御）。 */
    public static String decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return "";
        }
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
