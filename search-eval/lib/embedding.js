// 문장을 벡터로 바꾸는 부분. 제공자를 두 가지 지원한다.
//
//   local   transformers.js로 multilingual-e5-small을 내 컴퓨터에서 돌린다. 무료.
//   openai  OpenAI 임베딩 API를 부른다. 키와 크레딧이 필요하다.
//
// 둘 다 남겨둔 이유: 작은 로컬 모델과 큰 상용 모델의 차이를 재는 것 자체가
// 실험 재료가 된다. 그리고 한쪽 설치가 실패해도 다른 쪽으로 진행할 수 있다.
//
// 임베딩은 문장을 숫자 배열로 바꾼 것이다. 뜻이 비슷한 문장은 배열도 비슷해져서,
// 글자가 하나도 안 겹쳐도 '해수욕장'과 '바다'가 가깝다고 판정할 수 있다.
// 지금까지 쓴 문자열 색인으로는 원리적으로 안 되던 일이다.

import { fileURLToPath } from 'node:url';

// 주소를 환경변수로 바꿔 끼울 수 있게 해둔다. 이어하기 같은 동작을 확인할 때
// 진짜 API를 부르지 않고(돈이 들거나 키가 필요하니) 가짜 서버로 시험하기 위해서다.
const OPENAI_ENDPOINT = process.env.OPENAI_ENDPOINT_OVERRIDE
    ?? 'https://api.openai.com/v1/embeddings';
const GEMINI_ENDPOINT = process.env.GEMINI_ENDPOINT_OVERRIDE
    ?? 'https://generativelanguage.googleapis.com/v1beta/models';
const OPENAI_BATCH = 256;
const GEMINI_BATCH = 100;
const LOCAL_BATCH = 32;

export const DEFAULT_MODELS = {
    local: 'Xenova/multilingual-e5-small',
    openai: 'text-embedding-3-small',
    gemini: 'gemini-embedding-001',
};

/**
 * 스팟 텍스트와 검색어를 한 번에 임베딩한다.
 *
 * <p>둘을 따로 받는 이유가 있다. e5 계열 모델은 문서와 질문에 서로 다른 접두어를
 * 붙여야 제 성능이 난다(passage: / query:). 하나의 배열로 받으면 어느 것이 질문인지
 * 알 수 없어 접두어를 못 붙이고, 그러면 점수만 조용히 나빠진다.
 *
 * @returns {Promise<{passageVectors, queryVectors, dimensions, usage, model}>}
 */
export async function embedCorpus({
    passages, queries, provider, model, apiKey,
    onProgress = () => {},
    // 이미 만들어둔 벡터. 앞에서부터 이만큼은 건너뛴다.
    resumeVectors = [],
    // 한 묶음 끝날 때마다 부른다. 여기서 중간 저장을 한다.
    onBatch = () => {},
}) {
    const resolvedModel = model || DEFAULT_MODELS[provider];
    if (!resolvedModel) {
        throw new Error(`알 수 없는 제공자: ${provider} (gemini, openai, local 중 하나)`);
    }

    const embed = { local: embedWithLocalModel, openai: embedWithOpenAi, gemini: embedWithGemini }[provider];
    const result = await embed({
        passages, queries, model: resolvedModel, apiKey, onProgress,
        resumeVectors, onBatch,
    });

    return { ...result, model: resolvedModel, dimensions: result.passageVectors[0]?.length ?? 0 };
}

/**
 * 이어서 하기 위한 공통 처리.
 *
 * <p>스팟 벡터와 검색어 벡터를 하나로 이어붙인 목록을 기준으로 삼는다.
 * 앞에서부터 순서대로 만들기 때문에, "몇 개까지 만들었나"만 알면
 * 그 다음부터 이어서 할 수 있다.
 */
function splitResumed(vectors, passageCount) {
    return {
        passageVectors: vectors.slice(0, passageCount),
        queryVectors: vectors.slice(passageCount),
    };
}

// ─────────────────────────────────────────────────────────────
// 로컬 모델

async function loadPipeline(model) {
    // 두 패키지 이름을 모두 시도한다. transformers.js는 @xenova/transformers에서
    // @huggingface/transformers로 옮겨갔는데, 어느 쪽이 깔려 있든 동작하게 둔다.
    let transformers;
    for (const pkg of ['@huggingface/transformers', '@xenova/transformers']) {
        try {
            transformers = await import(pkg);
            break;
        } catch {
            // 다음 후보로
        }
    }

    if (!transformers) {
        throw new Error(
            'transformers.js가 설치되어 있지 않다.\n'
            + '  cd search-eval && npm install\n'
            + '설치가 안 되면 --provider openai 로 대신 진행할 수 있다.'
        );
    }

    // 모델 파일을 프로젝트 안 정해진 위치에 받는다. 어디에 쌓이는지 알 수 있어야
    // 나중에 지우기 쉽다(.gitignore에 넣어뒀다).
    //
    // URL.pathname을 그대로 쓰면 안 된다. Windows에서 '/C:/...' 형태라
    // 'C:\C:\...' 같은 경로가 만들어져 모델을 못 받는다. fileURLToPath를 거쳐야 한다.
    transformers.env.cacheDir = fileURLToPath(new URL('../.model-cache/', import.meta.url));

    return transformers.pipeline('feature-extraction', model, { dtype: 'fp32' });
}

async function embedWithLocalModel({ passages, queries, model, onProgress, resumeVectors, onBatch }) {
    const extractor = await loadPipeline(model);

    // e5 계열은 접두어를 요구한다. 빼먹어도 에러가 안 나고 점수만 나빠진다.
    const inputs = [
        ...passages.map((t) => `passage: ${t}`),
        ...queries.map((t) => `query: ${t}`),
    ];

    const vectors = [...resumeVectors];
    for (let start = vectors.length; start < inputs.length; start = vectors.length) {
        const batch = inputs.slice(start, start + LOCAL_BATCH);
        // mean pooling + 정규화는 파이프라인이 해준다.
        const output = await extractor(batch, { pooling: 'mean', normalize: true });
        const [rows, dim] = output.dims;

        for (let r = 0; r < rows; r += 1) {
            vectors.push(Array.from(output.data.slice(r * dim, (r + 1) * dim)));
        }
        onProgress(vectors.length, inputs.length);
        await onBatch(vectors);
    }

    return {
        ...splitResumed(vectors, passages.length),
        usage: { promptTokens: 0, estimatedCostUsd: 0 },
    };
}

// ─────────────────────────────────────────────────────────────
// OpenAI API

// $0.02 / 1M 토큰 (text-embedding-3-small 기준). 비용을 눈으로 확인시키려는 용도다.
const USD_PER_MILLION_TOKENS = 0.02;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * 실패하면 잠시 쉬었다 다시 보낸다.
 *
 * <p>스팟 10만 건을 보내려면 API를 400번쯤 불러야 한다. 그동안 한 번이라도
 * "너무 빨리 보낸다(429)"거나 서버가 잠깐 흔들리면(5xx) 전체가 날아간다.
 * 20분을 기다렸다가 처음부터 다시 하는 건 너무 아깝다.
 *
 * <p>기다리는 시간을 2배씩 늘린다(2초 → 4초 → 8초...). 계속 같은 간격으로
 * 다시 보내면 밀린 요청이 몰려 상황이 더 나빠지기 때문이다.
 */
async function postWithRetry(url, options, { attempts = 5, baseDelayMs = 2000 } = {}) {
    let lastError = '';

    for (let attempt = 1; attempt <= attempts; attempt += 1) {
        let response;
        try {
            response = await fetch(url, options);
        } catch (e) {
            lastError = e.message;
            await sleep(baseDelayMs * 2 ** (attempt - 1));
            continue;
        }

        if (response.ok) return response;

        // 429는 속도 제한, 5xx는 서버 문제. 둘 다 기다리면 풀릴 수 있다.
        // 400(잘못된 요청)이나 401(키 오류)은 다시 보내도 똑같으므로 바로 멈춘다.
        const retryable = response.status === 429 || response.status >= 500;
        lastError = `HTTP ${response.status}: ${(await response.text()).slice(0, 200)}`;
        if (!retryable || attempt === attempts) {
            throw new Error(`임베딩 API 실패 - ${lastError}`);
        }

        // 왜 느린지 알 수 있게 남긴다. 429가 반복되면 무료 한도에 걸린 것이고,
        // 그건 규모를 줄이거나 다른 제공자를 써야 한다는 신호다.
        const waitMs = baseDelayMs * 2 ** (attempt - 1);
        process.stderr.write(`\n  [재시도 ${attempt}/${attempts}] ${response.status} - ${waitMs / 1000}초 대기\n`);
        await sleep(waitMs);
    }

    throw new Error(`임베딩 API 실패 (${attempts}번 시도) - ${lastError}`);
}

async function embedWithOpenAi({ passages, queries, model, apiKey, onProgress, resumeVectors, onBatch }) {
    if (!apiKey) {
        throw new Error('OPENAI_API_KEY가 없다. 환경변수로 넣거나 --api-key로 전달할 것.');
    }

    // OpenAI 모델은 접두어나 taskType을 쓰지 않는다.
    const inputs = [...passages, ...queries];
    const vectors = [...resumeVectors];
    let promptTokens = 0;

    for (let start = vectors.length; start < inputs.length; start = vectors.length) {
        const batch = inputs.slice(start, start + OPENAI_BATCH);
        const response = await postWithRetry(OPENAI_ENDPOINT, {
            method: 'POST',
            headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ model, input: batch }),
        });

        const body = await response.json();
        // API가 순서를 보장하지만 index로 다시 정렬해 확실히 맞춘다.
        // 여기서 어긋나면 스팟과 벡터가 뒤섞여, 예외 없이 점수만 엉망이 된다.
        for (const item of [...body.data].sort((a, b) => a.index - b.index)) {
            vectors.push(normalize(item.embedding));
        }

        promptTokens += body.usage?.prompt_tokens ?? 0;
        onProgress(vectors.length, inputs.length);
        await onBatch(vectors);
    }

    return {
        ...splitResumed(vectors, passages.length),
        usage: {
            promptTokens,
            estimatedCostUsd: (promptTokens / 1_000_000) * USD_PER_MILLION_TOKENS,
        },
    };
}

// ─────────────────────────────────────────────────────────────
// Google Gemini API

/**
 * Gemini는 "이 문장을 어디에 쓸 거냐"를 taskType으로 알려줘야 한다.
 *
 * <p>e5 모델이 'passage:' / 'query:' 접두어를 요구하는 것과 같은 이유다.
 * 찾는 쪽(검색어)과 찾히는 쪽(스팟)은 성격이 달라서, 같은 방식으로 처리하면
 * 점수가 나빠진다. 빠뜨려도 에러가 안 나고 결과만 조용히 나빠지는 종류다.
 */
async function embedWithGemini({ passages, queries, model, apiKey, onProgress, resumeVectors, onBatch }) {
    if (!apiKey) {
        throw new Error('GEMINI_API_KEY가 없다. 환경변수로 넣거나 --api-key로 전달할 것.');
    }

    // 스팟은 '찾히는 쪽', 검색어는 '찾는 쪽'이라 taskType이 다르다.
    const items = [
        ...passages.map((text) => ({ text, taskType: 'RETRIEVAL_DOCUMENT' })),
        ...queries.map((text) => ({ text, taskType: 'RETRIEVAL_QUERY' })),
    ];

    const vectors = [...resumeVectors];

    while (vectors.length < items.length) {
        // taskType이 섞이지 않게 자른다. 한 요청 안에서 두 종류를 보내도 되지만,
        // 경계에서 실수하기 쉬워 아예 나눈다.
        const start = vectors.length;
        const taskType = items[start].taskType;
        const batch = [];
        for (let i = start; i < items.length && batch.length < GEMINI_BATCH; i += 1) {
            if (items[i].taskType !== taskType) break;
            batch.push(items[i].text);
        }

        const response = await postWithRetry(
            `${GEMINI_ENDPOINT}/${model}:batchEmbedContents`,
            {
                method: 'POST',
                headers: {
                    // 키를 URL 쿼리에 붙이는 방식도 있지만 헤더로 보낸다.
                    // URL은 로그나 오류 메시지에 그대로 남기 쉽다.
                    'x-goog-api-key': apiKey,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    requests: batch.map((text) => ({
                        model: `models/${model}`,
                        content: { parts: [{ text }] },
                        taskType,
                    })),
                }),
            },
            // 무료 한도에 걸리기 쉬우므로 더 오래, 더 여러 번 기다린다.
            { attempts: 6, baseDelayMs: 5000 }
        );

        const body = await response.json();
        for (const item of body.embeddings ?? []) {
            vectors.push(normalize(item.values));
        }

        onProgress(vectors.length, items.length);
        await onBatch(vectors);
    }

    return {
        ...splitResumed(vectors, passages.length),
        // 무료 한도 안에서 쓰는 것을 전제로 한다. 사용량은 API가 돌려주지 않는다.
        usage: { promptTokens: 0, estimatedCostUsd: 0 },
    };
}

// ─────────────────────────────────────────────────────────────

/**
 * 길이가 1이 되도록 벡터를 맞춘다.
 * 이렇게 해두면 코사인 유사도가 그냥 내적이 되어 계산이 단순해진다.
 */
export function normalize(vector) {
    let sumOfSquares = 0;
    for (const v of vector) sumOfSquares += v * v;

    const length = Math.sqrt(sumOfSquares);
    if (length === 0) return vector;

    return vector.map((v) => v / length);
}

/** 길이가 1인 벡터끼리의 코사인 유사도. 1에 가까울수록 뜻이 비슷하다. */
export function cosineSimilarity(a, b) {
    let dot = 0;
    for (let i = 0; i < a.length; i += 1) dot += a[i] * b[i];
    return dot;
}
