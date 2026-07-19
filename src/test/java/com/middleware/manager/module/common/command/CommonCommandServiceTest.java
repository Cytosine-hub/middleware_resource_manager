package com.middleware.manager.module.common.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.middleware.manager.module.common.PortalRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * TC-05：常用命令迁移到中间件岗位模块（category=中间件），历史数据与查询/详情等能力可用。
 * TC-07：常用命令为可复用的通用能力——同一套 Service 被多个岗位按 category 复用，核心逻辑不重复实现。
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommonCommandService.class)
class CommonCommandServiceTest {

    @Autowired
    private CommonCommandService service;

    @Test
    void TC_05_常用命令归属中间件模块且历史数据可查询与查看详情() {
        CommonCommand created = service.create("中间件", "查看Nginx进程", "ps -ef | grep nginx", "定位nginx进程", "nginx");
        service.create("中间件", "重载Nginx配置", "nginx -s reload", "热加载配置", "nginx");

        // 中间件岗位下可查询到全部历史命令
        var middleware = service.list("中间件", "", 0, 20);
        assertThat(middleware.getTotalElements()).isEqualTo(2);
        assertThat(middleware.getContent()).allMatch(c -> "中间件".equals(c.getCategory()));

        // 查看详情
        CommonCommand detail = service.get(created.getId());
        assertThat(detail.getTitle()).isEqualTo("查看Nginx进程");
        assertThat(detail.getCommand()).isEqualTo("ps -ef | grep nginx");

        // 关键字查询
        assertThat(service.list("中间件", "reload", 0, 20).getTotalElements()).isEqualTo(1);
    }

    @Test
    void TC_07_同一常用命令能力被多个岗位复用且按岗位隔离() {
        service.create("中间件", "中间件命令", "cmd-mw", null, null);
        service.create("数据库", "数据库命令", "cmd-db", null, null);
        service.create("主机", "主机命令", "cmd-host", null, null);

        // 同一 Service（同一套核心逻辑）服务于不同岗位，按 category 隔离
        assertThat(service.list("中间件", "", 0, 20).getTotalElements()).isEqualTo(1);
        assertThat(service.list("数据库", "", 0, 20).getTotalElements()).isEqualTo(1);
        assertThat(service.list("主机", "", 0, 20).getTotalElements()).isEqualTo(1);
        // 全部岗位
        assertThat(service.list("", "", 0, 20).getTotalElements()).isEqualTo(3);
    }

    @Test
    void TC_04_常用命令空岗位与单条数据的边界处理() {
        service.create("中间件", "唯一命令", "only", null, null);

        // 无数据岗位：返回空分页而非报错
        var empty = service.list("网络", "", 0, 20);
        assertThat(empty.getTotalElements()).isZero();
        assertThat(empty.getContent()).isEmpty();

        // 单条数据岗位：正常返回
        assertThat(service.list("中间件", "", 0, 20).getTotalElements()).isEqualTo(1);
    }

    @Test
    void TC_07_非法岗位分类被拒绝以保证数据一致() {
        assertThatThrownBy(() -> service.create("不存在岗位", "t", "c", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // 五大岗位分类均合法
        for (String category : PortalRole.categories()) {
            assertThat(service.create(category, "t-" + category, "c", null, null).getId()).isNotNull();
        }
    }
}
