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
    @DisplayName("TC-GATEWAY-001 默认 profile 使用静态路由且关闭 Nacos")
    void defaultProfileUsesStaticRouteWithNacosDisabled() {
        RouteDefinition route = findAppRoute();

        assertThat(route.getUri()).isEqualTo(URI.create("http://127.0.0.1:8081"));
        assertThat(route.getPredicates()).singleElement().satisfies(predicate -> {
            assertThat(predicate.getName()).isEqualTo("Path");
            assertThat(predicate.getArgs()).containsValues("/api/**", "/files/**");
        });
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("spring.cloud.nacos.config.enabled", Boolean.class))
                .isFalse();
    }

    private RouteDefinition findAppRoute() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block(Duration.ofSeconds(5));
        assertThat(routes).isNotNull();
        return routes.stream()
                .filter(route -> "app-api".equals(route.getId()))
                .findFirst()
                .orElseThrow();
    }
}
