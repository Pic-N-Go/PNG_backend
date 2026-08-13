// 임베딩 파일 입출력 — 왜 파일을 세 개로 나눴는지가 이 파일의 핵심이다.
//
// [무엇이 터졌었나]
// 처음엔 스팟 10만 건 + 검색어 853건의 벡터를 전부 객체 하나에 담아
// JSON.stringify() 한 번으로 저장했다. OpenAI 벡터는 1536차원이라 base64로
// 다 이어붙이면 문자열 하나가 800MB 안팎이 되는데, Node/V8은 문자열 하나의
// 최대 길이가 정해져 있다(대략 5억~10억 자, 버전마다 다름). 실제로 10만 건
// 임베딩 도중 "Invalid string length" 에러로 멈췄다.
//
// [왜 세 파일인가]
// 검색어(수백~수천 건)는 base64로 바꿔도 전체가 몇 MB 안쪽이라 그냥 한 번에
// 써도 안전하다. 문제는 스팟(최대 10만 건대) 쪽이다. 이걸 한 줄에 하나씩
// 쓰는 JSONL로 바꾸면, 아무리 개수가 늘어도 "문자열 하나"의 크기는 벡터
// 하나 분량(1536차원 기준 8KB 안팎)을 넘지 않는다. 개수 제한이 없어진다.
//
//   embeddings.meta.json     실험 조건 (작음, 그대로 저장)
//   embeddings.queries.json  검색어 + 벡터 (작음, 그대로 저장)
//   embeddings.spots.jsonl   스팟 + 벡터, 한 줄에 하나 (커질 수 있어 스트리밍)
//
// 중간 저장(이어하기)도 같은 이유로 JSONL이다. 벡터 하나가 만들어질 때마다
// 파일 끝에 한 줄만 추가하므로(append), 지금까지 몇 건을 만들었든 한 번에
// 다루는 문자열 크기는 항상 벡터 한 개 분량으로 고정된다.

import { createReadStream, appendFileSync, readFileSync, writeFileSync, existsSync, unlinkSync } from 'node:fs';
import { createInterface } from 'node:readline';
import { join } from 'node:path';

export function encodeVector(numbers) {
    return Buffer.from(Float32Array.from(numbers).buffer).toString('base64');
}

export function decodeVector(base64) {
    const buffer = Buffer.from(base64, 'base64');
    return new Float32Array(buffer.buffer, buffer.byteOffset, buffer.byteLength / 4);
}

const FILE = {
    meta: 'embeddings.meta.json',
    queries: 'embeddings.queries.json',
    spots: 'embeddings.spots.jsonl',
    checkpointVectors: 'embeddings.partial.jsonl',
    checkpointMeta: 'embeddings.partial.meta.json',
};

async function readLines(path) {
    const lines = [];
    const rl = createInterface({ input: createReadStream(path, 'utf8'), crlfDelay: Infinity });
    for await (const line of rl) {
        if (line.trim().length > 0) lines.push(line);
    }
    return lines;
}

// ─────────────────────────────────────────────────────────────
// 최종 출력

/**
 * @param dir 이미 만들어져 있어야 한다(mkdirSync는 호출부 책임).
 * @param spots   [{id, name?, vector: number[]}]  - name은 필러면 없어도 된다
 * @param queries [{id, type, keyword, relevantSpotIds, vector: number[]}]
 */
export function writeEmbeddingsOutput(dir, { meta, spots, queries }) {
    writeFileSync(join(dir, FILE.meta), JSON.stringify(meta, null, 2), 'utf8');

    // 검색어는 개수가 적어 base64로 바꿔도 전체가 크지 않다. 한 번에 쓴다.
    const queryRecords = queries.map((q) => ({ ...q, vector: encodeVector(q.vector) }));
    writeFileSync(join(dir, FILE.queries), JSON.stringify(queryRecords), 'utf8');

    // 스팟은 몇천 줄씩 묶어서 이어쓴다(append). 한 번에 파일을 다 채우지 않고
    // 나눠 쓰는 이유는 파일 열기 비용을 줄이기 위해서일 뿐이다 - 묶는 단위(2000줄)는
    // 문자열 제한(수억 자)에 비하면 훨씬 작아(수십 MB) 안전선이 넉넉하다.
    const spotsPath = join(dir, FILE.spots);
    writeFileSync(spotsPath, '', 'utf8');
    const CHUNK = 2000;
    for (let start = 0; start < spots.length; start += CHUNK) {
        const lines = spots.slice(start, start + CHUNK)
            .map((s) => JSON.stringify({ id: s.id, name: s.name, vector: encodeVector(s.vector) }))
            .join('\n');
        appendFileSync(spotsPath, `${lines}\n`, 'utf8');
    }
}

/**
 * @returns {Promise<{meta, queries, spots}>}
 *   queries[i].vector, spots[i].vector 는 base64 문자열 그대로 돌려준다.
 *   평가 쪽(evaluate-vector.js)이 필요한 시점에만 decodeVector로 풀어 쓰는 게
 *   미리 다 풀어두는 것보다 메모리를 덜 먹는다.
 */
export async function readEmbeddingsOutput(dir) {
    const meta = JSON.parse(readFileSync(join(dir, FILE.meta), 'utf8'));
    const queries = JSON.parse(readFileSync(join(dir, FILE.queries), 'utf8'));
    const spots = (await readLines(join(dir, FILE.spots))).map((line) => JSON.parse(line));
    return { meta, queries, spots };
}

// ─────────────────────────────────────────────────────────────
// 이어하기(체크포인트)

/**
 * 지금까지 만들어둔 벡터를 읽는다. 지문(fingerprint)이 다르면(검색어·스팟
 * 목록이 바뀐 경우) 이어서 쓰면 순서가 어긋나므로 빈 배열을 돌려줘 처음부터
 * 다시 만들게 한다.
 */
export async function loadCheckpoint(dir, fingerprint) {
    const metaPath = join(dir, FILE.checkpointMeta);
    const vectorsPath = join(dir, FILE.checkpointVectors);
    if (!existsSync(metaPath) || !existsSync(vectorsPath)) return [];

    let saved;
    try {
        saved = JSON.parse(readFileSync(metaPath, 'utf8'));
    } catch {
        return [];
    }
    if (saved.fingerprint !== fingerprint) return [];

    return (await readLines(vectorsPath)).map(decodeVector);
}

/**
 * 새로 만든 벡터만 파일 끝에 이어 붙인다(추가 전용).
 *
 * <p>매번 전체를 다시 써서 저장하면 갈수록 느려질 뿐 아니라, 갈수록 커지는
 * 배열 하나를 통째로 문자열로 만들어야 해서 애초에 고치려던 문제를 다시
 * 만든다. 새로 생긴 만큼만 붙이면 한 번에 다루는 크기가 항상 일정하다.
 */
export function appendCheckpoint(dir, fingerprint, newVectors) {
    // fingerprint 파일은 항상 작으니 매번 덮어써도 부담이 없다. 먼저 써야
    // 벡터 파일과 짝이 확실히 맞는다.
    writeFileSync(join(dir, FILE.checkpointMeta), JSON.stringify({ fingerprint }), 'utf8');
    if (newVectors.length === 0) return;

    const lines = newVectors.map((v) => encodeVector(v)).join('\n');
    appendFileSync(join(dir, FILE.checkpointVectors), `${lines}\n`, 'utf8');
}

export function clearCheckpoint(dir) {
    for (const name of [FILE.checkpointVectors, FILE.checkpointMeta]) {
        const p = join(dir, name);
        if (existsSync(p)) unlinkSync(p);
    }
}
