package com.middleware.manager.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.security.gateway.GatewayIdentityHeaders;
import com.middleware.manager.security.gateway.GatewaySignatureService;
import com.middleware.manager.service.CatalogSoftwareTypeProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RemoteSoftwareTypeLookupTest {
    private static final String SECRET = "test-only-gateway-signing-secret";

    @Test
    @DisplayName("TC-COMMAND-003 远程 SoftwareTypeLookup 按名解析并携带服务间 HMAC")
    void resolveOrCreateCallsCatalogWithSignedNames() {
        GatewaySignatureService signatureService = new GatewaySignatureService(SECRET);
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String payload = CatalogSoftwareTypeProtocol.resolvePayload("中间件", "Redis");
        server.expect(requestTo("http://core-service/api/internal/catalog/software-types/resolve"))
                .andExpect(header(GatewayIdentityHeaders.SIGNATURE,
                        signatureService.signInternalRequest(
                                CatalogSoftwareTypeProtocol.RESOLVE_OPERATION, payload)))
                .andExpect(jsonPath("$.categoryName").value("中间件"))
                .andExpect(jsonPath("$.softwareTypeName").value("Redis"))
                .andRespond(withSuccess("""
                        {"id":88,"category":"中间件","name":"Redis","active":true}
                        """, MediaType.APPLICATION_JSON));
        RemoteSoftwareTypeLookup lookup = new RemoteSoftwareTypeLookup(
                builder.build(), signatureService);

        SoftwareType result = lookup.resolveOrCreate("中间件", "Redis");

        assertThat(result.getId()).isEqualTo(88L);
        assertThat(result.getCategory()).isEqualTo("中间件");
        assertThat(result.getName()).isEqualTo("Redis");
        server.verify();
    }
}
