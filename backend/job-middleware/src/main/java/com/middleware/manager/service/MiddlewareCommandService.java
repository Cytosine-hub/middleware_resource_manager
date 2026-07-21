package com.middleware.manager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.MiddlewareCommand;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.MiddlewareCommandMapper;
import com.middleware.manager.web.api.dto.MiddlewareCommandImportResult;
import com.middleware.manager.web.api.dto.MiddlewareCommandTransferItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class MiddlewareCommandService {
    private static final TypeReference<List<String>> CATEGORY_LIST = new TypeReference<>() { };

    private final SoftwareTypeLookup softwareTypeLookup;
    private final MiddlewareCommandMapper commandMapper;
    private final ObjectMapper objectMapper;

    public MiddlewareCommandService(SoftwareTypeLookup softwareTypeLookup,
                                    MiddlewareCommandMapper commandMapper,
                                    ObjectMapper objectMapper) {
        this.softwareTypeLookup = softwareTypeLookup;
        this.commandMapper = commandMapper;
        this.objectMapper = objectMapper;
    }

    public List<SoftwareType> listTypes() {
        return softwareTypeLookup.findByIds(commandMapper.findDistinctSoftwareTypeIds());
    }

    public List<MiddlewareCommand> listCommands(Long softwareTypeId) {
        if (softwareTypeId != null) {
            return commandMapper.findBySoftwareTypeIdOrderBySortOrderAsc(softwareTypeId);
        }
        return commandMapper.findAllByOrderBySoftwareTypeIdAscSortOrderAsc();
    }

    public List<MiddlewareCommand> listCommandsByCategory(String category) {
        List<Long> ids = softwareTypeLookup.findByCategory(category).stream()
                .map(SoftwareType::getId)
                .toList();
        return ids.isEmpty() ? List.of() : commandMapper.findBySoftwareTypeIdsOrderBySortOrderAsc(ids);
    }

    public SoftwareType getSoftwareType(Long id) {
        return softwareTypeLookup.get(id);
    }

    @Transactional
    public MiddlewareCommand create(Long softwareTypeId, String commandFormat, String briefDescription,
                                    String detailedDescription, String categories, int sortOrder) {
        SoftwareType type = softwareTypeLookup.get(softwareTypeId);
        MiddlewareCommand command = newCommand(type.getId(), commandFormat, briefDescription,
                detailedDescription, categories, sortOrder);
        commandMapper.insert(command);
        log.info("命令已创建 id={}", command.getId());
        return command;
    }

    @Transactional
    public MiddlewareCommand update(Long id, Long softwareTypeId, String commandFormat,
                                    String briefDescription, String detailedDescription,
                                    String categories, int sortOrder) {
        MiddlewareCommand command = get(id);
        SoftwareType type = softwareTypeLookup.get(softwareTypeId);
        apply(command, type.getId(), commandFormat, briefDescription,
                detailedDescription, categories, sortOrder);
        commandMapper.update(command);
        log.info("命令已更新 id={}", id);
        return command;
    }

    public MiddlewareCommand get(Long id) {
        MiddlewareCommand command = commandMapper.findById(id);
        if (command == null) {
            throw new NotFoundException(ErrorCode.COMMAND_NOT_FOUND, ErrorMessages.COMMAND_NOT_FOUND);
        }
        return command;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        commandMapper.deleteById(id);
        log.info("命令已删除 id={}", id);
    }

    public List<MiddlewareCommandTransferItem> exportCommands() {
        List<MiddlewareCommand> commands = commandMapper.findAllByOrderBySoftwareTypeIdAscSortOrderAsc();
        List<Long> ids = commands.stream().map(MiddlewareCommand::getSoftwareTypeId).distinct().toList();
        Map<Long, SoftwareType> types = new HashMap<>();
        softwareTypeLookup.findByIds(ids).forEach(type -> types.put(type.getId(), type));
        return java.util.stream.IntStream.range(0, commands.size())
                .mapToObj(index -> toTransferItem(commands.get(index), types, index))
                .toList();
    }

    @Transactional
    public MiddlewareCommandImportResult importCommands(List<MiddlewareCommandTransferItem> items) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (int index = 0; index < items.size(); index++) {
            ImportAction action = importItem(items.get(index), index);
            created += action == ImportAction.CREATED ? 1 : 0;
            updated += action == ImportAction.UPDATED ? 1 : 0;
            skipped += action == ImportAction.SKIPPED ? 1 : 0;
        }
        log.info("命令导入完成 total={} created={} updated={} skipped={}",
                items.size(), created, updated, skipped);
        return new MiddlewareCommandImportResult(items.size(), created, updated, skipped);
    }

    private ImportAction importItem(MiddlewareCommandTransferItem item, int index) {
        try {
            String category = required(item == null ? null : item.getCategoryName());
            String typeName = required(item.getSoftwareTypeName());
            String commandFormat = required(item.getCommandFormat());
            SoftwareType type = softwareTypeLookup.resolveOrCreate(category, typeName);
            String categories = writeCategories(item.getCategories());
            MiddlewareCommand existing = commandMapper.findBySoftwareTypeIdAndCommandFormat(
                    type.getId(), commandFormat);
            if (existing == null) {
                commandMapper.insert(newCommand(type.getId(), commandFormat,
                        item.getBriefDescription(), item.getDetailedDescription(),
                        categories, item.getSortOrder()));
                return ImportAction.CREATED;
            }
            if (sameContent(existing, item, categories)) {
                return ImportAction.SKIPPED;
            }
            apply(existing, type.getId(), commandFormat, item.getBriefDescription(),
                    item.getDetailedDescription(), categories, item.getSortOrder());
            commandMapper.update(existing);
            return ImportAction.UPDATED;
        } catch (BusinessException exception) {
            throw importFailure(index, exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("命令导入单条失败 index={} reason={}", index + 1, exception.getMessage());
            throw importFailure(index, ErrorMessages.UNKNOWN_ERROR);
        }
    }

    private MiddlewareCommandTransferItem toTransferItem(MiddlewareCommand command,
                                                          Map<Long, SoftwareType> types,
                                                          int index) {
        SoftwareType type = types.get(command.getSoftwareTypeId());
        if (type == null) {
            throw new BusinessException(ErrorCode.COMMAND_EXPORT_FAILED,
                    ErrorMessages.COMMAND_EXPORT_FAILED, "第 " + (index + 1) + " 条命令的软件类型不存在");
        }
        return new MiddlewareCommandTransferItem(
                type.getCategory(), type.getName(), command.getCommandFormat(),
                command.getBriefDescription(), command.getDetailedDescription(),
                readCategories(command.getCategories(), index), command.getSortOrder());
    }

    private List<String> readCategories(String categories, int index) {
        if (!StringUtils.hasText(categories)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(categories, CATEGORY_LIST);
            return values == null ? List.of() : values;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.COMMAND_EXPORT_FAILED,
                    ErrorMessages.COMMAND_EXPORT_FAILED, "第 " + (index + 1) + " 条命令的标签不是 JSON 数组");
        }
    }

    private String writeCategories(List<String> categories) {
        List<String> normalized = categories == null ? List.of() : categories.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        try {
            return normalized.isEmpty() ? null : objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.COMMAND_IMPORT_FAILED, ErrorMessages.COMMAND_IMPORT_FAILED);
        }
    }

    private MiddlewareCommand newCommand(Long softwareTypeId, String commandFormat,
                                         String briefDescription, String detailedDescription,
                                         String categories, int sortOrder) {
        MiddlewareCommand command = new MiddlewareCommand();
        apply(command, softwareTypeId, commandFormat, briefDescription,
                detailedDescription, categories, sortOrder);
        return command;
    }

    private void apply(MiddlewareCommand command, Long softwareTypeId, String commandFormat,
                       String briefDescription, String detailedDescription,
                       String categories, int sortOrder) {
        String normalizedFormat = required(commandFormat);
        command.setSoftwareTypeId(softwareTypeId);
        command.setCommandFormat(normalizedFormat);
        command.setName(briefDescription);
        command.setCommand(normalizedFormat);
        command.setBriefDescription(briefDescription);
        command.setDetailedDescription(detailedDescription);
        command.setCategories(categories);
        command.setSortOrder(sortOrder);
    }

    private boolean sameContent(MiddlewareCommand existing, MiddlewareCommandTransferItem item,
                                String categories) {
        return Objects.equals(existing.getBriefDescription(), item.getBriefDescription())
                && Objects.equals(existing.getDetailedDescription(), item.getDetailedDescription())
                && Objects.equals(canonicalCategories(existing.getCategories()), categories)
                && existing.getSortOrder() == item.getSortOrder();
    }

    private String canonicalCategories(String categories) {
        if (!StringUtils.hasText(categories)) {
            return null;
        }
        try {
            return writeCategories(objectMapper.readValue(categories, CATEGORY_LIST));
        } catch (JsonProcessingException exception) {
            return categories;
        }
    }

    private String required(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.PARAM_INVALID);
        }
        return value.trim();
    }

    private BusinessException importFailure(int index, String reason) {
        return new BusinessException(ErrorCode.COMMAND_IMPORT_FAILED,
                ErrorMessages.COMMAND_IMPORT_FAILED, "第 " + (index + 1) + " 条：" + reason);
    }

    private enum ImportAction {
        CREATED,
        UPDATED,
        SKIPPED
    }
}
