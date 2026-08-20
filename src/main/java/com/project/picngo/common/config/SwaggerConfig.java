package com.project.picngo.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // 인증 방식을 components에 정의하는 것만으로는 부족하다. 그것만 있으면
                // Authorize 버튼은 뜨지만 실제 요청에 Authorization 헤더가 붙지 않아,
                // 토큰을 넣어도 401/403이 돌아온다. 아래 SecurityRequirement까지 있어야
                // "이 API들이 그 인증을 쓴다"가 되어 헤더가 실제로 실린다.
                //
                // 전역으로 건 이유: 공개 API에까지 헤더가 붙지만 서버가 무시하므로 해가 없고,
                // 엔드포인트마다 붙이면 새 API를 만들 때마다 빠뜨리기 쉽다.
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ))
                .info(new Info()
                        .title("PicNGo API 명세서")
                        .description("PicNGo 프로젝트의 백엔드 API 명세서입니다.")
                        .version("1.0.0"));
    }
}
