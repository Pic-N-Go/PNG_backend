package com.project.picngo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // ponytail: prototype 스코프 필수 — 싱글톤이면 각 클라이언트가 공유 builder를 mutate해서 baseUrl이 서로 오염됨
    @Bean
    @Scope("prototype")
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    // ponytail: WebClient.Builder와 동일한 이유로 prototype — 지금은 소비자가 하나뿐이라 실제 충돌은 없지만 예방 차원
    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
