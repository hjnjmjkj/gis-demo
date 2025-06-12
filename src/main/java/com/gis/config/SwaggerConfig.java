package com.gis.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI terrainOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("地形分析 API")
                        .description("地形数据生成和分析服务接口")
                        .version("1.0")
                        .contact(new Contact()
                                .name("GIS团队")
                                .email("contact@example.com")));
    }
}