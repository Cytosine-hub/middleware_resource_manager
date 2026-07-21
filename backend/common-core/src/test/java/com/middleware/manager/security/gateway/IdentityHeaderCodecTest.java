package com.middleware.manager.security.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityHeaderCodecTest {

    @Test
    @DisplayName("TC-CODEC-001 中文编码为纯 ASCII 且可逆")
    void chineseRoundTripsAsAscii() {
        String raw = "系统管理员·中间件";
        String encoded = IdentityHeaderCodec.encode(raw);

        // 编码结果必须是纯 ASCII，才能安全通过 ISO-8859-1 的 HTTP 头传输
        assertThat(encoded).isEqualTo(new String(encoded.getBytes(StandardCharsets.ISO_8859_1),
                StandardCharsets.ISO_8859_1));
        assertThat(encoded.chars().allMatch(c -> c < 128)).isTrue();
        assertThat(IdentityHeaderCodec.decode(encoded)).isEqualTo(raw);
    }

    @Test
    @DisplayName("TC-CODEC-002 空与 null 稳健处理")
    void nullAndEmptyAreSafe() {
        assertThat(IdentityHeaderCodec.encode(null)).isEmpty();
        assertThat(IdentityHeaderCodec.encode("")).isEmpty();
        assertThat(IdentityHeaderCodec.decode(null)).isEmpty();
        assertThat(IdentityHeaderCodec.decode("")).isEmpty();
    }
}
