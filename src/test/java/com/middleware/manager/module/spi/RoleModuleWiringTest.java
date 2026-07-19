package com.middleware.manager.module.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.middleware.manager.module.database.DatabaseModule;
import com.middleware.manager.module.host.HostModule;
import com.middleware.manager.module.middleware.MiddlewareModule;
import com.middleware.manager.module.network.NetworkModule;
import com.middleware.manager.module.security.SecurityRoleModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * TC-06：岗位模块接口接入点由各自独立的配置键（app.modules.&lt;角色&gt;.api-base）注入，
 * 只覆盖某一岗位的配置不会影响其他岗位——证明配置层面的独立性。
 */
@SpringBootTest(classes = {
        MiddlewareModule.class,
        DatabaseModule.class,
        HostModule.class,
        NetworkModule.class,
        SecurityRoleModule.class,
        RoleModuleRegistry.class
})
@TestPropertySource(properties = "app.modules.network.api-base=http://net-svc:8090/api")
class RoleModuleWiringTest {

    @Autowired
    private RoleModuleRegistry registry;

    @Test
    void TC_06_仅覆盖网络岗位配置其余岗位仍接入门户后端() {
        assertThat(registry.ordered()).hasSize(5);
        assertThat(registry.get("network").apiBase()).isEqualTo("http://net-svc:8090/api");
        assertThat(registry.get("middleware").apiBase()).isEqualTo("/api");
        assertThat(registry.get("database").apiBase()).isEqualTo("/api");
        assertThat(registry.get("host").apiBase()).isEqualTo("/api");
        assertThat(registry.get("security").apiBase()).isEqualTo("/api");
    }
}
