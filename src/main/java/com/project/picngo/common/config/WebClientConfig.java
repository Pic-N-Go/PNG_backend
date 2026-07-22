package com.project.picngo.common.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    // prototype 스코프 필수 — 싱글톤이면 각 클라이언트가 공유 builder를 mutate해서 baseUrl이 서로 오염됨
    @Bean
    @Scope("prototype")
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(12))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(12, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(12, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}

