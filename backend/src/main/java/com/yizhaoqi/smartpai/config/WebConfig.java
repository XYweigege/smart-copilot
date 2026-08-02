package com.yizhaoqi.smartpai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web配置类
 * 确保HTTP响应中的中文字符正确显示
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoggingInterceptor loggingInterceptor;

    @Autowired
    private SensitiveWordFilter sensitiveWordFilter;

    @Autowired
    private SensitiveWordConfig sensitiveWordConfig;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源处理
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
        
        // 添加根路径的静态资源处理
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/resources/", "classpath:/META-INF/resources/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册日志拦截器，排除静态资源
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/images/**", "/*.ico", "/*.html");
    }

    /**
     * 配置HTTP消息转换器，确保中文字符正确编码
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 配置字符串转换器，使用UTF-8编码
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setWriteAcceptCharset(false); // 避免在响应头中添加charset参数
        converters.add(stringConverter);
        
        // 配置JSON转换器
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = jsonConverter.getObjectMapper();
        
        // 确保中文字符不被转义为Unicode编码
        objectMapper.getFactory().configure(
            com.fasterxml.jackson.core.JsonGenerator.Feature.ESCAPE_NON_ASCII, false
        );
        
        jsonConverter.setObjectMapper(objectMapper);
        converters.add(jsonConverter);
    }

    /**
     * 注册敏感词 HTTP 过滤器。
     * order 设为较高优先级之后，确保在认证过滤器处理完、能拿到当前用户后再做扫描。
     */
    @Bean
    public FilterRegistrationBean<SensitiveWordFilterOnce> sensitiveWordFilterRegistration() {
        FilterRegistrationBean<SensitiveWordFilterOnce> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SensitiveWordFilterOnce(sensitiveWordFilter, sensitiveWordConfig));
        registration.addUrlPatterns("/api/v1/*");
        registration.setName("sensitiveWordFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }
} 