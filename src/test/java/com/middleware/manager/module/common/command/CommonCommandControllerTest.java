package com.middleware.manager.module.common.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.module.common.command.dto.CommonCommandRequest;
import com.middleware.manager.security.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * TC-06：岗位模块 API（common/command）虽在 module 独立包下，仍必须纳入门户统一异常处理（ApiExceptionHandler）
 * 覆盖范围，越权/参数错误按项目规范统一转成 API 错误响应，而非默认 500 或不一致响应。
 * TC-07：常用命令是可复用的通用能力，其写接口沿用核心 category 限权。
 */
@WebMvcTest(CommonCommandController.class)
@AutoConfigureMockMvc(addFilters = false)
class CommonCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommonCommandService service;

    @MockBean
    private PermissionService permissionService;

    @Test
    void TC_06_越权维护他岗常用命令返回统一400错误体而非500() throws Exception {
        // 无对应岗位权限：canManageCategory 返回 false，控制器抛 IllegalArgumentException
        when(permissionService.canManageCategory(any(), any())).thenReturn(false);

        CommonCommandRequest req = new CommonCommandRequest();
        req.setCategory("网络");
        req.setTitle("查看端口");
        req.setCommand("ss -lntp");

        mockMvc.perform(post("/api/module/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                // 若模块 API 未纳入 ApiExceptionHandler 覆盖，将得到 500；纳入后应为统一 400 错误体
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("无权维护该岗位的常用命令"));
    }

    @Test
    void TC_06_模块API参数校验失败也走门户统一校验响应() throws Exception {
        // category 缺失触发 @Valid 校验失败，应由统一异常处理转 400 校验响应
        CommonCommandRequest req = new CommonCommandRequest();
        req.setTitle("缺少岗位");
        req.setCommand("echo hi");

        mockMvc.perform(post("/api/module/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}
