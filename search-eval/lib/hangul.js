// 한글 자모 분해/조합과 오타 생성.
//
// 오타를 "아무 글자나 바꾸기"로 만들면 실험이 무의미해진다. 사람이 실제로 내는
// 오타에는 패턴이 있고, 그 패턴을 벗어난 오타는 검색 엔진이 못 잡는 게 당연해서
// 아무것도 증명하지 못한다. 여기서는 문서화된 두 가지 패턴만 쓴다:
//
//   1) 두벌식 자판 인접키 입력 (fat finger) - 물리적 원인이라 언어와 무관하게 발생
//   2) 종성 누락 - 한국어에서 흔한 표기 오류 (박물관 -> 바물관)
//
// 파생 규칙을 코드로 고정해두는 이유는 재현성 때문이다. 손으로 오타를 지어내면
// "그 검색어를 왜 그렇게 골랐냐"에 답할 수 없다.

const HANGUL_BASE = 0xac00;
const HANGUL_LAST = 0xd7a3;

const CHOSUNG = [
    'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
    'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
];

const JUNGSUNG = [
    'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
    'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ',
];

const JONGSUNG = [
    '', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ',
    'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ',
    'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
];

// 두벌식 자판 배열. 한 자모가 어느 키에 있는지.
const JAMO_TO_KEY = {
    'ㅂ': 'q', 'ㅈ': 'w', 'ㄷ': 'e', 'ㄱ': 'r', 'ㅅ': 't',
    'ㅛ': 'y', 'ㅕ': 'u', 'ㅑ': 'i', 'ㅐ': 'o', 'ㅔ': 'p',
    'ㅁ': 'a', 'ㄴ': 's', 'ㅇ': 'd', 'ㄹ': 'f', 'ㅎ': 'g',
    'ㅗ': 'h', 'ㅓ': 'j', 'ㅏ': 'k', 'ㅣ': 'l',
    'ㅋ': 'z', 'ㅌ': 'x', 'ㅊ': 'c', 'ㅍ': 'v',
    'ㅠ': 'b', 'ㅜ': 'n', 'ㅡ': 'm',
};

const KEY_TO_JAMO = Object.fromEntries(
    Object.entries(JAMO_TO_KEY).map(([jamo, key]) => [key, jamo])
);

// QWERTY 물리 배열 기준 인접 키. 손가락이 옆 키를 누르는 상황을 재현한다.
const KEY_NEIGHBORS = {
    q: ['w', 'a', 's'],
    w: ['q', 'e', 'a', 's', 'd'],
    e: ['w', 'r', 's', 'd', 'f'],
    r: ['e', 't', 'd', 'f', 'g'],
    t: ['r', 'y', 'f', 'g', 'h'],
    y: ['t', 'u', 'g', 'h', 'j'],
    u: ['y', 'i', 'h', 'j', 'k'],
    i: ['u', 'o', 'j', 'k', 'l'],
    o: ['i', 'p', 'k', 'l'],
    p: ['o', 'l'],
    a: ['q', 'w', 's', 'z', 'x'],
    s: ['q', 'w', 'e', 'a', 'd', 'z', 'x', 'c'],
    d: ['w', 'e', 'r', 's', 'f', 'x', 'c', 'v'],
    f: ['e', 'r', 't', 'd', 'g', 'c', 'v', 'b'],
    g: ['r', 't', 'y', 'f', 'h', 'v', 'b', 'n'],
    h: ['t', 'y', 'u', 'g', 'j', 'b', 'n', 'm'],
    j: ['y', 'u', 'i', 'h', 'k', 'n', 'm'],
    k: ['u', 'i', 'o', 'j', 'l', 'm'],
    l: ['i', 'o', 'p', 'k'],
    z: ['a', 's', 'x'],
    x: ['a', 's', 'd', 'z', 'c'],
    c: ['s', 'd', 'f', 'x', 'v'],
    v: ['d', 'f', 'g', 'c', 'b'],
    b: ['f', 'g', 'h', 'v', 'n'],
    n: ['g', 'h', 'j', 'b', 'm'],
    m: ['h', 'j', 'k', 'n'],
};

export function isHangulSyllable(ch) {
    const code = ch.codePointAt(0);
    return code >= HANGUL_BASE && code <= HANGUL_LAST;
}

// '값' -> { cho: 'ㄱ', jung: 'ㅏ', jong: 'ㅄ' }
export function decompose(ch) {
    if (!isHangulSyllable(ch)) return null;
    const offset = ch.codePointAt(0) - HANGUL_BASE;
    return {
        cho: CHOSUNG[Math.floor(offset / (21 * 28))],
        jung: JUNGSUNG[Math.floor(offset / 28) % 21],
        jong: JONGSUNG[offset % 28],
    };
}

export function compose({ cho, jung, jong }) {
    const choIdx = CHOSUNG.indexOf(cho);
    const jungIdx = JUNGSUNG.indexOf(jung);
    const jongIdx = JONGSUNG.indexOf(jong || '');
    if (choIdx < 0 || jungIdx < 0 || jongIdx < 0) return null;
    return String.fromCodePoint(HANGUL_BASE + (choIdx * 21 + jungIdx) * 28 + jongIdx);
}

// 자모 하나를 인접키 자모로 바꾼다. 바꾼 결과가 같은 분류(초성/중성)에
// 속하지 않으면 조합이 깨지므로, 후보 중 유효한 것만 남긴다.
function neighborJamo(jamo, validSet, rng) {
    const key = JAMO_TO_KEY[jamo];
    if (!key) return null;

    const candidates = (KEY_NEIGHBORS[key] || [])
        .map((k) => KEY_TO_JAMO[k])
        .filter((j) => j && validSet.includes(j));

    return candidates.length > 0 ? rng.pick(candidates) : null;
}

/**
 * 두벌식 인접키 오타를 1개 만든다.
 * 한글 음절 중 하나를 골라 초성 또는 중성을 옆 키 자모로 바꾼다.
 * 바꿀 수 있는 자리가 없으면 null(오타 생성 실패)을 돌려준다.
 */
export function typoAdjacentKey(text, rng) {
    const chars = [...text];
    const positions = chars
        .map((ch, i) => (isHangulSyllable(ch) ? i : -1))
        .filter((i) => i >= 0);
    if (positions.length === 0) return null;

    // 첫 글자를 틀리는 경우는 드물다(사람은 첫 글자를 보고 친다).
    // 두 글자 이상이면 첫 글자는 후보에서 뺀다.
    const targets = positions.length > 1 ? positions.slice(1) : positions;

    for (const idx of rng.sample(targets, targets.length)) {
        const parts = decompose(chars[idx]);

        const tryCho = rng.bool(0.5);
        const order = tryCho ? ['cho', 'jung'] : ['jung', 'cho'];

        for (const slot of order) {
            const validSet = slot === 'cho' ? CHOSUNG : JUNGSUNG;
            const replaced = neighborJamo(parts[slot], validSet, rng);
            if (!replaced) continue;

            const composed = compose({ ...parts, [slot]: replaced });
            if (composed && composed !== chars[idx]) {
                const out = [...chars];
                out[idx] = composed;
                return out.join('');
            }
        }
    }
    return null;
}

/**
 * 종성을 1개 떨어뜨린다. 박물관 -> 바물관.
 * 종성이 있는 음절이 없으면 null.
 */
export function typoDropJongsung(text, rng) {
    const chars = [...text];
    const targets = chars
        .map((ch, i) => {
            if (!isHangulSyllable(ch)) return -1;
            return decompose(ch).jong ? i : -1;
        })
        .filter((i) => i >= 0);
    if (targets.length === 0) return null;

    const idx = rng.pick(targets);
    const out = [...chars];
    out[idx] = compose({ ...decompose(chars[idx]), jong: '' });
    return out.join('');
}

/** 모든 공백 제거. '남산 서울타워' -> '남산서울타워' */
export function removeSpaces(text) {
    return text.replace(/\s+/g, '');
}

/**
 * 공백이 없는 자리에 공백을 1개 넣는다. '남산서울타워' -> '남산 서울타워'.
 * 양 끝은 제외하고, 이미 공백인 자리도 제외한다.
 */
export function insertSpace(text, rng) {
    const chars = [...text];
    const slots = [];
    for (let i = 1; i < chars.length; i += 1) {
        if (chars[i] !== ' ' && chars[i - 1] !== ' ') slots.push(i);
    }
    if (slots.length === 0) return null;

    const idx = rng.pick(slots);
    return `${chars.slice(0, idx).join('')} ${chars.slice(idx).join('')}`;
}

export const _internal = { CHOSUNG, JUNGSUNG, JONGSUNG, JAMO_TO_KEY, KEY_NEIGHBORS };
