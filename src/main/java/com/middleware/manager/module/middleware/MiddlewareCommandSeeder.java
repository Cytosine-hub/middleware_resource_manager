package com.middleware.manager.module.middleware;

import com.middleware.manager.module.common.PortalRole;
import com.middleware.manager.module.common.command.CommonCommand;
import com.middleware.manager.module.common.command.CommonCommandRepository;
import com.middleware.manager.module.common.command.CommonCommandService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 中间件岗位「常用命令」数据迁移/回填器（TC-05）。
 *
 * <p>需求 8：原门户「常用命令」迁移到中间件岗位模块。历史内置命令在此以中间件岗位（category=中间件）
 * 为归属，应用启动时**程序化幂等回填**到 {@code common_commands}——而不是依赖仅需手动执行的 DDL 样例，
 * 保证「历史数据完整展示」在任意环境（含全新库）都成立。已存在（按 category+title）则跳过，可安全重复运行。
 *
 * <p>该回填数据属于中间件岗位、放在中间件独立包内（见 agent.md §4b「岗位模块编码独立」），
 * 复用 common 的 {@link CommonCommandService} 通用能力写入，不与其他岗位耦合。
 *
 * <p>{@code @Profile("!test")}：测试 profile 下不自动启动回填，避免向共享的 H2 内存库
 * （{@code DB_CLOSE_DELAY=-1}）提交数据污染其它切片测试；回填逻辑本身由单测直接构造并调用 {@link #backfill()} 验证。
 */
@Component
@Profile("!test")
public class MiddlewareCommandSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareCommandSeeder.class);

    /** 原门户「常用命令」历史内置数据（迁移到中间件岗位）：标题 / 命令 / 说明 / 标签。 */
    static final List<String[]> LEGACY_MIDDLEWARE_COMMANDS = List.of(
            new String[] {"查看Nginx进程", "ps -ef | grep nginx", "定位 nginx 主/工作进程", "nginx"},
            new String[] {"重载Nginx配置", "nginx -t && nginx -s reload", "校验并热加载配置，不中断服务", "nginx"},
            new String[] {"查看Tomcat端口", "netstat -anp | grep 8080", "确认 Tomcat 监听端口与连接", "tomcat"});

    private final CommonCommandRepository repository;
    private final CommonCommandService service;

    public MiddlewareCommandSeeder(CommonCommandRepository repository, CommonCommandService service) {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        int inserted = backfill();
        if (inserted > 0) {
            log.info("中间件岗位常用命令回填完成，新增 {} 条", inserted);
        }
    }

    /**
     * 幂等回填中间件岗位历史常用命令。
     *
     * @return 本次实际新增的条数（已存在的跳过）
     */
    int backfill() {
        String category = PortalRole.MIDDLEWARE.getCategory();
        int inserted = 0;
        for (String[] c : LEGACY_MIDDLEWARE_COMMANDS) {
            String title = c[0];
            if (!repository.existsByCategoryAndTitle(category, title)) {
                CommonCommand saved = service.create(category, title, c[1], c[2], c[3]);
                if (saved.getId() != null) {
                    inserted++;
                }
            }
        }
        return inserted;
    }
}
