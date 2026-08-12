// 평가 결과 집계와 리포트 작성. 문자열 검색(evaluate-quality.js)과
// 벡터 검색(evaluate-vector.js)이 같은 코드를 쓴다.
//
// 같은 함수를 쓰는 게 중요하다. 두 리포트를 나란히 놓고 비교할 텐데, 집계 방식이
// 조금이라도 다르면 그 차이가 검색 방식의 차이로 잘못 읽힌다.

/**
 * @param results {Array<{type, totalElements, relevantCount, hitCount, firstHitRank, elapsedMs}>}
 * @param k 상위 몇 건까지 봤는지
 */
export function summarize(results, k) {
    const byType = new Map();

    for (const r of results) {
        if (!byType.has(r.type)) {
            byType.set(r.type, {
                type: r.type, queries: 0, zeroResult: 0, hit: 0,
                recallSum: 0, reciprocalRankSum: 0, matchedNotRanked: 0, latencySum: 0,
            });
        }
        const bucket = byType.get(r.type);
        bucket.queries += 1;
        bucket.latencySum += r.elapsedMs ?? 0;
        if (r.totalElements === 0) bucket.zeroResult += 1;
        if (r.hitCount > 0) bucket.hit += 1;
        // 결과는 있는데 정답이 상위 k 밖 -> 매칭이 아니라 랭킹의 실패
        if (r.totalElements > 0 && r.hitCount === 0) bucket.matchedNotRanked += 1;
        bucket.recallSum += r.relevantCount > 0 ? r.hitCount / Math.min(r.relevantCount, k) : 0;
        bucket.reciprocalRankSum += r.firstHitRank ? 1 / r.firstHitRank : 0;
    }

    return [...byType.values()].map((b) => ({
        type: b.type,
        queries: b.queries,
        zeroResultRate: b.zeroResult / b.queries,
        hitRate: b.hit / b.queries,
        recallAtK: b.recallSum / b.queries,
        mrr: b.reciprocalRankSum / b.queries,
        matchedNotRankedRate: b.matchedNotRanked / b.queries,
        avgLatencyMs: b.latencySum / b.queries,
    }));
}

const pct = (v) => `${(v * 100).toFixed(1)}%`;

/**
 * @param options.title      리포트 제목
 * @param options.conditions 실험 조건 표에 넣을 [항목, 값] 배열
 * @param options.notes      "읽는 법" 뒤에 덧붙일 문단 배열 (없으면 생략)
 */
export function buildReport({ title, conditions, summary, variantMeta, limitations, k, notes = [] }) {
    const order = Object.keys(variantMeta);
    const sorted = [...summary].sort((a, b) => order.indexOf(a.type) - order.indexOf(b.type));

    const lines = [
        `# ${title}`,
        '',
        '## 실험 조건',
        '',
        '| 항목 | 값 |',
        '| --- | --- |',
        ...conditions.map(([label, value]) => `| ${label} | ${value} |`),
        '',
        '## 변형 유형별 결과',
        '',
        `| 변형 유형 | 검색어 | 무결과율 | 적중률@${k} | Recall@${k} | MRR | 매칭O·순위밖 | 사전 예상 |`,
        '| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |',
    ];

    for (const row of sorted) {
        const vm = variantMeta[row.type];
        const expectation = vm.expectation === 'hit' ? '적중' : '실패';
        lines.push(
            `| ${vm.label} (\`${row.type}\`) | ${row.queries} | ${pct(row.zeroResultRate)} `
            + `| ${pct(row.hitRate)} | ${pct(row.recallAtK)} | ${row.mrr.toFixed(3)} `
            + `| ${pct(row.matchedNotRankedRate)} | ${expectation} |`
        );
    }

    const overall = {
        queries: summary.reduce((s, r) => s + r.queries, 0),
        zero: summary.reduce((s, r) => s + r.zeroResultRate * r.queries, 0),
        hit: summary.reduce((s, r) => s + r.hitRate * r.queries, 0),
    };

    lines.push(
        '',
        `전체 ${overall.queries}건 기준 무결과율 ${pct(overall.zero / overall.queries)}, `
        + `적중률 ${pct(overall.hit / overall.queries)}.`,
        '',
        '## 읽는 법',
        '',
        '- **무결과율**: 결과가 0건. 문자열 매칭 자체가 실패한 경우다.',
        `- **매칭O·순위밖**: 결과는 나왔는데 정답이 상위 ${k}건 밖으로 밀렸다. 매칭이 아니라`,
        '  **랭킹**의 실패다.',
        '- **사전 예상**: 측정 전에 적어둔 예상값이다. 결과를 보고 기준을 만든 게 아니라는',
        '  것을 보이기 위해 골든셋 생성 시점에 고정한다.',
        ...notes,
        '',
        '## 이 수치의 한계',
        '',
        ...limitations.map((l) => `- ${l}`),
        '',
    );

    return lines.join('\n');
}
