package com.project.picngo.external.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 외부 API 호출용 서킷브레이커 공용 설정.
 *
 * 서킷은 "장애 도메인" 단위로 만든다. 기준은 Java 클래스나 메서드가 아니라
 * 호출 대상 원격 호스트다. 예를 들어 WeatherClient는 클래스는 하나지만
 * 기상청과 일출일몰 API라는 독립적으로 죽는 두 호스트를 호출하므로 서킷도 둘로 나눈다.
 * 반대로 같은 호스트를 치는 단기예보/중기예보는 하나를 공유해야 실패 신호가 모여
 * 더 빨리 판단할 수 있다.
 *
 * Spring Boot 4용 resilience4j 스타터가 아직 없어 @CircuitBreaker 어노테이션 대신
 * 이 레지스트리에서 인스턴스를 꺼내 CircuitBreaker.decorateSupplier로 직접 감싼다.
 */
@Configuration
public class ExternalApiCircuitBreakerConfig {

    /** 느린 호출 판정 기준. 각 클라이언트의 호출 타임아웃과 맞춰 둔다. */
    public static final Duration SLOW_CALL_THRESHOLD = Duration.ofSeconds(3);

    @Bean
    public CircuitBreakerRegistry externalApiCircuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                // 최근 10건 중 5건 이상 모이면 판단한다. 트래픽이 적을 때 한두 건의
                // 우연한 실패로 서킷이 열리지 않게 하는 최소 표본 수다.
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                // 응답이 오더라도 이 시간을 넘으면 "느린 호출"로 센다. 외부 API가
                // 죽지 않고 느려지기만 해도 스레드는 똑같이 묶이기 때문이다.
                .slowCallDurationThreshold(SLOW_CALL_THRESHOLD)
                .slowCallRateThreshold(50.0f)
                .failureRateThreshold(50.0f)
                // open 상태를 10초 유지한 뒤 half-open으로 3건만 흘려보내 회복을 확인한다.
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                // 서킷이 열린 동안에는 거부가 초당 수백 건씩 발생한다. 그 예외마다
                // 스택트레이스를 채우면 장애 상황에서 CPU를 태우는데,
                // 스택은 항상 같은 지점이라 정보 가치도 없다.
                .writableStackTraceEnabled(false)
                .build();

        return CircuitBreakerRegistry.of(config);
    }
}
