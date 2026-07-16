package com.invsys;

import com.invsys.config.IntegrationProperties;
import com.invsys.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        DataRedisAutoConfiguration.class,
        DataRedisReactiveAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy
@EnableConfigurationProperties({JwtProperties.class, IntegrationProperties.class})
public class InvSysApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvSysApplication.class, args);
    }
}
