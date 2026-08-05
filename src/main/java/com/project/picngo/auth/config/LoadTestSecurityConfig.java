package com.project.picngo.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * loadtest 프로파일에서만 로드되는 보안 예외 설정.
 *
 * 여기 담긴 엔드포인트들은 전부 외부 유료·쿼터 제한 API를 호출하거나(카카오, 공공데이터포털)
 * 무거운 배치를 돌린다. 인터넷에 공개되면 아무나 우리 API 쿼터를 소진시킬 수 있고,
 * 서비스가 죽는 게 아니라 조용히 "외부 API가 안 되는 상태"가 되어 발견도 늦다.
 *
 * 활성화:
 *   SPRING_PROFILES_ACTIVE=loadtest ./gradlew bootRun
 *   (PowerShell: $env:SPRING_PROFILES_ACTIVE = "loadtest")
 */
@Configuration
@Profile("loadtest")
public class LoadTestSecurityConfig {

    @Bean
    public LoadTestPublicEndpoints loadTestPublicEndpoints() {
        return new LoadTestPublicEndpoints(List.of(
                // 서킷브레이커 부하테스트 전용으로 만든 엔드포인트
                "/local-search",
                // 실사용 API지만 부하테스트 대상이라 열어둔다
                "/directions",
                // 관리자용 스팟 동기화. /tour-api/sync/all은 전국 데이터를 통째로
                // 재동기화하는 무거운 작업이라 공개 상태로 두면 안 된다
                "/tour-api/**"
        ));
    }
}
