package com.middleware.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("cloud")
@SpringBootTest(properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false"
})
class ApiGatewayCloudRouteTests {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    @DisplayName("TC-GATEWAY-002 cloud profile 经服务发现动态路由到 app")
    void cloudProfileUsesLoadBalancedAppRoute() {
        URI appRouteUri = routeDefinitionLocator.getRouteDefinitions()
                .filter(route -> "app-api".equals(route.getId()))
                .single()
                .map(route -> route.getUri())
                .block(Duration.ofSeconds(5));

        assertThat(appRouteUri).isEqualTo(URI.create("lb://middleware-resource-manager-app"));
    }
}
