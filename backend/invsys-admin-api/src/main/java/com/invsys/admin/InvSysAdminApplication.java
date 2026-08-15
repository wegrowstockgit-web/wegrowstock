package com.invsys.admin;

import com.invsys.config.IntegrationProperties;
import com.invsys.config.JwtProperties;
import com.invsys.core.security.SecurityConfig;
import com.invsys.integration.easypost.EasyPostProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        DataRedisAutoConfiguration.class,
        DataRedisReactiveAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class
})
@EntityScan(basePackages = "com.invsys")
@EnableJpaRepositories(basePackages = "com.invsys")
@ComponentScan(
        basePackages = "com.invsys",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.invsys\\.support\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.invsys\\.api\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.invsys\\.api\\.SupportChatController"),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy
@EnableConfigurationProperties({JwtProperties.class, IntegrationProperties.class, EasyPostProperties.class})
public class InvSysAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvSysAdminApplication.class, args);
    }
}
