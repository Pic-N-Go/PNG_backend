// 시드 고정 난수. 시드 데이터와 골든셋이 매번 같은 값으로 재생성되어야
// "이 숫자는 재현 가능한가"라는 질문에 답할 수 있다. Math.random()을 쓰면
// 실행할 때마다 결과가 달라져서 실험 결과를 검증할 수 없다.
//
// mulberry32: 32비트 상태 하나로 도는 짧은 PRNG. 통계적 품질이 필요한 용도가
// 아니라(암호도, 시뮬레이션도 아님) 재현성만 있으면 충분하다.
export function createRng(seed) {
    let state = seed >>> 0;

    function next() {
        state = (state + 0x6d2b79f5) >>> 0;
        let t = state;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    }

    return {
        next,
        // [min, max) 정수
        int(min, max) {
            return min + Math.floor(next() * (max - min));
        },
        pick(arr) {
            return arr[Math.floor(next() * arr.length)];
        },
        // 배열에서 중복 없이 n개. n이 배열보다 크면 전체를 돌려준다.
        sample(arr, n) {
            const copy = [...arr];
            const out = [];
            while (out.length < n && copy.length > 0) {
                out.push(copy.splice(Math.floor(next() * copy.length), 1)[0]);
            }
            return out;
        },
        bool(p = 0.5) {
            return next() < p;
        },
    };
}
