package dev.mesbase.server;

import dev.mesbase.common.filter.XssFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SystemRedisConfiguration {
    @Bean("redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        var template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(factory);
        var keys = new StringRedisSerializer();
        template.setKeySerializer(keys);
        template.setHashKeySerializer(keys);
        template.setValueSerializer(new JdkSerializationRedisSerializer());
        template.setHashValueSerializer(new JdkSerializationRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    FilterRegistrationBean<XssFilter> xss(Environment env) {
        var r = new FilterRegistrationBean<>(new XssFilter());
        r.setEnabled(env.getProperty("xss.enabled", Boolean.class, true));
        r.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        r.addUrlPatterns(env.getProperty("xss.url-patterns", "/api/system/*,/api/monitor/*").split(","));
        r.addInitParameter("excludes", env.getProperty("xss.excludes", "/api/system/user/profile/updatePwd"));
        return r;
    }
}
