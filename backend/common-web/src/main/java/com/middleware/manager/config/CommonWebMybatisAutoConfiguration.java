package com.middleware.manager.config;

import java.io.IOException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@AutoConfiguration(before = MybatisAutoConfiguration.class)
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
public class CommonWebMybatisAutoConfiguration {

    private static final String AUDIT_MAPPER_LOCATION =
            "classpath*:mapper/ApiAuditLogMapper.xml";

    @Bean
    public SqlSessionFactoryBeanCustomizer apiAuditLogMapperLocationCustomizer(
            ResourcePatternResolver resourcePatternResolver) throws IOException {
        Resource[] auditMapperResources =
                resourcePatternResolver.getResources(AUDIT_MAPPER_LOCATION);
        return factoryBean -> factoryBean.addMapperLocations(auditMapperResources);
    }
}
