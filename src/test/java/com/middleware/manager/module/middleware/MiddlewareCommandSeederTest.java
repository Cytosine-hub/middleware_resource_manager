package com.middleware.manager.module.middleware;

import static org.assertj.core.api.Assertions.assertThat;

import com.middleware.manager.module.common.command.CommonCommand;
import com.middleware.manager.module.common.command.CommonCommandRepository;
import com.middleware.manager.module.common.command.CommonCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * TC-05：原门户「常用命令」迁移到中间件岗位模块。
 * 验证真实的程序化回填逻辑：全新库回填后历史内置命令完整归入中间件岗位、可查询/查看详情/关键字检索；
 * 且回填幂等（重复运行不产生重复数据），可安全在每次启动执行。
 *
 * <p>生产由 {@link MiddlewareCommandSeeder}（ApplicationRunner）自动回填；测试 profile 下该 Bean 关闭
 * 自动启动（{@code @Profile("!test")}，避免污染共享内存库），故此处直接构造 seeder 验证回填逻辑本身。
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommonCommandService.class)
class MiddlewareCommandSeederTest {

    @Autowired
    private CommonCommandService service;

    @Autowired
    private CommonCommandRepository repository;

    private MiddlewareCommandSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new MiddlewareCommandSeeder(repository, service);
    }

    @Test
    void TC_05_全新库回填后中间件岗位历史常用命令完整可查询与查看详情() {
        // @DataJpaTest 事务隔离 + 测试 profile 下 seeder 不自动运行：起点为空
        assertThat(repository.count()).isZero();

        int inserted = seeder.backfill();
        assertThat(inserted).isEqualTo(MiddlewareCommandSeeder.LEGACY_MIDDLEWARE_COMMANDS.size());

        // 历史数据完整展示：全部归属中间件岗位（category=中间件），可按岗位查询
        var page = service.list("中间件", "", 0, 50);
        assertThat(page.getTotalElements()).isEqualTo(MiddlewareCommandSeeder.LEGACY_MIDDLEWARE_COMMANDS.size());
        assertThat(page.getContent()).allMatch(c -> "中间件".equals(c.getCategory()));

        // 查看详情 + 关键字查询等已有能力可用
        CommonCommand nginx = page.getContent().stream()
                .filter(c -> "查看Nginx进程".equals(c.getTitle())).findFirst().orElseThrow();
        assertThat(service.get(nginx.getId()).getCommand()).isEqualTo("ps -ef | grep nginx");
        assertThat(service.list("中间件", "reload", 0, 50).getTotalElements()).isEqualTo(1);
    }

    @Test
    void TC_05_回填幂等重复执行不产生重复历史数据() {
        seeder.backfill();
        long afterFirst = repository.count();

        // 再次启动回填：已存在则跳过，不新增
        int insertedAgain = seeder.backfill();
        assertThat(insertedAgain).isZero();
        assertThat(repository.count()).isEqualTo(afterFirst);
    }
}
