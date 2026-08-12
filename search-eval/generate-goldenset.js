#!/usr/bin/env node
// 골든셋(정답이 붙은 검색어 집합) 생성기.
//
//   node search-eval/generate-goldenset.js
//
// 왜 이렇게 하는가:
// 무결과율 같은 검색 품질 지표는 실사용 트래픽이 있어야 의미가 있다. 혼자
// 검색해서 나온 비율은 표본이 작은 것도 문제지만, 더 큰 문제는 검색어를 치는
// 사람이 데이터를 아는 사람이라는 점이다. 나도 모르게 DB에 있는 이름을 정확히
// 친다. 사람을 더 모아도 이 편향은 그대로 남는다.
//
// 그래서 관측 대신 통제된 실험으로 바꾼다. 사람이 검색어를 "떠올리는" 게 아니라
// DB에 있는 스팟에서 검색어를 기계적으로 파생시킨다. 파생 규칙이 곧 실험 조건이고
// 정답 스팟이 이미 정해져 있으니 적중률을 셀 수 있다. 정보검색(IR) 분야에서
// test collection이라고 부르는 평가 방식이다.
//
// 정답 집합의 근거:
//   exact ~ typo_*  파생의 출처가 된 스팟 1건. 명백하다.
//   synonym         이름에 해당 단어가 들어간 스팟 전체.
//   natural         해당 카테고리가 달린 스팟 전체. (태깅 오탐 가능 - 한계로 기록)
//
// 필러 스팟(id 1000000+)은 정답이 될 수 없다. 골든셋은 항상 실제 135건만
// 대상으로 하므로, 데이터 규모를 1만/10만/100만으로 바꿔가며 같은 검색어 집합으로
// 품질과 응답시간의 변화를 나란히 볼 수 있다.

import { mkdirSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRng } from './lib/rng.js';
import { loadCorpus } from './lib/corpus.js';
import { SYNONYMS, NATURAL_QUERIES } from './lib/semantics.js';
import {
    typoAdjacentKey,
    typoDropJongsung,
    removeSpaces,
    insertSpace,
} from './lib/hangul.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(HERE, '..');
const CORPUS_SQL = join(PROJECT_ROOT, 'src', 'main', 'resources', 'spot_data.sql');

// 변형 유형. expectation은 "LIKE '%키워드%' 방식이 원리적으로 잡을 수 있는가"에
// 대한 사전 예상이다. 측정 전에 적어두는 게 중요하다 - 결과를 보고 나서
// 기준을 정하면 결론에 맞춰 해석했다는 의심을 피할 수 없다.
const VARIANT_META = {
    exact: { label: '정확 일치', expectation: 'hit', note: '기준선. 여기서 100%가 안 나오면 실험 자체가 잘못된 것' },
    prefix: { label: '앞부분만', expectation: 'hit', note: '부분 문자열이라 LIKE가 잡는다' },
    substring: { label: '중간 부분', expectation: 'hit', note: '선행 와일드카드라 인덱스는 못 타지만 매칭은 된다' },
    spacing_removed: { label: '공백 제거', expectation: 'miss', note: '저장된 문자열과 달라져 LIKE가 실패' },
    spacing_added: { label: '공백 삽입', expectation: 'miss', note: '같은 이유' },
    typo_adjacent: { label: '자판 인접키 오타', expectation: 'miss', note: '한 글자만 달라도 LIKE는 전부 실패' },
    typo_jongseong: { label: '종성 누락', expectation: 'miss', note: '같은 이유' },
    synonym: { label: '동의어', expectation: 'miss', note: '문자열이 겹치지 않아 원리적으로 불가' },
    natural: { label: '자연어 서술', expectation: 'miss', note: '의미 검색이라 문자열 매칭으로는 불가' },
};

function parseArgs(argv) {
    const args = { out: join(HERE, 'out'), seed: 20260811, excludeIds: [] };
    for (let i = 2; i < argv.length; i += 1) {
        const [key, inlineValue] = argv[i].split('=');
        const value = inlineValue ?? argv[i + 1];
        if (key === '--out') { args.out = value; if (!inlineValue) i += 1; }
        else if (key === '--seed') { args.seed = Number(value); if (!inlineValue) i += 1; }
        else if (key === '--exclude-ids') {
            args.excludeIds = value.split(',').map((v) => Number(v.trim())).filter(Number.isFinite);
            if (!inlineValue) i += 1;
        } else if (key === '--help' || key === '-h') { args.help = true; }
    }
    return args;
}

// 공백·문장부호로 끊기지 않는 연속 구간(한글/영숫자)
const WORD_RUN = /[가-힣a-zA-Z0-9]+/g;

/**
 * 장소유형 판정용 이름. 끝에 붙은 괄호와 공백을 떼어낸다.
 * '남산공원(서울)' -> '남산공원', '동대문디자인플라자(DDP)' -> '동대문디자인플라자'
 */
function placeTypeOf(name) {
    return name.replace(/\s*\([^)]*\)\s*$/, '').trim();
}

/**
 * 이름 중간에서 부분 문자열을 뽑는다. 반드시 <b>한 단어 안에서</b> 자른다.
 *
 * 처음엔 이름 전체를 3글자씩 기계적으로 잘랐는데, 그러면
 * '경성 부민관 폭탄 의거지' -> '탄 의', '경복궁 (서울)' -> '(서울' 같은 조각이 나온다.
 * 사용자가 칠 리 없는 검색어인데, 이게 섞이면 검색 방식의 차이가 아니라
 * 골든셋의 결함을 측정하게 된다. 실제로 그랬다:
 *
 *   - '탄 의' : ngram 파서는 공백으로 먼저 끊고 각 조각에서 2글자 토큰을 만든다.
 *               '탄'과 '의'는 각각 1글자라 토큰이 하나도 안 생겨 매칭 자체가 불가능하다.
 *   - '(서울' : ngram은 문장부호를 색인하지 않아 '서울'만 남는다.
 *               검색 범위가 13건에서 17,650건으로 넓어져 정답이 상위권 밖으로 밀린다.
 *
 * 두 경우 모두 LIKE는 문자열을 그대로 비교하므로 통과한다. 그래서 측정하면
 * FULLTEXT가 재현율을 떨어뜨린 것처럼 보이는데, 실제로는 그렇지 않다.
 * 공백·문장부호가 없는 검색어만 비교하면 두 방식의 적중률은 91.7%로 같았다.
 *
 * 단어 안에서만 자르면 이 왜곡이 사라지고, 사용자가 실제로 칠 법한
 * 부분 검색어("국립중앙박물관" 중 "중앙박")만 남는다.
 */
function middleSubstring(name, rng) {
    const words = [...name.matchAll(WORD_RUN)]
        .map((m) => m[0])
        .filter((w) => [...w].length >= 4);
    if (words.length === 0) return null;

    const chars = [...rng.pick(words)];
    const len = Math.min(3, chars.length - 1);

    // start >= 1 이라야 '앞부분만(prefix)' 유형과 겹치지 않는다.
    const start = rng.int(1, chars.length - len + 1);
    return chars.slice(start, start + len).join('');
}

function main() {
    const args = parseArgs(process.argv);
    if (args.help) {
        console.log(`사용법: node search-eval/generate-goldenset.js [옵션]

  --out          출력 디렉터리 (기본 search-eval/out)
  --seed         난수 시드. 같은 시드면 항상 같은 골든셋 (기본 20260811)
  --exclude-ids  정답 후보에서 제외할 스팟 id (쉼표 구분)

spot_data.sql의 실제 스팟 135건에서 변형 검색어와 정답 매핑을 만든다.

--exclude-ids 에는 검색 대상이 아닌 스팟을 넣는다. 목록은 DB에서 뽑는다:
  mysql -u root -p picngo -N -e "SELECT GROUP_CONCAT(id) FROM spot
    WHERE id <= 135 AND (status <> 'APPROVED' OR is_active = 0);"`);
        return;
    }

    const { spots: allSpots } = loadCorpus(CORPUS_SQL);

    // 검색 대상이 아닌 스팟을 정답 후보에서 제외한다.
    //
    // spot_data.sql은 시드일 뿐이고 운영 DB의 상태는 그 뒤로 달라질 수 있다
    // (승인 취소, 비활성화 등). 검색 쿼리는 status='APPROVED' AND is_active=true로
    // 거르므로, 그 조건을 벗어난 스팟은 애초에 찾을 수 없는 게 정상이다.
    // 이런 스팟을 정답으로 두면 스팟 하나당 변형 7건이 통째로 실패로 집계되어
    // 모든 유형의 수치가 함께 오염된다.
    //
    // 제외 기준은 반드시 검색 동작과 독립적이어야 한다. "검색이 못 찾은 스팟을 뺀다"로
    // 하면 평가 대상으로 평가 기준을 정하는 순환 논리가 되어 실험이 무의미해진다.
    // 그래서 DB의 status/is_active로만 판단하고, 그 목록을 밖에서 받는다:
    //
    //   mysql -u root -p picngo -N -e "SELECT GROUP_CONCAT(id) FROM spot
    //     WHERE id <= 135 AND (status <> 'APPROVED' OR is_active = 0);"
    //   node search-eval/generate-goldenset.js --exclude-ids 1,2,4,5,6
    const excluded = new Set(args.excludeIds);
    const spots = allSpots.filter((s) => !excluded.has(s.id));

    const rng = createRng(args.seed);
    const queries = [];
    let nextId = 1;

    // 같은 유형 안에서 같은 검색어가 두 번 나오면 정답을 합친다.
    //
    // 두 가지 경로로 생긴다. 하나는 이름이 같은 스팟이 데이터에 둘 있는 경우
    // ('계남근린공원'이 2건이다). 다른 하나는 서로 다른 스팟에서 같은 조각이 나오는 경우다.
    // 어느 쪽이든 검색어가 같으면 정답도 같아야 채점이 성립한다 - 같은 말을 쳤는데
    // 채점 기준이 둘이면 한쪽은 반드시 틀린 것으로 집계된다.
    //
    // 유형이 다른 중복(어떤 스팟의 정확 일치가 다른 스팟의 앞부분과 같은 경우)은 합치지 않는다.
    // 재는 대상이 다르고, 유형별 성적을 따로 보는 것이 이 골든셋의 목적이기 때문이다.
    const byTypeAndKeyword = new Map();

    const add = (type, keyword, relevantSpotIds, source) => {
        if (!keyword || keyword.trim().length === 0) return;
        if (relevantSpotIds.length === 0) return;

        const dedupeKey = `${type} ${keyword}`;
        const existing = byTypeAndKeyword.get(dedupeKey);
        if (existing) {
            const merged = new Set([...existing.relevantSpotIds, ...relevantSpotIds]);
            existing.relevantSpotIds = [...merged].sort((a, b) => a - b);
            return;
        }

        const query = {
            id: `q${String(nextId++).padStart(4, '0')}`,
            type,
            keyword,
            relevantSpotIds,
            source,
        };
        byTypeAndKeyword.set(dedupeKey, query);
        queries.push(query);
    };

    // --- 1) 스팟 1건에서 파생되는 유형들 ---
    for (const spot of spots) {
        const name = spot.name;
        const src = { spotId: spot.id, spotName: name };

        add('exact', name, [spot.id], src);

        // 앞부분도 원본 이름에서 자른다(공백 제거 후 자르면 매칭이 깨진다 -
        // middleSubstring 주석 참고). 잘린 끝의 공백은 사용자가 치지 않으므로 떼어낸다.
        const chars = [...name];
        if (chars.length >= 3) {
            add('prefix', chars.slice(0, 3).join('').trim(), [spot.id], src);
        }

        add('substring', middleSubstring(name, rng), [spot.id], src);

        // 공백이 있는 이름만 "공백 제거" 변형이 의미가 있다.
        if (/\s/.test(name)) {
            add('spacing_removed', removeSpaces(name), [spot.id], src);
        }
        add('spacing_added', insertSpace(removeSpaces(name), rng), [spot.id], src);

        add('typo_adjacent', typoAdjacentKey(name, rng), [spot.id], src);
        add('typo_jongseong', typoDropJongsung(name, rng), [spot.id], src);
    }

    // --- 2) 동의어: 이름이 원어로 끝나는 스팟 전체가 정답 ---
    // endsWith를 쓰는 이유는 semantics.js의 SYNONYMS 주석 참고.
    //
    // 판정 전에 이름 끝 괄호를 뗀다. '남산공원(서울)'은 공원이 맞는데
    // 괄호 때문에 endsWith('공원')에 안 걸려서 정답 집합이 빠지기 때문이다.
    //
    // 검색어가 스팟 이름이나 주소에 글자 그대로 들어 있으면 동의어 시험이 성립하지 않는다.
    // '파크'는 '송도센트럴파크'에, '길'은 수많은 도로명 주소에 이미 있어서, 의미를
    // 이해하지 못해도 문자열 매칭만으로 걸린다. 그런 검색어는 뺀다.
    // (자연어 유형은 여러 단어로 된 구문이라 통째로 등장할 일이 없어 이 검사가 필요 없다.)
    const corpusText = spots.map((s) => `${s.name} ${s.address}`).join(' ');
    let literalDrops = 0;

    // 같은 말이 여러 장소유형의 동의어일 수 있다. '화원'은 식물원이기도 하고 정원이기도 하다.
    // 검색어마다 정답이 하나로 정해져야 채점이 되므로, 같은 검색어는 하나로 합치고
    // 정답 집합은 합집합으로 둔다. 사용자가 '화원'을 쳤을 때 둘 중 무엇이 나와도 맞는 답이다.
    const synonymAnswers = new Map();

    for (const { term, queries: synonymQueries } of SYNONYMS) {
        const relevant = spots
            .filter((s) => placeTypeOf(s.name).endsWith(term))
            .map((s) => s.id);

        for (const keyword of synonymQueries) {
            if (corpusText.includes(keyword)) {
                literalDrops += 1;
                continue;
            }

            if (!synonymAnswers.has(keyword)) {
                synonymAnswers.set(keyword, { terms: [], ids: new Set() });
            }
            const entry = synonymAnswers.get(keyword);
            entry.terms.push(term);
            relevant.forEach((id) => entry.ids.add(id));
        }
    }

    for (const [keyword, { terms, ids }] of synonymAnswers) {
        const relevant = [...ids].sort((a, b) => a - b);
        add('synonym', keyword, relevant, { term: terms.join('|'), relevantCount: relevant.length });
    }

    // --- 3) 자연어: 해당 카테고리가 달린 스팟 전체가 정답 ---
    for (const { category, queries: naturalQueries } of NATURAL_QUERIES) {
        const relevant = spots.filter((s) => s.categories.includes(category)).map((s) => s.id);
        for (const keyword of naturalQueries) {
            add('natural', keyword, relevant, { category, relevantCount: relevant.length });
        }
    }

    const byType = {};
    for (const q of queries) {
        byType[q.type] = (byType[q.type] ?? 0) + 1;
    }

    const goldenset = {
        meta: {
            generatedBy: 'search-eval/generate-goldenset.js',
            seed: args.seed,
            corpus: 'src/main/resources/spot_data.sql',
            corpusSpotCount: spots.length,
            excludedSpotIds: args.excludeIds,
            excludedReason: args.excludeIds.length > 0
                ? 'status <> APPROVED 또는 is_active = false 라서 검색 대상이 아님 (검색 동작과 무관한 기준)'
                : null,
            totalQueries: queries.length,
            queriesByType: byType,
            variantMeta: VARIANT_META,
            limitations: [
                '실사용 검색 로그가 아니라 데이터에서 파생한 검색어다. 실제 사용자의 검색어 분포를 대표한다고 주장할 수 없다.',
                '변형 규칙(어떤 오타를 낼지, 어떤 동의어를 쓸지)은 사람이 골랐다. 이 선택 자체가 편향이다.',
                'natural 유형의 정답 집합은 spot_categories 태깅에 의존한다. 장면형 카테고리는 키워드 추출 기반이라 오탐이 있다.',
                '동의어 중 정답이 1~2건뿐인 것이 있다. 그런 검색어는 한 건 차이로 적중률이 크게 흔들리므로 유형 전체 수치로 읽을 것.',
                '정답은 실제 스팟 135건으로 한정된다. 필러 스팟이 정답이 되는 경우는 없다.',
            ],
        },
        queries,
    };

    mkdirSync(args.out, { recursive: true });
    const outPath = join(args.out, 'goldenset.json');
    writeFileSync(outPath, `${JSON.stringify(goldenset, null, 2)}\n`, 'utf8');

    console.log(`골든셋 생성: 검색어 ${queries.length}건 (스팟 ${spots.length}건 기반, seed=${args.seed})`);
    if (args.excludeIds.length > 0) {
        console.log(`  제외한 스팟 ${args.excludeIds.length}건: ${args.excludeIds.join(', ')} (검색 대상 아님)`);
    }
    if (literalDrops > 0) {
        console.log(`  버린 동의어 ${literalDrops}건: 이름/주소에 글자 그대로 있어 동의어 시험이 안 됨`);
    }
    console.log('');
    for (const [type, count] of Object.entries(byType)) {
        const meta = VARIANT_META[type];
        const mark = meta.expectation === 'hit' ? '적중 기대' : '실패 예상';
        console.log(`  ${type.padEnd(17)} ${String(count).padStart(4)}건   ${meta.label} (${mark})`);
    }
    console.log('');
    console.log(`  -> ${outPath.replace(PROJECT_ROOT, '.').replace(/\\/g, '/')}`);
}

main();
