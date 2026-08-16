package com.invsys;

import com.invsys.config.IntegrationProperties;
import com.invsys.config.JwtProperties;
import com.invsys.integration.easypost.EasyPostProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bootable entrypoint. Core packages are always scanned; Support Co-Pilot classes are loaded
 * only via {@code invsys-chatbot} auto-configuration when that module is on the classpath
 * and {@code invsys.features.chatbot.enabled=true}.
 */
@Modulith(systemName = "weGrowStock", additionalPackages = "com.invsys.modules")
@SpringBootApplication(exclude = {
        DataRedisAutoConfiguration.class,
        DataRedisReactiveAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class
})
@ComponentScan(
        basePackages = "com.invsys",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.invsys\\.support\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.invsys\\.api\\.SupportChatController"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.invsys\\.admin\\..*")
        }
)
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy
@EnableConfigurationProperties({JwtProperties.class, IntegrationProperties.class, EasyPostProperties.class})
public class InvSysApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvSysApplication.class, args);
    }
}
