import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// WeatherClient 서킷브레이커 부하테스트.
//
// 이 스크립트의 목적은 "서킷이 열리는가"뿐 아니라 **두 서킷이 독립적인가**를 보는 것이다.
// WeatherClient는 클래스가 하나지만 기상청과 일출일몰이라는 별개 호스트를 호출하고,
// 서킷도 kmaWeather / sunriseSunset 둘로 나뉘어 있다.
//
// 목업 서버는 기상청 경로만 5초 지연시키고 일출일몰(/json)은 즉시 응답한다.
// 따라서 기대 결과는:
//   - /weather               : 서킷이 열려 빠르게 실패(5xx)로 전환
//   - /spots/1/golden-hour   : 내내 200 정상 (기상청 장애에 영향받지 않음)
//
// 서킷을 하나로 합쳤다면 골든아워도 같이 막혀버린다. 그게 이 테스트가 잡아내려는 회귀다.
//
// 사전 준비 (PowerShell 기준, 터미널 3개):
//   1) java load-test/mock-server/DelayedKakaoMockServer.java
//   2) ./gradlew bootRun --args='--spring.profiles.active=loadtest'
//   3) k6 run load-test/circuit-breaker-weather.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const kmaDuration = new Trend('kma_duration', true);
const sunriseDuration = new Trend('sunrise_duration', true);
const sunriseOk = new Counter('sunrise_ok');
const sunriseFail = new Counter('sunrise_fail');

export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '30s', target: 50 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        // 기상청이 죽어도 일출일몰은 계속 성공해야 한다. 이게 깨지면 서킷 분리가 무너진 것.
        sunrise_fail: ['count==0'],
    },
};

export default function () {
    // 1) 기상청 - 목업이 5초 지연시키므로 타임아웃/서킷 open 경로를 탄다
    const kma = http.get(`${BASE_URL}/weather?lat=37.5665&lng=126.9780`);
    kmaDuration.add(kma.timings.duration);

    // 2) 일출일몰 - 목업이 즉시 응답하므로 계속 정상이어야 한다
    const sunrise = http.get(`${BASE_URL}/spots/1/golden-hour?date=2026-08-05`);
    sunriseDuration.add(sunrise.timings.duration);

    if (sunrise.status === 200) {
        sunriseOk.add(1);
    } else {
        sunriseFail.add(1);
    }

    check(sunrise, {
        '일출일몰은 기상청 장애와 무관하게 200': (r) => r.status === 200,
    });
}
