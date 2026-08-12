import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// 검색 / 지도 API 부하테스트.
//
// 사전 준비:
//   1) 시드 적재 (데이터 규모를 바꿔가며 반복):
//      node search-eval/generate-seed.js --count 100000
//      docker exec -i picngo-mysql mysql -uroot -pPW picngo < search-eval/out/seed/seed-001.sql
//   2) node search-eval/generate-goldenset.js    (검색어 공급원)
//   3) ./gradlew bootRun
//   4) k6 run load-test/search-load.js
//
// 환경변수:
//   BASE_URL      기본 http://localhost:8080
//   SEARCH_VUS    검색 시나리오 최대 VU (기본 30)
//   MAP_VUS       지도 시나리오 최대 VU (기본 20)
//   DURATION      유지 구간 길이 (기본 3m)
//   LABEL         결과 구분용 라벨. 데이터 규모를 넣어두면 나중에 대조하기 쉽다.
//
// 이 스크립트는 게이트가 아니라 관측 도구다. threshold를 걸어두긴 하지만
// 실패해도 의미가 있다 - 어느 데이터 규모에서 SLO가 깨지는지 찾는 게 목적이라
// "깨지는 것"이 곧 관측하려는 현상이다.
//
// 실제 지표는 k6 요약이 아니라 프로메테우스에서 본다. k6는 부하를 만들고,
// 앱의 http_server_requests / spot_search_duration / HikariCP / MySQL 지표를
// 그라파나에서 읽는 방식이다. k6 요약은 클라이언트 관점 교차검증용이다.

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEARCH_VUS = Number(__ENV.SEARCH_VUS || 30);
const MAP_VUS = Number(__ENV.MAP_VUS || 20);
const DURATION = __ENV.DURATION || '3m';
const LABEL = __ENV.LABEL || 'unlabeled';

// 검색어는 골든셋에서 가져온다. 손으로 고른 검색어 몇 개를 반복하면
// MySQL 쿼리 캐시나 버퍼풀에 그 몇 건만 올라가서 실제보다 빠르게 측정된다.
// 골든셋은 800건이 넘고 대부분 서로 다른 스팟을 노려서 캐시 편향이 적다.
const FALLBACK_KEYWORDS = [
    '남산', '한강', '공원', '해수욕장', '전망대', '수목원',
    '한옥마을', '벚꽃', '야경', '계곡', '숲길', '박물관',
];

const keywords = new SharedArray('keywords', () => {
    try {
        const goldenset = JSON.parse(open('../search-eval/out/goldenset.json'));
        // 부하용으로는 "결과가 나오는" 검색어가 적합하다. 무결과 검색어만
        // 던지면 결과 직렬화 비용이 빠져서 실제보다 가볍게 측정된다.
        const usable = goldenset.queries
            .filter((q) => ['exact', 'prefix', 'substring'].includes(q.type))
            .map((q) => q.keyword);
        return usable.length > 0 ? usable : FALLBACK_KEYWORDS;
    } catch (e) {
        console.warn(`골든셋을 못 읽어 기본 검색어로 진행한다: ${e}`);
        return FALLBACK_KEYWORDS;
    }
});

// 지도 시나리오의 출발 지점. 실제 사용자가 몰릴 만한 도시 중심.
const MAP_ORIGINS = [
    { name: '서울', lat: 37.5665, lng: 126.9780 },
    { name: '부산', lat: 35.1796, lng: 129.0756 },
    { name: '제주', lat: 33.4996, lng: 126.5312 },
    { name: '강릉', lat: 37.7519, lng: 128.8761 },
    { name: '전주', lat: 35.8242, lng: 127.1480 },
    { name: '대구', lat: 35.8714, lng: 128.6014 },
];

// 화면 크기(줌 레벨). 작을수록 확대된 화면이고 반환 행 수가 적다.
// 이 값이 지도 쿼리의 선택도를 결정한다 - 광역 줌아웃이 최악의 경우다.
const ZOOM_SPANS = [0.02, 0.05, 0.12, 0.3, 0.6];

const searchDuration = new Trend('search_keyword_duration', true);
const mapDuration = new Trend('search_map_duration', true);
const searchZeroResult = new Counter('search_zero_result');
const mapEmptyResult = new Counter('map_empty_result');

function rampingScenario(exec, vus, name) {
    return {
        executor: 'ramping-vus',
        exec,
        startVUs: 0,
        stages: [
            { duration: '30s', target: vus },
            { duration: DURATION, target: vus },
            { duration: '20s', target: 0 },
        ],
        tags: { scenario: name },
    };
}

// 시나리오를 조건부로 넣는다. ramping-vus는 모든 stage의 target이 0이면
// 설정 오류로 실행 자체가 막히기 때문에, VU가 0인 시나리오는 아예 빼야 한다.
//
// VU 0을 허용해야 하는 이유: 두 시나리오가 같은 커넥션 풀을 공유해서 서로의
// 응답시간에 영향을 준다(실측에서 지도가 풀 점유의 88%를 차지했다). 한쪽만 돌려
// 경합 없는 상태의 성능을 따로 재려면 나머지를 끌 수 있어야 한다.
//   k6 run -e SEARCH_VUS=30 -e MAP_VUS=0 load-test/search-load.js
const scenarios = {};
const thresholds = { http_req_failed: ['rate<0.01'] };

if (SEARCH_VUS > 0) {
    scenarios.keyword_search = rampingScenario('keywordSearch', SEARCH_VUS, 'keyword_search');
    // SLO 기준선. 깨지는 지점을 찾는 게 목적이라 abortOnFail은 걸지 않는다.
    thresholds['http_req_duration{scenario:keyword_search}'] = ['p(95)<200', 'p(99)<500'];
}

// 지도 조회: 사용자가 지도를 이동시키는 상황.
// 디바운싱이 걸려 있어도 팬 한 번에 요청이 하나씩 나가므로,
// 검색보다 요청 빈도가 높은 게 정상이다(읽기 증폭).
if (MAP_VUS > 0) {
    scenarios.map_bounds = rampingScenario('mapBounds', MAP_VUS, 'map_bounds');
    thresholds['http_req_duration{scenario:map_bounds}'] = ['p(95)<200'];
}

if (Object.keys(scenarios).length === 0) {
    throw new Error('SEARCH_VUS와 MAP_VUS가 둘 다 0이라 실행할 시나리오가 없다.');
}

export const options = { scenarios, thresholds };

export function keywordSearch() {
    const keyword = keywords[Math.floor(Math.random() * keywords.length)];
    const url = `${BASE_URL}/spots/search?keyword=${encodeURIComponent(keyword)}&page=0&size=20`;

    const res = http.get(url, { tags: { name: 'GET /spots/search' } });
    searchDuration.add(res.timings.duration);

    const ok = check(res, { '검색 200': (r) => r.status === 200 });
    if (ok && res.body) {
        try {
            if ((res.json('totalElements') ?? 0) === 0) searchZeroResult.add(1);
        } catch (e) {
            // 본문 파싱 실패는 응답 형식이 바뀐 경우다. 부하 측정 자체는 계속한다.
        }
    }
}

// VU마다 자기 위치를 들고 조금씩 움직인다. 매번 완전히 새로운 좌표를 뽑으면
// 버퍼풀 입장에서 무작위 접근이 되어 실제 사용 패턴(한 지역을 둘러봄)과 달라진다.
let cursor = null;

export function mapBounds() {
    if (cursor === null) {
        const origin = MAP_ORIGINS[Math.floor(Math.random() * MAP_ORIGINS.length)];
        cursor = { lat: origin.lat, lng: origin.lng };
    }

    // 팬 이동. 화면 폭의 30% 정도씩 움직이는 상황을 가정한다.
    const span = ZOOM_SPANS[Math.floor(Math.random() * ZOOM_SPANS.length)];
    cursor.lat += (Math.random() - 0.5) * span * 0.6;
    cursor.lng += (Math.random() - 0.5) * span * 0.6;

    // 한반도 밖으로 나가면 되돌린다.
    if (cursor.lat < 33.0 || cursor.lat > 38.6) cursor.lat = 36.5;
    if (cursor.lng < 126.0 || cursor.lng > 129.6) cursor.lng = 127.8;

    // k6 런타임(goja)에는 URLSearchParams가 없어서 직접 조립한다.
    const half = span / 2;
    const query = [
        `southWestLat=${(cursor.lat - half).toFixed(6)}`,
        `southWestLng=${(cursor.lng - half).toFixed(6)}`,
        `northEastLat=${(cursor.lat + half).toFixed(6)}`,
        `northEastLng=${(cursor.lng + half).toFixed(6)}`,
        'size=100',
    ].join('&');

    const res = http.get(`${BASE_URL}/spots/map?${query}`, { tags: { name: 'GET /spots/map' } });
    mapDuration.add(res.timings.duration);

    const ok = check(res, { '지도 200': (r) => r.status === 200 });
    if (ok && res.body) {
        try {
            if ((res.json() ?? []).length === 0) mapEmptyResult.add(1);
        } catch (e) {
            // 위와 같은 이유로 무시
        }
    }
}

export function handleSummary(data) {
    // 실험 조건을 결과와 같이 남긴다. 나중에 데이터 규모별 결과를 비교할 때
    // "이 숫자가 어느 조건에서 나왔는지"가 파일 안에 없으면 대조가 불가능해진다.
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const enriched = {
        label: LABEL,
        baseUrl: BASE_URL,
        config: { searchVus: SEARCH_VUS, mapVus: MAP_VUS, duration: DURATION },
        keywordPoolSize: keywords.length,
        metrics: data.metrics,
    };

    return {
        stdout: `\n부하 라벨: ${LABEL} (검색 ${SEARCH_VUS}VU / 지도 ${MAP_VUS}VU, 유지 ${DURATION})\n`
            + `검색어 풀: ${keywords.length}건\n\n`,
        [`load-test/results/search-load-${LABEL}-${stamp}.json`]: JSON.stringify(enriched, null, 2),
    };
}
