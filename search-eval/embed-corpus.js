#!/usr/bin/env node
// 스팟과 골든셋 검색어를 임베딩해서 파일로 저장한다.
//
//   $env:OPENAI_API_KEY="sk-..."       (PowerShell)
//   node search-eval/embed-corpus.js
//
// 평가와 분리한 이유: 임베딩은 돈이 들고 시간이 걸린다. 한 번 만들어 캐시해두면
// 평가는 몇 번이든 공짜로 다시 돌릴 수 있다.
//
// [무엇을 임베딩하는가 - 이 실험의 가장 중요한 제약]
// 실제 스팟 130건에는 설명문(overview)이 없다. 이름과 주소뿐이다.
// 그래서 '야경 예쁜 곳'으로 '남산 팔각정'을 찾으려면 임베딩 모델이 이름만 보고
// 그곳이 야경 명소라는 걸 알아야 한다. 즉 모델의 세계 지식에 기대는 셈이다.
//
// 결과가 나쁘게 나오면 그건 "벡터 검색이 안 된다"가 아니라 "설명 텍스트가 없어서
// 안 된다"일 수 있다. 두 가지는 해결책이 완전히 다르다(기술 교체 vs 데이터 확보).
// 그 구분을 위해 --with-overview 옵션으로 설명문을 붙인 조건도 잴 수 있게 해뒀다.
//
// [카테고리를 넣지 않는 이유]
// 스팟에 달린 카테고리(NIGHT_VIEW 등)를 텍스트로 넣으면 자연어 검색어의 적중률이
// 크게 오른다. 하지만 그 카테고리가 바로 정답 판정 기준이다. 정답을 입력에 넣고
// 맞히는 셈이라 측정이 무의미해진다.

import { readFileSync, writeFileSync, mkdirSync, existsSync, rmSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadCorpus, loadFillerSpots } from './lib/corpus.js';
import { embedCorpus, DEFAULT_MODELS } from './lib/embedding.js';

// 벡터는 실수 384~1536개짜리라 JSON 숫자로 적으면 파일이 금방 수백 MB가 된다.
// float32 이진값을 base64로 담으면 정확도를 잃지 않으면서 크기가 크게 준다.
const encodeVector = (v) => Buffer.from(Float32Array.from(v).buffer).toString('base64');

const HERE = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = join(HERE, '..');
const CORPUS_SQL = join(PROJECT_ROOT, 'src', 'main', 'resources', 'spot_data.sql');

function parseArgs(argv) {
    const args = {
        provider: 'gemini',
        apiKey: null,
        model: null,
        goldenset: join(HERE, 'out', 'goldenset.json'),
        out: join(HERE, 'out'),
        baseUrl: process.env.BASE_URL || 'http://localhost:8080',
        withOverview: false,
        excludeIds: [],
        filler: 0,
        seedDir: join(HERE, 'out', 'seed'),
    };
    for (let i = 2; i < argv.length; i += 1) {
        const [key, inlineValue] = argv[i].split('=');
        const value = inlineValue ?? argv[i + 1];
        const step = () => { if (!inlineValue) i += 1; };
        if (key === '--provider') { args.provider = value; step(); }
        else if (key === '--api-key') { args.apiKey = value; step(); }
        else if (key === '--model') { args.model = value; step(); }
        else if (key === '--goldenset') { args.goldenset = value; step(); }
        else if (key === '--out') { args.out = value; step(); }
        else if (key === '--base-url') { args.baseUrl = value; step(); }
        else if (key === '--exclude-ids') {
            args.excludeIds = value.split(',').map((v) => Number(v.trim())).filter(Number.isFinite);
            step();
        } else if (key === '--filler') { args.filler = Number(value); step(); }
        else if (key === '--seed-dir') { args.seedDir = value; step(); }
        else if (key === '--with-overview') { args.withOverview = true; }
        else if (key === '--help' || key === '-h') { args.help = true; }
    }
    return args;
}

/**
 * 앱에서 설명문을 가져와 붙인다. spot_data.sql에는 overview가 없지만,
 * TourAPI 동기화를 돌렸다면 DB에는 들어 있을 수 있다.
 */
async function fetchOverviews(spots, baseUrl) {
    const overviews = new Map();
    let failures = 0;

    for (const spot of spots) {
        try {
            const res = await fetch(`${baseUrl}/spots/${spot.id}`, { headers: { Accept: 'application/json' } });
            if (!res.ok) { failures += 1; continue; }
            const body = await res.json();
            if (body.overview) overviews.set(spot.id, body.overview);
        } catch {
            failures += 1;
        }
    }

    return { overviews, failures };
}

function spotText(spot, overviews) {
    const overview = overviews.get(spot.id);
    return overview
        ? `${spot.name} ${spot.address} ${overview}`
        : `${spot.name} ${spot.address}`;
}

async function main() {
    const args = parseArgs(process.argv);
    if (args.help) {
        console.log(`사용법: node search-eval/embed-corpus.js [옵션]

  --provider       gemini | openai | local (기본 gemini)
                     gemini = ${DEFAULT_MODELS.gemini}. 카드 등록 없이 무료 한도 사용 가능.
                     openai = ${DEFAULT_MODELS.openai}. 키와 선불 크레딧 필요.
                     local  = 내 컴퓨터에서 ${DEFAULT_MODELS.local} 실행. 무료지만
                              먼저 'cd search-eval && npm install @huggingface/transformers' 필요.
  --model          모델 이름 직접 지정 (기본값은 제공자에 따라 위와 같음)
  --api-key        API 키. 안 주면 환경변수에서 읽는다
                     gemini -> GEMINI_API_KEY,  openai -> OPENAI_API_KEY
  --goldenset      골든셋 JSON 경로
  --out            출력 디렉터리 (기본 search-eval/out)
  --exclude-ids    스팟에서 제외할 id (골든셋과 같은 값을 줄 것)
  --filler N       필러 스팟 N건을 후보에 섞는다 (기본 0)
                     실제 스팟 130건만 후보로 두면 상위 20건이 전체의 15%가 되어
                     아무렇게나 골라도 자주 맞는다. 문자열 검색은 10만 건 사이에서
                     20건을 고르므로, 섞지 않으면 벡터에 크게 유리한 비교가 된다.
  --seed-dir       필러를 읽어올 시드 SQL 디렉터리 (기본 search-eval/out/seed)
  --with-overview  앱에서 설명문을 가져와 함께 임베딩 (앱이 떠 있어야 함)
  --base-url       --with-overview 용 앱 주소

결과는 out/embeddings.json에 저장된다. 평가는 evaluate-vector.js로 한다.`);
        return;
    }

    if (!['local', 'openai', 'gemini'].includes(args.provider)) {
        console.error(`--provider는 gemini, openai, local 중 하나여야 한다 (받은 값: ${args.provider})`);
        process.exit(1);
    }

    // 제공자마다 환경변수 이름이 다르다. --api-key를 직접 준 경우가 우선.
    args.apiKey = args.apiKey
        ?? (args.provider === 'gemini' ? process.env.GEMINI_API_KEY : process.env.OPENAI_API_KEY);

    let goldenset;
    try {
        goldenset = JSON.parse(readFileSync(args.goldenset, 'utf8'));
    } catch {
        console.error(`골든셋을 읽지 못했다: ${args.goldenset}`);
        console.error('먼저 실행: node search-eval/generate-goldenset.js');
        process.exit(1);
    }

    // 골든셋이 쓴 것과 같은 스팟 집합이어야 정답 id가 맞아떨어진다.
    const excludeIds = args.excludeIds.length > 0
        ? args.excludeIds
        : (goldenset.meta.excludedSpotIds ?? []);
    const excluded = new Set(excludeIds);
    const spots = loadCorpus(CORPUS_SQL).spots.filter((s) => !excluded.has(s.id));

    let overviews = new Map();
    if (args.withOverview) {
        console.log(`앱에서 설명문 조회 중... (${args.baseUrl})`);
        const result = await fetchOverviews(spots, args.baseUrl);
        overviews = result.overviews;
        console.log(`  설명문 있는 스팟: ${overviews.size} / ${spots.length}`
            + (result.failures > 0 ? ` (조회 실패 ${result.failures}건)` : ''));
        if (overviews.size === 0) {
            console.log('  설명문이 하나도 없다. 이름과 주소만으로 임베딩한다.');
        }
    }

    // 필러는 정답이 될 수 없다. 후보 집합을 실제 검색과 비슷한 크기로 만들어
    // 난이도를 맞추는 역할만 한다.
    let filler = [];
    if (args.filler > 0) {
        try {
            filler = loadFillerSpots(args.seedDir, args.filler);
        } catch {
            console.error(`시드 SQL을 읽지 못했다: ${args.seedDir}`);
            console.error('먼저 실행: node search-eval/generate-seed.js --count 100000');
            process.exit(1);
        }
        if (filler.length < args.filler) {
            console.log(`필러가 ${filler.length}건뿐이다 (요청 ${args.filler}건). 시드를 더 만들면 늘릴 수 있다.`);
        }
    }

    const candidates = [
        ...spots.map((s) => ({ id: s.id, name: s.name, text: spotText(s, overviews), real: true })),
        ...filler.map((s) => ({ id: s.id, name: s.name, text: `${s.name} ${s.address}`, real: false })),
    ];

    const spotTexts = candidates.map((c) => c.text);
    const queryTexts = goldenset.queries.map((q) => q.keyword);

    console.log('');
    console.log(`임베딩 대상: 후보 ${candidates.length}건 `
        + `(정답 후보 ${spots.length} + 필러 ${filler.length}) + 검색어 ${queryTexts.length}건`);
    console.log(`제공자: ${args.provider}${args.provider === 'local' ? ' (첫 실행은 모델 내려받느라 몇 분 걸린다)' : ''}`);

    // 중간 저장 파일. 무료 한도에 걸려 멈춰도 여기까지는 남으므로,
    // 다시 실행하면 이어서 한다. 한도가 하루 단위로 풀리는 API를 쓸 때 특히 중요하다.
    //
    // 입력이 바뀌면(스팟이나 검색어가 달라지면) 이어서 하면 안 된다. 순서가 어긋나
    // 엉뚱한 스팟에 엉뚱한 벡터가 붙는데, 에러 없이 점수만 이상해진다.
    // 그래서 입력 목록의 지문(해시)을 같이 저장해두고 다를 때는 버린다.
    mkdirSync(args.out, { recursive: true });
    const checkpointPath = join(args.out, 'embeddings.partial.json');
    const fingerprint = createHash('sha256')
        .update(`${args.provider}|${args.model ?? 'default'}|${spotTexts.length}|${queryTexts.length}`)
        .update(spotTexts.join(' '))
        .update(queryTexts.join(' '))
        .digest('hex');

    let resumeVectors = [];
    if (existsSync(checkpointPath)) {
        try {
            const saved = JSON.parse(readFileSync(checkpointPath, 'utf8'));
            if (saved.fingerprint === fingerprint) {
                resumeVectors = saved.vectors;
                console.log(`이어서 시작한다: 이미 만들어둔 ${resumeVectors.length.toLocaleString()}건은 건너뛴다.`);
            } else {
                console.log('중간 저장 파일이 있지만 입력이 달라졌다. 처음부터 다시 만든다.');
            }
        } catch {
            console.log('중간 저장 파일을 읽지 못했다. 처음부터 다시 만든다.');
        }
    }

    // 0으로 시작해야 첫 묶음이 끝나자마자 한 번 저장된다.
    // Date.now()로 시작하면 처음 10초 안에 실패했을 때 아무것도 안 남는다.
    let lastSaved = 0;
    let latestVectors = resumeVectors;

    const saveCheckpoint = (vectors) => {
        writeFileSync(checkpointPath, JSON.stringify({ fingerprint, vectors }), 'utf8');
        lastSaved = Date.now();
    };

    let embedded;
    try {
        embedded = await embedCorpus({
            passages: spotTexts,
            queries: queryTexts,
            provider: args.provider,
            model: args.model,
            apiKey: args.apiKey,
            resumeVectors,
            onProgress: (done, total) => process.stdout.write(`\r  진행 ${done}/${total}`),
            onBatch: (vectors) => {
                latestVectors = vectors;
                // 매번 쓰면 수백 MB를 반복해서 쓰게 된다. 10초에 한 번으로 제한한다.
                if (Date.now() - lastSaved < 10_000) return;
                saveCheckpoint(vectors);
            },
        });
    } catch (e) {
        // 실패해도 여기까지 만든 건 남긴다. 다시 실행하면 이어서 한다.
        if (latestVectors.length > 0) {
            saveCheckpoint(latestVectors);
            process.stdout.write('\n');
            console.error(`중간까지 만든 ${latestVectors.length.toLocaleString()}건을 저장했다. `
                + '같은 명령을 다시 실행하면 이어서 한다.');
        }
        throw e;
    }
    process.stdout.write('\n');

    const { passageVectors, queryVectors, usage, model, dimensions } = embedded;

    console.log(`모델: ${model} (${dimensions}차원)`);
    if (usage.promptTokens > 0) {
        console.log(`토큰 ${usage.promptTokens.toLocaleString()}개, 비용 약 $${usage.estimatedCostUsd.toFixed(4)}`);
    } else if (args.provider === 'local') {
        console.log('비용: 없음 (내 컴퓨터에서 실행)');
    } else {
        console.log('사용량: API가 알려주지 않는다. 무료 한도 안에서 쓴 것으로 본다.');
    }

    const payload = {
        meta: {
            generatedAt: new Date().toISOString(),
            provider: args.provider,
            model,
            dimensions,
            withOverview: args.withOverview,
            overviewCount: overviews.size,
            spotCount: candidates.length,
            realSpotCount: spots.length,
            fillerCount: filler.length,
            queryCount: queryTexts.length,
            excludedSpotIds: excludeIds,
            goldensetSeed: goldenset.meta.seed,
            promptTokens: usage.promptTokens,
            estimatedCostUsd: Number(usage.estimatedCostUsd.toFixed(6)),
        },
        // 후보가 10만 건이 되면 텍스트까지 담을 이유가 없다. id와 벡터만 남긴다.
        spots: candidates.map((c, i) => ({
            id: c.id,
            name: c.real ? c.name : undefined,
            vector: encodeVector(passageVectors[i]),
        })),
        queries: goldenset.queries.map((q, i) => ({
            id: q.id,
            type: q.type,
            keyword: q.keyword,
            relevantSpotIds: q.relevantSpotIds,
            vector: encodeVector(queryVectors[i]),
        })),
    };

    const outPath = join(args.out, 'embeddings.json');
    writeFileSync(outPath, JSON.stringify(payload), 'utf8');

    // 다 끝났으니 중간 저장 파일은 지운다. 남겨두면 다음에 입력을 바꿨을 때
    // 헷갈릴 뿐이고, 용량도 본 파일만큼 차지한다.
    if (existsSync(checkpointPath)) rmSync(checkpointPath);

    console.log('');
    console.log(`저장: ${outPath.replace(PROJECT_ROOT, '.').replace(/\\/g, '/')}`);
    console.log('다음: node search-eval/evaluate-vector.js');
}

main().catch((e) => {
    console.error(e.message);
    process.exit(1);
});
