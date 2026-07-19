package com.middleware.manager.module.common.command;

import com.middleware.manager.module.common.PortalRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 常用命令的业务逻辑——按岗位（category）隔离的可复用能力。
 *
 * <p>各岗位模块共用本服务，仅通过 category 区分数据范围，避免重复实现列表/筛选/详情等核心逻辑。
 */
@Service
public class CommonCommandService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CommonCommandRepository repository;

    public CommonCommandService(CommonCommandRepository repository) {
        this.repository = repository;
    }

    /**
     * 按岗位分类 + 关键字分页查询常用命令。
     *
     * @param category 岗位数据范围；为空表示全部岗位
     * @param keyword  标题/命令/说明模糊匹配；为空表示不限
     */
    public Page<CommonCommand> list(String category, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        return repository.filter(trimToNull(category), trimToNull(keyword), pageable);
    }

    public CommonCommand get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("常用命令不存在"));
    }

    @Transactional
    public CommonCommand create(String category, String title, String command, String description, String tag) {
        CommonCommand entity = new CommonCommand();
        applyFields(entity, category, title, command, description, tag);
        return repository.save(entity);
    }

    @Transactional
    public CommonCommand update(Long id, String category, String title, String command, String description, String tag) {
        CommonCommand entity = get(id);
        applyFields(entity, category, title, command, description, tag);
        return repository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(get(id));
    }

    private void applyFields(CommonCommand entity, String category, String title,
                             String command, String description, String tag) {
        String normalizedCategory = trimToNull(category);
        if (normalizedCategory == null) {
            throw new IllegalArgumentException("岗位分类不能为空");
        }
        if (!PortalRole.isRoleCategory(normalizedCategory)) {
            throw new IllegalArgumentException("非法岗位分类: " + normalizedCategory);
        }
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("命令标题不能为空");
        }
        if (!StringUtils.hasText(command)) {
            throw new IllegalArgumentException("命令内容不能为空");
        }
        entity.setCategory(normalizedCategory);
        entity.setTitle(title.trim());
        entity.setCommand(command.trim());
        entity.setDescription(trimToNull(description));
        entity.setTag(trimToNull(tag));
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
