package com.middleware.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.middleware.manager.config.ApiAuditLogger;
import com.middleware.manager.service.AdminAccountService;
import com.middleware.manager.service.RoleService;
import com.middleware.manager.service.SystemSettingService;
import com.middleware.manager.service.TokenService;
import com.middleware.manager.web.api.AdminAccountApiController;
import com.middleware.manager.web.api.AdminParameterStandardController;
import com.middleware.manager.web.api.AdminReleaseApiController;
import com.middleware.manager.web.api.AdminSettingController;
import com.middleware.manager.web.api.AdminSoftwareCategoryApiController;
import com.middleware.manager.web.api.AdminSoftwareTypeApiController;
import com.middleware.manager.web.api.AdminStandardDocumentApiController;
import com.middleware.manager.web.api.AdminStandardParameterApiController;
import com.middleware.manager.web.api.AdminUserController;
import com.middleware.manager.web.api.AuthApiController;
import com.middleware.manager.web.api.DocumentRevisionController;
import com.middleware.manager.web.api.ImageController;
import com.middleware.manager.web.api.PublicConfigController;
import com.middleware.manager.web.api.PublicParameterStandardController;
import com.middleware.manager.web.api.PublicReleaseApiController;
import com.middleware.manager.web.api.PublicStandardDocumentApiController;
import com.middleware.manager.web.api.PublicStandardParameterApiController;
import com.middleware.manager.web.api.ReviewApiController;
import com.middleware.manager.web.controller.FileDownloadController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(classes = CoreServiceApplication.class)
@AutoConfigureMockMvc
class CoreServiceApplicationTests {

    private static final List<Class<?>> CORE_CONTROLLERS = List.of(
            AdminAccountApiController.class,
            AdminParameterStandardController.class,
            AdminReleaseApiController.class,
            AdminSettingController.class,
            AdminSoftwareCategoryApiController.class,
            AdminSoftwareTypeApiController.class,
            AdminStandardDocumentApiController.class,
            AdminStandardParameterApiController.class,
            AdminUserController.class,
            AuthApiController.class,
            DocumentRevisionController.class,
            FileDownloadController.class,
            ImageController.class,
            PublicConfigController.class,
            PublicParameterStandardController.class,
            PublicReleaseApiController.class,
            PublicStandardDocumentApiController.class,
            PublicStandardParameterApiController.class,
            ReviewApiController.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private SystemSettingService systemSettingService;

    @MockitoBean
    private AdminAccountService adminAccountService;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private ApiAuditLogger apiAuditLogger;

    @Test
    @DisplayName("TC-CORE-001 默认 profile 独立加载平台核心且关闭 Nacos")
    void defaultProfileLoadsCoreWithNacosDisabled() {
        CORE_CONTROLLERS.forEach(controller ->
                assertThat(applicationContext.getBean(controller)).isNotNull());
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8084);
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("core-service");
        assertThat(environment.getProperty(
                "spring.cloud.nacos.discovery.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty(
                "spring.cloud.nacos.config.enabled", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("TC-CORE-002 平台核心保留 78 个既有端点映射")
    void coreEndpointMappingsRemainComplete() {
        long endpointCount = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> CORE_CONTROLLERS.contains(entry.getValue().getBeanType()))
                .mapToLong(entry -> entry.getKey().getPatternValues().size())
                .sum();

        assertThat(endpointCount).isEqualTo(78);
    }

    @Test
    @DisplayName("TC-CORE-003 public 与登录路径保持公开")
    void publicAndLoginPathsRemainPublic() throws Exception {
        when(adminAccountService.loadUserByUsername("alice"))
                .thenReturn(User.withUsername("alice").password("{noop}secret")
                        .authorities("ROLE_SYS_ADMIN").build());
        when(adminAccountService.getDisplayNameByUsername("alice")).thenReturn("Alice");
        when(tokenService.createToken("alice")).thenReturn("test-token");

        mockMvc.perform(get("/api/public/config"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Basic YWxpY2U6c2VjcmV0"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-CORE-004 auth 与 admin 路径保持 Bearer Token 鉴权")
    void protectedCorePathsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/releases"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/parameter-standards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-CORE-005 core-service 类路径不包含岗位、论坛和 AI Controller")
    void coreClasspathContainsOnlyCoreDomains() {
        ClassLoader classLoader = CoreServiceApplicationTests.class.getClassLoader();

        assertThat(classLoader.getResource(
                "com/middleware/manager/web/api/MiddlewareCommandApiController.class")).isNull();
        assertThat(classLoader.getResource(
                "com/middleware/manager/web/api/ForumController.class")).isNull();
        assertThat(classLoader.getResource(
                "com/middleware/manager/knowledge/web/KnowledgeController.class")).isNull();
        assertThat(classLoader.getResource(
                "com/middleware/manager/wiki/web/WikiController.class")).isNull();
        assertThat(classLoader.getResource(
                "com/middleware/manager/agent/web/AgentController.class")).isNull();
    }
}
