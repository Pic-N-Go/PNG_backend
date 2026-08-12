#!/usr/bin/env node
// 벡터 검색 품질 평가. DB도 서버도 필요 없다.
//
//   node search-eval/evaluate-vector.js
//
// embed-corpus.js가 만들어둔 벡터로 유사도만 계산한다. 인프라를 고르기 전에
// "의미 검색이 정말 이 문제를 푸는가"부터 답하려는 것이다.
// 여기서 효과가 없으면 pgvector든 Elasticsearch든 고를 이유가 없고,
// 효과가 있으면 그 수치가 저장소 선택의 근거가 된다.
//
// 지표는 문자열 검색 평가(evaluate-quality.js)와 같은 코드로 계산한다.
// 다만 무결과율은 비교 대상이 아니다 - 벡터 검색은 아무리 관련 없어도
// 항상 상위 k건을 돌려주므로 무결과가 0%로 고정된다.
// 비교는 적중률·Recall·MRR로 해야 한다.

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { summarize, buildReport } from './lib/report.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(HERE, '..');

function parseArgs(argv) {
    const args = {
        embeddings: join(HERE, 'out', 'embeddings.json'),
        goldenset: join(HERE, 'out', 'goldenset.json'),
        out: join(HERE, 'out'),
        k: 20,
        label: '',
        filler: null,
    };
    for (let i = 2; i < argv.length; i += 1) {
        const [key, inlineValue] = argv[i].split('=');
        const value = inlineValue ?? argv[i + 1];
        const step = () => { if (!inlineValue) i += 1; };
        if (key === '--embeddings') { args.embeddings = value; step(); }
        else if (key === '--goldenset') { args.goldenset = value; step(); }
        else if (key === '--out') { args.out = value; step(); }
        else if (key === '--k') { args.k = Number(value); step(); }
        else if (key === '--label') { args.label = value; step(); }
        else if (key === '--filler') { args.filler = Number(value); step(); }
        else if (key === '--help' || key === '-h') { args.help = true; }
    }
    return args;
}

// 필러 스팟의 id는 여기서부터 시작한다(generate-seed.js와 같은 값).
const FILLER_START_ID = 1_000_000;

/**
 * 저장된 벡터에서 필러를 앞에서부터 N건만 남긴다. 정답 후보는 전부 남긴다.
 *
 * <p>모델을 서로 비교할 때 쓴다. 한쪽 모델은 필러 10만 건까지 벡터를 만들어뒀는데
 * 다른 쪽은 1,000건까지만 만들 수 있다면, 많은 쪽을 잘라서 조건을 맞춰야 한다.
 * 후보 수가 다르면 문제 난이도가 달라서 점수를 나란히 놓을 수 없다.
 *
 * <p>필러를 만든 순서가 두 파일에서 같기 때문에(시드 SQL을 앞에서부터 읽는다)
 * 앞에서 N건을 자르면 양쪽이 정확히 같은 스팟을 보게 된다.
 */
function limitFiller(spots, limit) {
    const real = spots.filter((s) => s.id < FILLER_START_ID);
    const filler = spots.filter((s) => s.id >= FILLER_START_ID).slice(0, limit);
    return [...real, ...filler];
}

// 벡터는 float32 이진값을 base64로 담아둔 것이다(embed-corpus.js 참고).
function decodeVector(encoded) {
    const buffer = Buffer.from(encoded, 'base64');
    return new Float32Array(buffer.buffer, buffer.byteOffset, buffer.byteLength / 4);
}

/**
 * 후보 벡터를 하나의 평평한 배열로 합친다.
 *
 * <p>후보가 10만 건이면 검색어 하나당 10만 개의 객체를 만들게 되는데, 검색어가
 * 853건이니 8천만 개다. 계산보다 할당과 GC에 시간이 더 든다.
 * 미리 이어붙여 두면 그 비용이 사라진다.
 */
function flatten(spots) {
    const dim = decodeVector(spots[0].vector).length;
    const ids = new Int32Array(spots.length);
    const matrix = new Float32Array(spots.length * dim);

    spots.forEach((s, i) => {
        ids[i] = s.id;
        matrix.set(decodeVector(s.vector), i * dim);
    });

    return { ids, matrix, dim, count: spots.length };
}

/**
 * 검색어 벡터와 모든 후보의 유사도를 재서 상위 k개를 돌려준다.
 * 후보 전체를 훑는 완전탐색이다. 근사 색인(HNSW 등)을 쓰지 않으므로
 * 이 방식으로 잰 값은 정확도의 상한이 된다.
 *
 * <p>전부 정렬하지 않고 상위 k개만 유지한다. 후보 10만 건을 매번 정렬하면
 * 필요 없는 일에 대부분의 시간을 쓰게 된다.
 */
function topK(queryVector, { ids, matrix, dim, count }, k) {
    const topIds = new Int32Array(k);
    const topScores = new Float64Array(k).fill(-Infinity);
    let filled = 0;

    for (let i = 0; i < count; i += 1) {
        const offset = i * dim;
        let score = 0;
        for (let d = 0; d < dim; d += 1) score += queryVector[d] * matrix[offset + d];

        if (filled === k && score <= topScores[k - 1]) continue;

        // 점수 내림차순을 유지하며 끼워 넣는다.
        let pos = Math.min(filled, k - 1);
        while (pos > 0 && topScores[pos - 1] < score) {
            topScores[pos] = topScores[pos - 1];
            topIds[pos] = topIds[pos - 1];
            pos -= 1;
        }
        topScores[pos] = score;
        topIds[pos] = ids[i];
        if (filled < k) filled += 1;
    }

    return Array.from({ length: filled }, (_, i) => ({ id: topIds[i], score: topScores[i] }));
}

function main() {
    const args = parseArgs(process.argv);
    if (args.help) {
        console.log(`사용법: node search-eval/evaluate-vector.js [옵션]

  --embeddings  벡터 캐시 경로 (기본 search-eval/out/embeddings.json)
  --goldenset   골든셋 JSON (변형 유형 설명과 한계를 가져온다)
  --out         리포트 출력 디렉터리
  --k           상위 몇 건까지 볼지 (기본 20)
  --label       리포트에 남길 실험 라벨

먼저 embed-corpus.js로 벡터를 만들어야 한다.`);
        return;
    }

    let embeddings;
    try {
        embeddings = JSON.parse(readFileSync(args.embeddings, 'utf8'));
    } catch {
        console.error(`벡터 캐시를 읽지 못했다: ${args.embeddings}`);
        console.error('먼저 실행: node search-eval/embed-corpus.js');
        process.exit(1);
    }

    const goldenset = JSON.parse(readFileSync(args.goldenset, 'utf8'));

    // 캐시를 만든 뒤 골든셋을 다시 생성했다면 검색어가 어긋난다.
    // 그대로 두면 엉뚱한 정답으로 채점되므로 여기서 막는다.
    if (embeddings.queries.length !== goldenset.queries.length) {
        console.error(`골든셋과 벡터 캐시가 맞지 않는다 `
            + `(캐시 ${embeddings.queries.length}건, 골든셋 ${goldenset.queries.length}건).`);
        console.error('골든셋을 다시 만들었다면 embed-corpus.js도 다시 실행할 것.');
        process.exit(1);
    }

    const usedSpots = args.filler === null
        ? embeddings.spots
        : limitFiller(embeddings.spots, args.filler);

    if (args.filler !== null) {
        console.log(`후보를 ${usedSpots.length.toLocaleString()}건으로 줄여서 평가한다 `
            + `(저장된 ${embeddings.spots.length.toLocaleString()}건 중 필러 앞 ${args.filler.toLocaleString()}건만 사용).`);
    }

    const candidates = flatten(usedSpots);

    const results = embeddings.queries.map((q) => {
        const queryVector = decodeVector(q.vector);
        const started = performance.now();
        const ranked = topK(queryVector, candidates, args.k);
        const elapsedMs = performance.now() - started;

        const relevant = new Set(q.relevantSpotIds);
        const hitIds = ranked.filter((r) => relevant.has(r.id));
        const firstHitIndex = ranked.findIndex((r) => relevant.has(r.id));

        return {
            id: q.id,
            type: q.type,
            keyword: q.keyword,
            // 벡터 검색은 늘 k건을 돌려주므로 "결과 없음"이 없다.
            totalElements: ranked.length,
            returned: ranked.length,
            relevantCount: q.relevantSpotIds.length,
            hitCount: hitIds.length,
            firstHitRank: firstHitIndex >= 0 ? firstHitIndex + 1 : null,
            topScore: ranked[0]?.score ?? 0,
            elapsedMs,
        };
    });

    const summary = summarize(results, args.k);
    const runAt = new Date().toISOString();

    const conditions = [
        ['실행 시각', runAt],
        ['검색 방식', '벡터 유사도 (오프라인)'],
        ['임베딩 제공자', embeddings.meta.provider ?? '(기록 없음)'],
        ['임베딩 모델', embeddings.meta.model],
        ['벡터 차원', embeddings.meta.dimensions],
        ['임베딩한 텍스트', embeddings.meta.withOverview && embeddings.meta.overviewCount > 0
            ? `이름 + 주소 + 설명문(${embeddings.meta.overviewCount}건)`
            : '이름 + 주소'],
        ['후보 스팟', `${usedSpots.length.toLocaleString()}건`
            + (usedSpots.length > (embeddings.meta.realSpotCount ?? 130)
                ? ` (정답 후보 ${embeddings.meta.realSpotCount} + 필러 ${(usedSpots.length - (embeddings.meta.realSpotCount ?? 130)).toLocaleString()})`
                : ' — 필러 없음')],
        ['검색어 수', `${results.length}`],
        ['k (상위 몇 건까지)', args.k],
        ['골든셋 시드', embeddings.meta.goldensetSeed],
    ];
    if (args.label) conditions.push(['라벨', args.label]);

    const report = buildReport({
        title: '검색 품질 평가 리포트 (벡터)',
        conditions,
        summary,
        variantMeta: goldenset.meta.variantMeta,
        limitations: [
            ...goldenset.meta.limitations,
            '벡터 검색은 관련이 없어도 늘 상위 k건을 돌려주므로 무결과율이 0%로 고정된다. 문자열 검색의 무결과율과 비교하면 안 된다.',
            embeddings.meta.withOverview && embeddings.meta.overviewCount > 0
                ? '설명문이 있는 스팟과 없는 스팟이 섞여 있어 조건이 균일하지 않다.'
                : '실제 스팟에는 설명문이 없어 이름과 주소만 임베딩했다. 결과가 나쁘다면 검색 방식의 한계가 아니라 설명 텍스트 부재가 원인일 수 있다.',
            '실제 DB가 아니라 후보 전체를 완전탐색해 순위를 매겼다. 근사 최근접 색인(HNSW 등)을 쓰면 정확도가 조금 내려갈 수 있다.',
            embeddings.meta.fillerCount
                ? `후보 ${embeddings.meta.spotCount.toLocaleString()}건으로 쟀다. 문자열 검색 측정은 100,135건 기준이므로, 후보 수가 다르면 난이도가 달라 그대로 비교할 수 없다.`
                : '⚠️ 필러 없이 정답 후보만으로 쟀다. 상위 20건이 전체의 15%라 아무렇게나 골라도 자주 맞는다. 문자열 검색(100,135건 기준)과 비교하면 안 된다 - --filler 옵션으로 다시 잴 것.',
        ],
        k: args.k,
        notes: [
            '- **무결과율**: 벡터 검색에서는 항상 0%다. 지표로 쓰지 말 것.',
        ],
    });

    mkdirSync(args.out, { recursive: true });
    const stamp = runAt.replace(/[:.]/g, '-');
    const reportPath = join(args.out, `vector-report-${stamp}.md`);
    const rawPath = join(args.out, `vector-raw-${stamp}.json`);

    writeFileSync(reportPath, report, 'utf8');
    writeFileSync(rawPath, `${JSON.stringify({ meta: { runAt, label: args.label, embedding: embeddings.meta }, summary, results }, null, 2)}\n`, 'utf8');

    console.log(report.split('\n## 읽는 법')[0]);
    console.log(`리포트: ${reportPath.replace(PROJECT_ROOT, '.').replace(/\\/g, '/')}`);
    console.log(`원자료: ${rawPath.replace(PROJECT_ROOT, '.').replace(/\\/g, '/')}`);
}

main();
