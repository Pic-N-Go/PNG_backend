#!/usr/bin/env node
// 검색 품질 평가 러너.
//
//   node search-eval/evaluate-quality.js
//   node search-eval/evaluate-quality.js --base-url http://localhost:8080 --k 20
//
// 골든셋의 검색어를 실제 검색 API에 던지고, 정답 스팟이 결과에 들어오는지 센다.
// 앱이 떠 있어야 한다.
//
// 지표를 여러 개 내는 이유 - "안 나왔다"에는 서로 다른 원인이 섞여 있다:
//
//   무결과율        totalElements == 0. 아예 매칭이 안 된 경우.
//                   LIKE '%키워드%'가 문자열을 못 잡았다는 뜻.
//   적중률@k        정답이 상위 k건 안에 있는 비율. 사용자가 실제로 보는 것.
//   Recall@k        정답 집합 중 몇 %를 상위 k건에 담았는지. 정답이 여러 건인
//                   동의어/자연어 유형에서 의미가 있다.
//   매칭O·순위밖    결과는 나왔는데 정답이 상위 k건 밖으로 밀린 경우.
//                   이건 매칭 문제가 아니라 랭킹 문제다. 현재 검색은
//                   createdAt DESC 정렬이라 관련도 개념 자체가 없어서,
//                   데이터가 늘수록 이 값이 커질 것으로 예상된다.
//                   무결과율만 보면 이 실패를 놓친다.
//
// 응답시간도 같이 찍지만 참고값이다. 동시성 1~4의 순차 호출이라
// 부하 상태의 지연이 아니다. 성능 수치는 load-test/search-load.js로 낸다.

import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
// 집계와 리포트 작성은 벡터 평가(evaluate-vector.js)와 같은 코드를 쓴다.
// 두 리포트를 나란히 비교할 것이라, 계산이 조금이라도 다르면
// 그 차이가 검색 방식의 차이로 잘못 읽힌다.
import { summarize, buildReport } from './lib/report.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(HERE, '..');

function parseArgs(argv) {
    const args = {
        baseUrl: process.env.BASE_URL || 'http://localhost:8080',
        goldenset: join(HERE, 'out', 'goldenset.json'),
        out: join(HERE, 'out'),
        k: 20,
        concurrency: 4,
        label: '',
    };
    for (let i = 2; i < argv.length; i += 1) {
        const [key, inlineValue] = argv[i].split('=');
        const value = inlineValue ?? argv[i + 1];
        const step = () => { if (!inlineValue) i += 1; };
        if (key === '--base-url') { args.baseUrl = value; step(); }
        else if (key === '--goldenset') { args.goldenset = value; step(); }
        else if (key === '--out') { args.out = value; step(); }
        else if (key === '--k') { args.k = Number(value); step(); }
        else if (key === '--concurrency') { args.concurrency = Number(value); step(); }
        else if (key === '--label') { args.label = value; step(); }
        else if (key === '--help' || key === '-h') { args.help = true; }
    }
    return args;
}

async function fetchJson(url) {
    const started = performance.now();
    const res = await fetch(url, { headers: { Accept: 'application/json' } });
    const elapsedMs = performance.now() - started;
    if (!res.ok) {
        throw new Error(`HTTP ${res.status} ${res.statusText} - ${url}`);
    }
    return { body: await res.json(), elapsedMs };
}

/** 앱에 물어서 현재 스팟 총건수를 얻는다. 실험 조건을 리포트에 자동으로 남기기 위함. */
async function readCorpusSize(baseUrl) {
    try {
        const { body } = await fetchJson(`${baseUrl}/spots?page=0&size=1`);
        return body.totalElements ?? null;
    } catch {
        return null;
    }
}

async function runQuery(baseUrl, query, k) {
    const url = `${baseUrl}/spots/search?keyword=${encodeURIComponent(query.keyword)}&page=0&size=${k}`;
    const { body, elapsedMs } = await fetchJson(url);

    const returnedIds = (body.content ?? []).map((s) => s.id);
    const relevant = new Set(query.relevantSpotIds);
    const hitIds = returnedIds.filter((id) => relevant.has(id));
    const firstHitIndex = returnedIds.findIndex((id) => relevant.has(id));

    return {
        id: query.id,
        type: query.type,
        keyword: query.keyword,
        totalElements: body.totalElements ?? 0,
        returned: returnedIds.length,
        relevantCount: query.relevantSpotIds.length,
        hitCount: hitIds.length,
        firstHitRank: firstHitIndex >= 0 ? firstHitIndex + 1 : null,
        elapsedMs,
    };
}

// 동시 요청 수를 제한하며 전부 실행한다. 서버를 부하 상태로 만들면
// 품질 측정 중에 타임아웃이 섞여 결과가 오염된다.
async function runAll(baseUrl, queries, k, concurrency, onProgress) {
    const results = new Array(queries.length);
    let cursor = 0;
    let done = 0;

    async function worker() {
        while (cursor < queries.length) {
            const index = cursor++;
            results[index] = await runQuery(baseUrl, queries[index], k);
            done += 1;
            onProgress(done, queries.length);
        }
    }

    await Promise.all(
        Array.from({ length: Math.max(1, concurrency) }, () => worker())
    );
    return results;
}


async function main() {
    const args = parseArgs(process.argv);
    if (args.help) {
        console.log(`사용법: node search-eval/evaluate-quality.js [옵션]

  --base-url     대상 서버 (기본 http://localhost:8080, 환경변수 BASE_URL도 가능)
  --goldenset    골든셋 JSON 경로 (기본 search-eval/out/goldenset.json)
  --out          리포트 출력 디렉터리 (기본 search-eval/out)
  --k            상위 몇 건까지 볼지 (기본 20)
  --concurrency  동시 요청 수 (기본 4)
  --label        리포트에 남길 실험 라벨 (예: "필러 10만건")

앱이 떠 있어야 한다. 먼저 generate-goldenset.js를 실행해 골든셋을 만들 것.`);
        return;
    }

    let goldenset;
    try {
        goldenset = JSON.parse(readFileSync(args.goldenset, 'utf8'));
    } catch (e) {
        console.error(`골든셋을 읽지 못했다: ${args.goldenset}`);
        console.error('먼저 실행: node search-eval/generate-goldenset.js');
        process.exit(1);
    }

    // 서버가 살아있는지 먼저 확인한다. 860건을 던지고 나서 전부 실패하는 것보다 낫다.
    try {
        await fetchJson(`${args.baseUrl}/spots/search?keyword=%ED%99%95%EC%9D%B8&page=0&size=1`);
    } catch (e) {
        console.error(`검색 API에 접근할 수 없다: ${args.baseUrl}`);
        console.error(`  ${e.message}`);
        console.error('앱이 떠 있는지 확인할 것 (./gradlew bootRun)');
        process.exit(1);
    }

    const corpusSize = await readCorpusSize(args.baseUrl);

    console.log(`검색어 ${goldenset.queries.length}건 평가 시작 (k=${args.k}, 동시 ${args.concurrency})`);
    const results = await runAll(
        args.baseUrl, goldenset.queries, args.k, args.concurrency,
        (done, total) => {
            if (done % 50 === 0 || done === total) {
                process.stdout.write(`\r  진행 ${done}/${total}`);
            }
        }
    );
    process.stdout.write('\n');

    const summary = summarize(results, args.k);
    const meta = {
        runAt: new Date().toISOString(),
        baseUrl: args.baseUrl,
        corpusSize,
        totalQueries: goldenset.queries.length,
        k: args.k,
        label: args.label,
        goldenset: goldenset.meta,
    };

    const conditions = [
        ['실행 시각', meta.runAt],
        ['검색 방식', '문자열 색인 (실행 중인 앱)'],
        ['대상', meta.baseUrl],
        ['스팟 총건수', corpusSize === null ? '확인 실패' : corpusSize.toLocaleString()],
        ['검색어 수', meta.totalQueries],
        ['정답 후보', `실제 스팟 ${goldenset.meta.corpusSpotCount}건`],
        ['k (상위 몇 건까지)', args.k],
        ['골든셋 시드', goldenset.meta.seed],
    ];
    if (args.label) conditions.push(['라벨', args.label]);

    mkdirSync(args.out, { recursive: true });
    const report = buildReport({
        title: '검색 품질 평가 리포트',
        conditions,
        summary,
        variantMeta: goldenset.meta.variantMeta,
        limitations: goldenset.meta.limitations,
        k: args.k,
        notes: [
            '- **매칭O·순위밖**: 현재 검색은 `created_at DESC` 정렬이라 관련도 개념이 없어서,',
            '  데이터가 늘수록 이 값이 커진다. 무결과율만 보면 이 실패는 보이지 않는다.',
        ],
    });
    const stamp = meta.runAt.replace(/[:.]/g, '-');
    const reportPath = join(args.out, `quality-report-${stamp}.md`);
    const rawPath = join(args.out, `quality-raw-${stamp}.json`);

    writeFileSync(reportPath, report, 'utf8');
    writeFileSync(rawPath, `${JSON.stringify({ meta, summary, results }, null, 2)}\n`, 'utf8');

    console.log('');
    console.log(report.split('\n## 읽는 법')[0]);
    console.log(`리포트: ${reportPath.replace(PROJECT_ROOT, '.').replace(/\\/g, '/')}`);
    console.log(`원자료: ${rawPath.replace(PROJECT_ROOT, '.').replace(/\\/g, '/')}`);
}

main().catch((e) => {
    console.error(e);
    process.exit(1);
});
