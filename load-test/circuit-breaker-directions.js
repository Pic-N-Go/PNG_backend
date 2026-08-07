import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// 카카오 길찾기(KakaoDirectionsClient) 서킷브레이커 부하테스트.
//
// 사전 준비 (PowerShell 기준, 터미널 3개):
//   1) java load-test/mock-server/DelayedKakaoMockServer.java
//   2) $env:SPRING_PROFILES_ACTIVE = "loadtest"
//      ./gradlew bootRun
//   3) k6 run load-test/circuit-breaker-directions.js
//
// loadtest 프로파일이 인증 면제와 목업 URL을 한꺼번에 걸어준다(application-loadtest.yaml).
// 이 프로파일 없이 띄우면 /directions는 403이고 실제 카카오 API를 호출한다.
//
// 로컬 검색 테스트와 다른 점: /directions는 실패 시 CustomException을 그대로 던지는
// 직통 엔드포인트라 HTTP 500이 나온다. 실제 유저 경로(코스 저장 -> RouteCacheService)는
// 이 예외를 잡아 폴백 추정치로 넘어가므로 500이 유저에게 나가지는 않는다.
// 따라서 여기서 볼 것은 상태코드가 아니라 "얼마나 빨리 실패하는가"다.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const slowResponses = new Counter('slow_responses'); // >= 1s
const fastResponses = new Counter('fast_responses'); // < 1s
const responseTime = new Trend('directions_duration', true);

export const options = {
    stages: [
        { duration: '10s', target: 100 },
        { duration: '40s', target: 100 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        // 게이트가 아니라 관찰이 목적이라 느슨하게 잡는다.
        http_req_duration: ['p(95)<10000'],
    },
};

export default function () {
    // 제주 성산일출봉 -> 한라산 방면 임의 좌표
    const url = `${BASE_URL}/directions?startLat=33.4519&startLng=126.9918&goalLat=33.3617&goalLng=126.5382`;
    const res = http.get(url);

    responseTime.add(res.timings.duration);

    if (res.timings.duration >= 1000) {
        slowResponses.add(1);
    } else {
        fastResponses.add(1);
    }

    check(res, {
        '응답 수신(200 또는 500)': (r) => r.status === 200 || r.status === 500,
        '1초 미만 응답': (r) => r.timings.duration < 1000,
    });
}

// 서킷이 정상 동작했다면 fast_responses가 slow_responses보다 압도적으로 많아야 한다.
// slow_responses는 서킷이 닫혀 있던 초반 + half-open 재시도분(10초마다 3건)만 나와야 정상.
