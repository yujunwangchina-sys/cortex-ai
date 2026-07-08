package com.cortex.web.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.TimeZone;

/**
 * Jackson全局配置
 * 
 * @author cortex
 */
@Configuration
public class JacksonConfig
{
    /**
     * 配置Jackson全局时区为GMT+8
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonObjectMapperCustomization()
    {
        return jacksonObjectMapperBuilder -> {
            // 设置全局时区为东八区
            jacksonObjectMapperBuilder.timeZone(TimeZone.getTimeZone("GMT+8"));
        };
    }
}
