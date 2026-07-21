package com.middleware.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.Environment;

@SpringBootTest
class ApiGatewayApplicationTests {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("TC-GATEWAY-001 默认 profile 将论坛和 AI 集群静态路由到独立服务")
    void defaultProfileUsesStaticRouteWithNacosDisabled() {
        RouteDefinition communityRoute = findRoute("community-api");
        RouteDefinition aiRoute = findRoute("ai-api");
        RouteDefinition appRoute = findRoute("app-api");

        assertThat(communityRoute.getUri()).isEqualTo(URI.create("http://127.0.0.1:8082"));
        assertThat(communityRoute.getPredicates()).singleElement().satisfies(predicate -> {
            assertThat(predicate.getName()).isEqualTo("Path");
            assertThat(predicate.getArgs()).containsValue("/api/forum/**");
        });
        assertThat(aiRoute.getUri()).isEqualTo(URI.create("http://127.0.0.1:8083"));
        assertThat(aiRoute.getPredicates()).singleElement().satisfies(predicate -> {
            assertThat(predicate.getName()).isEqualTo("Path");
            assertThat(predicate.getArgs()).containsValues(
                    "/api/knowledge/**",
                    "/api/agent/**",
                    "/api/wiki/**",
                    "/api/ops-agent/**",
                    "/api/ops-agent/export/**");
        });
        assertThat(appRoute.getUri()).isEqualTo(URI.create("http://127.0.0.1:8081"));
        assertThat(appRoute.getPredicates()).singleElement().satisfies(predicate -> {
            assertThat(predicate.getName()).isEqualTo("Path");
            assertThat(predicate.getArgs()).containsValues("/api/**", "/files/**");
        });
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class))
                .isFalse();
    }

    private RouteDefinition findRoute(String routeId) {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block(Duration.ofSeconds(5));
        assertThat(routes).isNotNull();
        return routes.stream()
                .filter(route -> routeId.equals(route.getId()))
                .findFirst()
                .orElseThrow();
    }
}
