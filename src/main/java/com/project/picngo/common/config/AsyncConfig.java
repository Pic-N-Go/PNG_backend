package com.project.picngo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

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
        // 큐(200)까지 가득 차면 예외로 스케줄러를 중단시키는 대신, 제출한 스레드(스케줄러)가 직접 실행한다.
        // → 작업 유실 없이 자연스러운 백프레셔로 감속. (워밍업은 실패해도 매칭 루프에서 개별 조회로 폴백)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 500 에러 디스코드 알림 전송 전용 스레드풀.
     * 위의 워밍업 풀과 달리, 꽉 차면 요청 스레드가 대신 보내지 않고 알림을 버린다(DiscardPolicy).
     * 장애로 에러가 쏟아질 때 알림 전송 때문에 사용자 응답까지 느려지면 안 되기 때문이다.
     * 같은 에러는 10분에 한 번만 보내므로 평소에는 큐가 거의 비어 있다.
     */
    @Bean(name = "errorAlertExecutor")
    public ThreadPoolTaskExecutor errorAlertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("error-alert-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
