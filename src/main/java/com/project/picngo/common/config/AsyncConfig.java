package com.project.picngo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /**
     * 스케줄러의 날씨 예보 사전 워밍업 전용 스레드풀.
     * 스팟별 기상청 외부 API 호출을 병렬화하되, 외부 API rate limit 보호를 위해
     * 동시 호출 수를 제한한다. (rate limit 확인 전까지 보수적으로 8개로 고정)
     */
    @Bean(name = "weatherWarmupExecutor")
    public ThreadPoolTaskExecutor weatherWarmupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("weather-warmup-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
