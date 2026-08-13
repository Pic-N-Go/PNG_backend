// src/main/resources/spot_data.sql에 들어있는 실제 스팟 135건을 읽는다.
//
// 골든셋을 "직접 떠올린 검색어"가 아니라 "DB에 실제로 있는 데이터에서 파생한
// 검색어"로 만들기 위한 입력이다. 검색어의 출처가 데이터 자체여야
// 검색어 선정에 사람의 편향이 끼어들 여지가 줄어든다.
//
// 파서를 쓰는 이유: DB에 접속하지 않아도 골든셋을 만들 수 있어야 한다.
// (평가 러너만 실행 중인 서버가 필요하고, 생성 단계는 오프라인으로 돈다.)

import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const SPOT_ROW = /^\s*\((\d+),\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*(-?[\d.]+),\s*(-?[\d.]+),/;
const CATEGORY_PAIR = /\((\d+),\s*'([A-Z_]+)'\)/g;

function unquote(value) {
    return value.replace(/''/g, "'");
}

/**
 * @returns {{spots: Array<{id, name, address, latitude, longitude, categories: string[]}>}}
 */
export function loadCorpus(sqlPath) {
    const sql = readFileSync(sqlPath, 'utf8');

    // spot_categories INSERT 위쪽이 스팟 본문, 아래쪽이 카테고리 매핑이다.
    const splitAt = sql.indexOf('INSERT INTO spot_categories');
    if (splitAt < 0) {
        throw new Error(`spot_categories INSERT를 찾지 못했다: ${sqlPath}`);
    }

    const spotSection = sql.slice(0, splitAt);
    const categorySection = sql.slice(splitAt);

    const spots = new Map();
    for (const line of spotSection.split('\n')) {
        const m = line.match(SPOT_ROW);
        if (!m) continue;
        const [, id, name, address, lat, lng] = m;
        spots.set(Number(id), {
            id: Number(id),
            name: unquote(name),
            address: unquote(address),
            latitude: Number(lat),
            longitude: Number(lng),
            categories: [],
        });
    }

    for (const m of categorySection.matchAll(CATEGORY_PAIR)) {
        const spot = spots.get(Number(m[1]));
        if (spot && !spot.categories.includes(m[2])) {
            spot.categories.push(m[2]);
        }
    }

    const OVERVIEW_UPDATE = /^UPDATE spot SET overview = '((?:[^']|'')*)' WHERE id = (\d+);/gm;
    for (const m of sql.matchAll(OVERVIEW_UPDATE)) {
        const spot = spots.get(Number(m[2]));
        if (spot) {
            spot.overview = unquote(m[1]);
        }
    }

    if (spots.size === 0) {
        throw new Error(`스팟을 한 건도 파싱하지 못했다: ${sqlPath}`);
    }

    return { spots: [...spots.values()].sort((a, b) => a.id - b.id) };
}

/** 주소 앞머리에서 시/도를 뽑는다. '서울특별시 종로구 ...' -> '서울특별시' */
export function sidoOf(address) {
    return address.split(/\s+/)[0] ?? '';
}

// generate-seed.js가 만든 필러 행. (id, '이름', '주소', '설명문', 위도, 경도, ...)
const FILLER_ROW = /^\((\d+),\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',/;

/**
 * 시드 SQL에서 필러 스팟을 읽는다.
 *
 * <p>벡터 평가의 후보 집합을 실제 검색과 맞추기 위한 것이다. 실제 스팟 130건만
 * 후보로 두면 상위 20건이 전체의 15%가 되어, 아무렇게나 골라도 자주 맞는다.
 * 문자열 검색은 10만 건 사이에서 20건을 고르므로 그대로 비교하면 벡터에 크게 유리하다.
 *
 * <p>DB에 붙지 않고 시드 SQL을 읽는 이유: 생성기가 만든 파일이 그대로 남아 있고,
 * 오프라인으로 돌아가는 이 도구의 성격을 유지하기 위해서다.
 *
 * @param limit 앞에서부터 몇 건만 읽을지. 전부 읽으려면 Infinity.
 */
export function loadFillerSpots(seedDir, limit) {
    const files = readdirSync(seedDir)
        .filter((n) => /^seed-\d+\.sql$/.test(n))
        .sort();

    const spots = [];
    for (const file of files) {
        for (const line of readFileSync(join(seedDir, file), 'utf8').split('\n')) {
            const m = line.match(FILLER_ROW);
            if (!m) continue;

            const [, id, name, address, overview] = m;
            spots.push({
                id: Number(id),
                name: unquote(name),
                address: unquote(address),
                overview: unquote(overview),
            });
            if (spots.length >= limit) return spots;
        }
    }
    return spots;
}
