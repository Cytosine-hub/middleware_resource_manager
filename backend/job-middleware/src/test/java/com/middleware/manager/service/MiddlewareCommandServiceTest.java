package com.middleware.manager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.domain.MiddlewareCommand;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.repository.MiddlewareCommandMapper;
import com.middleware.manager.web.api.dto.MiddlewareCommandImportResult;
import com.middleware.manager.web.api.dto.MiddlewareCommandTransferItem;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MiddlewareCommandServiceTest {

    @Test
    @DisplayName("TC-COMMAND-001 导出不含 ID 且可在不同自增 ID 环境幂等往返")
    void exportThenImportUsesNamesAndDoesNotCreateDuplicates() throws Exception {
        MiddlewareCommand source = command(3L, 11L, "redis-cli INFO", "查看信息", "[\"查询\"]");
        SoftwareType sourceType = softwareType(11L, "中间件", "Redis");
        MiddlewareCommandMapper sourceMapper = mock(MiddlewareCommandMapper.class);
        SoftwareTypeLookup sourceLookup = mock(SoftwareTypeLookup.class);
        when(sourceMapper.findAllByOrderBySoftwareTypeIdAscSortOrderAsc()).thenReturn(List.of(source));
        when(sourceLookup.findByIds(List.of(11L))).thenReturn(List.of(sourceType));
        ObjectMapper objectMapper = new ObjectMapper();
        MiddlewareCommandService sourceService = new MiddlewareCommandService(
                sourceLookup, sourceMapper, objectMapper);

        List<MiddlewareCommandTransferItem> exported = sourceService.exportCommands();

        assertThat(exported).singleElement().satisfies(item -> {
            assertThat(item.getCategoryName()).isEqualTo("中间件");
            assertThat(item.getSoftwareTypeName()).isEqualTo("Redis");
            assertThat(item.getCommandFormat()).isEqualTo("redis-cli INFO");
            assertThat(item.getCategories()).containsExactly("查询");
        });
        String exportedJson = objectMapper.writeValueAsString(exported);
        assertThat(exportedJson)
                .doesNotContain("\"id\"")
                .doesNotContain("\"softwareTypeId\"");

        SoftwareType targetType = softwareType(901L, "中间件", "Redis");
        SoftwareTypeLookup targetLookup = mock(SoftwareTypeLookup.class);
        MiddlewareCommandMapper targetMapper = mock(MiddlewareCommandMapper.class);
        AtomicReference<MiddlewareCommand> stored = new AtomicReference<>();
        when(targetLookup.resolveOrCreate("中间件", "Redis")).thenReturn(targetType);
        when(targetMapper.findBySoftwareTypeIdAndCommandFormat(901L, "redis-cli INFO"))
                .thenAnswer(ignored -> stored.get());
        when(targetMapper.insert(any())).thenAnswer(invocation -> {
            MiddlewareCommand inserted = invocation.getArgument(0);
            inserted.setId(77L);
            stored.set(inserted);
            return 1;
        });
        MiddlewareCommandService targetService = new MiddlewareCommandService(
                targetLookup, targetMapper, new ObjectMapper());

        MiddlewareCommandImportResult first = targetService.importCommands(exported);
        MiddlewareCommandImportResult second = targetService.importCommands(exported);

        assertThat(first.getCreated()).isEqualTo(1);
        assertThat(first.getUpdated()).isZero();
        assertThat(first.getSkipped()).isZero();
        assertThat(second.getCreated()).isZero();
        assertThat(second.getUpdated()).isZero();
        assertThat(second.getSkipped()).isEqualTo(1);
        assertThat(stored.get().getSoftwareTypeId()).isEqualTo(901L);

        exported.get(0).setBriefDescription("查看服务器信息");
        MiddlewareCommandImportResult third = targetService.importCommands(exported);
        assertThat(third.getUpdated()).isEqualTo(1);
        assertThat(stored.get().getBriefDescription()).isEqualTo("查看服务器信息");
    }

    @Test
    @DisplayName("TC-COMMAND-004 导入失败消息包含条目序号和 catalog 解析原因")
    void importFailureIdentifiesItemAndReason() {
        SoftwareTypeLookup lookup = mock(SoftwareTypeLookup.class);
        MiddlewareCommandMapper mapper = mock(MiddlewareCommandMapper.class);
        MiddlewareCommandTransferItem item = new MiddlewareCommandTransferItem(
                "中间件", "Missing", "missing --version", "查看版本", null, List.of(), 0);
        when(lookup.resolveOrCreate("中间件", "Missing")).thenThrow(
                new BusinessException(
                        ErrorCode.SOFTWARE_TYPE_LOOKUP_FAILED,
                        ErrorMessages.SOFTWARE_TYPE_LOOKUP_FAILED));
        MiddlewareCommandService service = new MiddlewareCommandService(
                lookup, mapper, new ObjectMapper());

        assertThatThrownBy(() -> service.importCommands(List.of(item)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第 1 条")
                .hasMessageContaining(ErrorMessages.SOFTWARE_TYPE_LOOKUP_FAILED);
    }

    private MiddlewareCommand command(Long id, Long softwareTypeId, String format,
                                      String brief, String categories) {
        MiddlewareCommand command = new MiddlewareCommand();
        command.setId(id);
        command.setSoftwareTypeId(softwareTypeId);
        command.setCommandFormat(format);
        command.setCommand(format);
        command.setName(brief);
        command.setBriefDescription(brief);
        command.setDetailedDescription("详情");
        command.setCategories(categories);
        command.setSortOrder(2);
        return command;
    }

    private SoftwareType softwareType(Long id, String category, String name) {
        SoftwareType type = new SoftwareType();
        type.setId(id);
        type.setCategory(category);
        type.setName(name);
        type.setActive(true);
        return type;
    }
}
