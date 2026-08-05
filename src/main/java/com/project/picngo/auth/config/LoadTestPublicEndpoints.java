package com.project.picngo.auth.config;

import java.util.List;

/**
 * 부하테스트/로컬 검증 목적으로만 인증을 면제할 엔드포인트 목록.
 *
 * 이 빈은 loadtest 프로파일에서만 등록된다({@link LoadTestSecurityConfig}).
 * 운영 프로파일에서는 빈 자체가 존재하지 않으므로 SecurityConfig가 이 목록을 적용할 수 없다.
 * "임시로 열고 배포 전에 닫자"는 사람이 기억해야 하지만, 이 구조는 환경이 강제한다.
 */
public record LoadTestPublicEndpoints(List<String> patterns) {
}
