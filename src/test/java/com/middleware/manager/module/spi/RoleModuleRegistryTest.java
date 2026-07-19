package com.middleware.manager.module.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.middleware.manager.module.database.DatabaseModule;
import com.middleware.manager.module.host.HostModule;
import com.middleware.manager.module.middleware.MiddlewareModule;
import com.middleware.manager.module.network.NetworkModule;
import com.middleware.manager.module.security.SecurityRoleModule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TC-06：五个岗位模块具备清晰独立的代码边界（各自独立的 module 包 + RoleModule 壳），
 * 默认接入门户后端；单个岗位模块接口配置变化不影响其他岗位。
 */
class RoleModuleRegistryTest {

    private RoleModuleRegistry registry(String middleware, String database, String host,
                                        String network, String security) {
        return new RoleModuleRegistry(List.of(
                new MiddlewareModule(middleware),
                new DatabaseModule(database),
                new HostModule(host),
                new NetworkModule(network),
                new SecurityRoleModule(security)));
    }

    @Test
    void TC_06_五岗位模块独立注册且默认接入门户后端() {
        RoleModuleRegistry reg = registry("/api", "/api", "/api", "/api", "/api");

        // 五大岗位模块齐全，按门户约定顺序，数据范围（category）与契约一致
        assertThat(reg.ordered()).hasSize(5);
        assertThat(reg.ordered().stream().map(RoleModule::roleId))
                .containsExactly("middleware", "database", "host", "network", "security");
        assertThat(reg.ordered().stream().map(RoleModule::category))
                .containsExactly("中间件", "数据库", "主机", "网络", "安全");
        // 默认全部接入门户后端 /api
        assertThat(reg.ordered()).allMatch(m -> "/api".equals(m.apiBase()));
    }

    @Test
    void TC_06_单个岗位后端配置变化不影响其他岗位() {
        // 仅数据库岗位指向独立后端服务，其余仍使用门户后端
        RoleModuleRegistry reg = registry("/api", "http://db-svc:9000/api", "/api", "/api", "/api");

        assertThat(reg.get("database").apiBase()).isEqualTo("http://db-svc:9000/api");
        assertThat(reg.get("middleware").apiBase()).isEqualTo("/api");
        assertThat(reg.get("host").apiBase()).isEqualTo("/api");
        assertThat(reg.get("network").apiBase()).isEqualTo("/api");
        assertThat(reg.get("security").apiBase()).isEqualTo("/api");
    }

    @Test
    void TC_06_接入点空白时回退门户后端() {
        RoleModuleRegistry reg = registry("  ", null, "", "/api", "/api");

        assertThat(reg.get("middleware").apiBase()).isEqualTo("/api");
        assertThat(reg.get("database").apiBase()).isEqualTo("/api");
        assertThat(reg.get("host").apiBase()).isEqualTo("/api");
    }
}
