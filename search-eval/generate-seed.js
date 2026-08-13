#!/usr/bin/env node
// 검색 성능 실험용 시드 데이터 생성기.
//
//   node search-eval/generate-seed.js --count 100000
//   node search-eval/generate-seed.js --count 1000000 --out ./out
//
// 왜 필요한가: 현재 spot 테이블은 135건이라 LIKE '%키워드%' 풀스캔이 얼마든 빨리
// 끝난다. "31ms"는 MySQL이 충분하다는 증거가 아니라 테이블이 작다는 증거다.
// 데이터 규모를 축으로 놓고 응답시간이 어디서 꺾이는지 봐야 판단이 선다.
//
// 생성 원칙 - 측정에 영향을 주는 속성만 현실적으로 만든다:
//   - name       유니크하고 그럴듯한 한국어 지명. 검색 선택도를 좌우한다.
//   - overview   200~800자 한국어 본문. 검색 쿼리가 이 TEXT 컬럼까지 LIKE로 훑기
//                때문에, 비워두면 실제 스캔 비용을 크게 과소평가하게 된다.
//   - latitude/longitude  전국에 퍼진 좌표. 지도 bounds 쿼리의 선택도를 좌우한다.
//   - categories 다중 태그. 카테고리 필터 쿼리의 상관 서브쿼리 비용에 영향.
//
// 생성하지 않는 것(측정과 무관): 이미지 URL, 전화번호, 운영시간 등.
//
// 실제 스팟 135건(id 1~135)은 건드리지 않는다. 골든셋의 정답이 그 135건이라,
// 필러가 정답을 덮어쓰면 평가가 망가진다. 필러는 id 1000000부터 시작한다.

import { mkdirSync, writeFileSync, rmSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRng } from './lib/rng.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(HERE, '..');

const FILLER_START_ID = 1_000_000;
const ROWS_PER_INSERT = 500;
// 한 파일이 너무 크면 mysql 클라이언트로 밀어넣을 때 다루기 번거롭다.
const ROWS_PER_FILE = 100_000;

// 시/도별 좌표 범위와 가중치. 실제 인구/관광지 분포를 대충 흉내낸다.
// 정확할 필요는 없고, 좌표가 전국에 퍼져 있어서 지도 bounds 쿼리가
// 화면 크기에 따라 다른 행 수를 반환하기만 하면 된다.
const REGIONS = [
    { sido: '서울특별시', weight: 20, lat: [37.43, 37.70], lng: [126.79, 127.18], gu: ['종로구', '중구', '용산구', '성동구', '광진구', '마포구', '서대문구', '강남구', '송파구', '노원구'] },
    { sido: '경기도', weight: 18, lat: [36.90, 38.28], lng: [126.40, 127.85], gu: ['수원시 팔달구', '성남시 분당구', '고양시 일산동구', '용인시 기흥구', '파주시', '가평군', '양평군'] },
    { sido: '부산광역시', weight: 8, lat: [35.05, 35.35], lng: [128.85, 129.30], gu: ['해운대구', '수영구', '중구', '영도구', '기장군'] },
    { sido: '강원특별자치도', weight: 9, lat: [37.00, 38.55], lng: [127.70, 129.36], gu: ['속초시', '강릉시', '평창군', '양양군', '인제군'] },
    { sido: '제주특별자치도', weight: 8, lat: [33.11, 33.56], lng: [126.16, 126.96], gu: ['제주시', '서귀포시'] },
    { sido: '경상북도', weight: 7, lat: [35.70, 37.10], lng: [128.00, 129.60], gu: ['경주시', '안동시', '포항시 남구', '영주시'] },
    { sido: '전라남도', weight: 7, lat: [34.30, 35.50], lng: [126.10, 127.90], gu: ['여수시', '순천시', '목포시', '담양군'] },
    { sido: '충청남도', weight: 6, lat: [36.00, 37.00], lng: [126.10, 127.50], gu: ['공주시', '보령시', '태안군', '서산시'] },
    { sido: '경상남도', weight: 6, lat: [34.60, 35.90], lng: [127.60, 129.20], gu: ['통영시', '거제시', '남해군', '창원시 마산합포구'] },
    { sido: '전북특별자치도', weight: 5, lat: [35.30, 36.20], lng: [126.40, 127.90], gu: ['전주시 완산구', '군산시', '남원시', '고창군'] },
    { sido: '충청북도', weight: 4, lat: [36.00, 37.20], lng: [127.30, 128.60], gu: ['청주시 상당구', '제천시', '단양군'] },
    { sido: '인천광역시', weight: 4, lat: [37.30, 37.80], lng: [126.35, 126.80], gu: ['중구', '연수구', '강화군', '옹진군'] },
    { sido: '대구광역시', weight: 3, lat: [35.75, 36.00], lng: [128.45, 128.75], gu: ['중구', '수성구', '달성군'] },
    { sido: '광주광역시', weight: 3, lat: [35.08, 35.25], lng: [126.75, 126.95], gu: ['동구', '북구', '광산구'] },
    { sido: '대전광역시', weight: 3, lat: [36.25, 36.45], lng: [127.28, 127.50], gu: ['유성구', '중구', '동구'] },
    { sido: '울산광역시', weight: 2, lat: [35.45, 35.65], lng: [129.10, 129.45], gu: ['중구', '동구', '울주군'] },
    { sido: '세종특별자치시', weight: 1, lat: [36.44, 36.62], lng: [127.20, 127.35], gu: ['연서면', '조치원읍', '세종동'] },
];

// 이름을 만드는 어휘. (수식어 + 고유어 + 장소유형)의 조합으로
// 겹치지 않으면서 그럴듯한 이름을 대량으로 만든다.
const NAME_MODIFIER = ['', '', '', '옛', '큰', '작은', '윗', '아랫', '동', '서', '남', '북', '새', '한', '올', '늘'];
const NAME_STEM = [
    '가람', '노을', '달빛', '별빛', '이슬', '바람', '구름', '안개', '서리', '무지개',
    '소나무', '단풍', '벚꽃', '억새', '갈대', '연꽃', '수국', '동백', '유채', '메밀',
    '바위', '너럭', '벼랑', '기슭', '골짜기', '언덕', '들녘', '갯벌', '모래', '자갈',
    '학', '두루미', '기러기', '수달', '반딧불', '고래', '갈매기', '까치', '노루', '다람쥐',
    '청람', '백로', '금강', '은하', '옥빛', '푸른', '맑은', '고요', '해맑', '늘봄',
];
const NAME_SUFFIX = [
    '공원', '숲길', '전망대', '호수', '저수지', '해변', '포구', '산책로', '둘레길', '정원',
    '수목원', '생태원', '체험관', '문화마을', '한옥마을', '폭포', '계곡', '자연휴양림', '캠핑장', '오름',
    '유원지', '박물관', '미술관', '기념관', '성지', '고택', '서원', '향교', '나루터', '등대',
];

// overview 본문 조각. 실제 TourAPI 개요문의 어투를 흉내낸다.
const OVERVIEW_OPENER = [
    '사계절 내내 서로 다른 풍경을 보여주는 곳이다.',
    '지역 주민들이 오래 아껴온 조용한 명소다.',
    '수도권에서 접근성이 좋아 당일 여행지로 인기가 높다.',
    '해가 지는 시간대에 특히 아름다운 빛을 담을 수 있다.',
    '사진 애호가들 사이에서 입소문을 타며 알려지기 시작했다.',
];
const OVERVIEW_BODY = [
    '산책로를 따라 걷다 보면 계절마다 다른 야생화가 피어난다.',
    '넓은 잔디밭과 그늘이 있어 가족 단위 방문객이 많다.',
    '이른 아침에는 물안개가 피어올라 몽환적인 분위기를 만든다.',
    '주변에 카페와 식당이 모여 있어 하루를 온전히 보내기 좋다.',
    '전망 데크에서 내려다보는 풍경이 이곳의 백미로 꼽힌다.',
    '봄에는 벚꽃, 가을에는 단풍이 길을 따라 이어진다.',
    '야간 조명이 설치되어 해가 진 뒤에도 산책이 가능하다.',
    '완만한 경사라 아이나 어르신도 부담 없이 둘러볼 수 있다.',
    '인근 주차장이 넓어 자가용 방문객도 불편함이 적다.',
    '바람이 잔잔한 날에는 수면에 반영된 풍경을 담을 수 있다.',
    '자연 그대로의 지형을 살려 조성해 인공적인 느낌이 적다.',
    '탐방로 곳곳에 쉼터와 안내판이 마련되어 있다.',
];
const OVERVIEW_CLOSER = [
    '방문 전 운영 시간과 휴무일을 확인하는 것이 좋다.',
    '주말과 공휴일에는 방문객이 몰려 다소 혼잡할 수 있다.',
    '대중교통보다는 자가용 이용이 편리한 편이다.',
    '삼각대 사용은 일부 구간에서 제한될 수 있으니 유의한다.',
];

const CATEGORIES = [
    'PARK', 'BEACH', 'MOUNTAIN', 'HANOK', 'FOREST', 'HERITAGE',
    'CAFE', 'CITY', 'NIGHT_VIEW', 'FESTIVAL', 'FLOWER', 'SUNRISE_SUNSET', 'MILKY_WAY', 'ETC',
];

// 장소유형 -> 자연스러운 카테고리. 무작위로 붙이면 "해변"인데 MOUNTAIN이 달리는
// 식이 되어 동의어/자연어 골든셋의 정답 집합이 오염된다.
const SUFFIX_CATEGORY = {
    공원: ['PARK'], 숲길: ['FOREST'], 전망대: ['NIGHT_VIEW', 'CITY'], 호수: ['PARK'],
    저수지: ['PARK'], 해변: ['BEACH'], 포구: ['BEACH'], 산책로: ['PARK'], 둘레길: ['MOUNTAIN'],
    정원: ['FLOWER', 'PARK'], 수목원: ['FOREST', 'FLOWER'], 생태원: ['FOREST'], 체험관: ['ETC'],
    문화마을: ['CITY'], 한옥마을: ['HANOK'], 폭포: ['MOUNTAIN'], 계곡: ['MOUNTAIN'],
    자연휴양림: ['FOREST'], 캠핑장: ['FOREST', 'MILKY_WAY'], 오름: ['MOUNTAIN', 'SUNRISE_SUNSET'],
    유원지: ['PARK'], 박물관: ['HERITAGE'], 미술관: ['CITY'], 기념관: ['HERITAGE'],
    성지: ['HERITAGE'], 고택: ['HANOK', 'HERITAGE'], 서원: ['HANOK', 'HERITAGE'],
    향교: ['HANOK', 'HERITAGE'], 나루터: ['SUNRISE_SUNSET'], 등대: ['BEACH', 'SUNRISE_SUNSET'],
};

function parseArgs(argv) {
    const args = { count: 100_000, out: join(HERE, 'out'), seed: 20260811 };
    for (let i = 2; i < argv.length; i += 1) {
        const [key, inlineValue] = argv[i].split('=');
        const value = inlineValue ?? argv[i + 1];
        if (key === '--count') { args.count = Number(value.replace(/_/g, '')); if (!inlineValue) i += 1; }
        else if (key === '--out') { args.out = value; if (!inlineValue) i += 1; }
        else if (key === '--seed') { args.seed = Number(value); if (!inlineValue) i += 1; }
        else if (key === '--help' || key === '-h') { args.help = true; }
    }
    return args;
}

function weightedRegion(rng) {
    const total = REGIONS.reduce((sum, r) => sum + r.weight, 0);
    let roll = rng.next() * total;
    for (const region of REGIONS) {
        roll -= region.weight;
        if (roll <= 0) return region;
    }
    return REGIONS[REGIONS.length - 1];
}

function buildOverview(rng) {
    const sentences = [
        rng.pick(OVERVIEW_OPENER),
        ...rng.sample(OVERVIEW_BODY, rng.int(3, 9)),
        rng.pick(OVERVIEW_CLOSER),
    ];
    return sentences.join(' ');
}

function sqlString(value) {
    return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}

function buildSpot(index, rng) {
    const id = FILLER_START_ID + index;
    const region = weightedRegion(rng);
    const suffix = rng.pick(NAME_SUFFIX);

    // 이름 충돌을 완전히 막기 위해 일련번호를 붙인다. 실제 지명에도
    // '제1주차장'처럼 숫자가 붙는 경우가 흔해서 부자연스럽지 않고,
    // 무엇보다 name의 선택도(거의 유니크)가 실제와 비슷해진다.
    const modifier = rng.pick(NAME_MODIFIER);
    const stem = rng.pick(NAME_STEM);
    const name = `${modifier}${stem}${suffix} ${index + 1}`.trim();

    const gu = rng.pick(region.gu);
    const address = `${region.sido} ${gu} ${rng.pick(NAME_STEM)}로 ${rng.int(1, 400)}`;

    const latitude = region.lat[0] + rng.next() * (region.lat[1] - region.lat[0]);
    const longitude = region.lng[0] + rng.next() * (region.lng[1] - region.lng[0]);

    const base = SUFFIX_CATEGORY[suffix] ?? ['ETC'];
    const extra = rng.bool(0.35) ? [rng.pick(CATEGORIES)] : [];
    const categories = [...new Set([...base, ...extra])];

    return {
        id,
        name,
        address,
        overview: buildOverview(rng),
        latitude: latitude.toFixed(7),
        longitude: longitude.toFixed(7),
        categories,
        bookmarkCount: rng.int(0, 200),
        reviewCount: rng.int(0, 80),
        photogenicScore: rng.int(40, 100),
        reviewAverage: (rng.int(25, 50) / 10).toFixed(1),
        toilet: rng.bool(0.6),
    };
}

function spotValues(spot) {
    return `(${spot.id}, ${sqlString(spot.name)}, ${sqlString(spot.address)}, ${sqlString(spot.overview)}, `
        + `${spot.latitude}, ${spot.longitude}, 'TOUR_API', true, 'APPROVED', 'UNKNOWN', `
        + `${spot.bookmarkCount}, ${spot.reviewCount}, ${spot.photogenicScore}, true, `
        + `${spot.reviewAverage}, ${spot.toilet}, NOW(), NOW())`;
}

const SPOT_COLUMNS = 'id, name, address, overview, latitude, longitude, source, badge, status, access_type, '
    + 'bookmark_count, review_count, photogenic_score, is_active, review_average, toilet, created_at, updated_at';

function main() {
    const args = parseArgs(process.argv);
    if (args.help) {
        console.log(`사용법: node search-eval/generate-seed.js [--count 100000] [--out DIR] [--seed 20260811]

  --count  생성할 필러 스팟 수 (기본 100000)
  --out    출력 디렉터리 (기본 search-eval/out)
  --seed   난수 시드. 같은 시드면 항상 같은 데이터가 나온다 (기본 20260811)`);
        return;
    }

    if (!Number.isFinite(args.count) || args.count <= 0) {
        console.error('--count 는 양의 정수여야 한다.');
        process.exit(1);
    }

    const outDir = join(args.out, 'seed');
    if (existsSync(outDir)) rmSync(outDir, { recursive: true });
    mkdirSync(outDir, { recursive: true });

    const rng = createRng(args.seed);
    const fileCount = Math.ceil(args.count / ROWS_PER_FILE);
    const written = [];

    for (let fileIdx = 0; fileIdx < fileCount; fileIdx += 1) {
        const from = fileIdx * ROWS_PER_FILE;
        const to = Math.min(from + ROWS_PER_FILE, args.count);

        const lines = [
            `-- 자동 생성 파일 - 직접 수정하지 말 것 (search-eval/generate-seed.js)`,
            `-- seed=${args.seed}, 스팟 ${from + 1}~${to} / 전체 ${args.count}`,
            `-- id 범위: ${FILLER_START_ID + from} ~ ${FILLER_START_ID + to - 1}`,
            '',
            // 파일이 스스로 세션 charset을 선언한다. --default-character-set 옵션을
            // 빠뜨리거나 Windows 클라이언트가 콘솔 코드페이지를 따라가더라도, 이 한 줄이
            // 있으면 한글이 '?'로 치환되지 않는다. 적재 명령을 어떻게 치든 안전하게 만드는
            // 게 목적 - 데이터가 깨진 채 들어가면 검색 실험 자체가 무의미해진다.
            'SET NAMES utf8mb4;',
            '',
            'SET autocommit = 0;',
            'SET unique_checks = 0;',
            'SET foreign_key_checks = 0;',
            '',
        ];

        const categoryRows = [];

        for (let batchStart = from; batchStart < to; batchStart += ROWS_PER_INSERT) {
            const batchEnd = Math.min(batchStart + ROWS_PER_INSERT, to);
            const values = [];

            for (let i = batchStart; i < batchEnd; i += 1) {
                const spot = buildSpot(i, rng);
                values.push(spotValues(spot));
                for (const category of spot.categories) {
                    categoryRows.push(`(${spot.id}, '${category}')`);
                }
            }

            lines.push(`INSERT INTO spot (${SPOT_COLUMNS}) VALUES`);
            lines.push(`${values.join(',\n')}`);
            lines.push('ON DUPLICATE KEY UPDATE id=id;', '');
        }

        for (let i = 0; i < categoryRows.length; i += ROWS_PER_INSERT) {
            lines.push('INSERT INTO spot_categories (spot_id, category) VALUES');
            lines.push(categoryRows.slice(i, i + ROWS_PER_INSERT).join(',\n'));
            lines.push('ON DUPLICATE KEY UPDATE spot_id=spot_id;', '');
        }

        lines.push('COMMIT;', 'SET unique_checks = 1;', 'SET foreign_key_checks = 1;', 'SET autocommit = 1;', '');

        const filename = `seed-${String(fileIdx + 1).padStart(3, '0')}.sql`;
        writeFileSync(join(outDir, filename), lines.join('\n'), 'utf8');
        written.push(filename);
    }

    // 되돌리기용. 실험 조건을 바꿀 때마다 필러만 지우고 다시 넣는다.
    // chat_room도 지우는 이유: SpotCreatedEvent 리스너(ChatRoomEventListener)가 스팟마다
    // 채팅방을 만드는데, spot.id를 FK로 참조하므로 먼저 지우지 않으면 스팟 삭제가 막힌다.
    writeFileSync(join(outDir, 'cleanup.sql'), [
        '-- 필러 스팟만 삭제한다. 실제 스팟 135건(id 1~135)은 건드리지 않는다.',
        'SET NAMES utf8mb4;',
        `DELETE FROM chat_room WHERE spot_id >= ${FILLER_START_ID};`,
        `DELETE FROM spot_categories WHERE spot_id >= ${FILLER_START_ID};`,
        `DELETE FROM spot WHERE id >= ${FILLER_START_ID};`,
        '',
    ].join('\n'), 'utf8');

    const relOut = outDir.replace(PROJECT_ROOT, '.').replace(/\\/g, '/');
    console.log(`시드 SQL ${written.length}개 파일 생성 (필러 ${args.count.toLocaleString()}건, seed=${args.seed})`);
    console.log(`  위치: ${relOut}`);
    console.log(`  id 범위: ${FILLER_START_ID} ~ ${FILLER_START_ID + args.count - 1}`);
    // 한글이 들어가므로 --default-character-set=utf8mb4를 반드시 붙인다.
    // Windows의 mysql 클라이언트는 콘솔 코드페이지(cp949)를 따라가는 경우가 있어
    // 이걸 빼면 이름/overview가 깨진 채로 적재된다.
    // PowerShell은 '<' 입력 리다이렉션을 지원하지 않는다(예약 연산자). 그래서
    // mysql 클라이언트 자체의 source 명령을 쓴다. 파일을 mysql이 직접 읽으므로
    // 셸을 거치며 인코딩이 다시 바뀔 일도 없어서, 어느 셸에서든 이 형태가 안전하다.
    console.log('');
    console.log('적재 (PowerShell / cmd / Git Bash 공통):');
    for (const file of written) {
        console.log(`  mysql -u root -p --default-character-set=utf8mb4 picngo -e "source ${relOut}/${file}"`);
    }
    console.log('');
    console.log('적재 (docker 컨테이너인 경우):');
    console.log(`  docker exec -i picngo-mysql mysql -uroot -p --default-character-set=utf8mb4 picngo -e "source ${relOut}/${written[0]}"`);
    console.log('');
    console.log('되돌리기:');
    console.log(`  mysql -u root -p --default-character-set=utf8mb4 picngo -e "source ${relOut}/cleanup.sql"`);
}

main();
