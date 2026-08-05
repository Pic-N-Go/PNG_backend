import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// 카카오 로컬 검색 서킷브레이커 부하테스트.
//
// 사전 준비:
//   1) java load-test/mock-server/DelayedKakaoMockServer.java   (5초 지연 목업, :9999)
//   2) KAKAO_LOCAL_SEARCH_BASE_URL=http://localhost:9999 ./gradlew bootRun
//   3) k6 run load-test/circuit-breaker.js
//
// 기대 결과: 초반 요청들은 ~3초(타임아웃 캡)에 걸리다가, 서킷이 open되는 시점부터는
// CallNotPermittedException으로 즉시(수 ms) PlaceSearchResult.error()가 반환된다.
// waitDurationInOpenState(10초) 이후 half-open으로 전환되며 3건만 다시 느려진다.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const slowResponses = new Counter('slow_responses'); // >= 1s
const fastResponses = new Counter('fast_responses'); // < 1s
const responseTime = new Trend('local_search_duration', true);

export const options = {
    stages: [
        { duration: '10s', target: 100 }, // ramp-up
        { duration: '40s', target: 100 }, // 유지
        { duration: '10s', target: 0 },   // ramp-down
    ],
    thresholds: {
        // 게이트 목적이 아니라 관찰 목적이라 실패해도 빌드를 막지 않는다.
        http_req_duration: ['p(95)<10000'],
    },
};

export default function () {
    const url = `${BASE_URL}/local-search?query=${encodeURIComponent('테스트주차장')}&lat=33.4519&lng=126.9918`;
    const res = http.get(url);

    responseTime.add(res.timings.duration);

    if (res.timings.duration >= 1000) {
        slowResponses.add(1);
    } else {
        fastResponses.add(1);
    }

    check(res, {
        '200 응답': (r) => r.status === 200,
    });
}

// handleSummary를 따로 정의하지 않아 k6 기본 요약(퍼센타일 등)이 그대로 출력된다.
// slow_responses/fast_responses 카운터도 그 표 안에 자동으로 포함된다.
// 서킷이 정상 동작했다면 fast_responses가 slow_responses보다 훨씬 많아야 한다.
