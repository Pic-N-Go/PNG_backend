package com.project.picngo.spot.domain;

/**
 * 두 문자열이 몇 글자나 다른지(편집거리) 계산한다. 오타 검색의 마지막 수단이다.
 *
 * <p>왜 필요한가: 앞 단계의 유사도 검색은 ngram 색인을 쓴다. 글자를 두 개씩 묶은
 * 조각이 얼마나 겹치는지로 찾는 방식인데, 검색어가 짧으면 조각 수가 적어서
 * 오타 하나에 전멸한다.
 *
 * <ul>
 *   <li>'헙재'(2글자) - 조각이 '헙재' 하나뿐이라 그 하나가 틀리면 남는 게 없다
 *   <li>'오셜록'(3글자) - 조각이 '오셜','셜록' 둘인데 가운데 글자를 틀리면 둘 다 깨진다
 * </ul>
 *
 * <p>여기서는 조각으로 쪼개지 않고 글자를 직접 맞대어 세므로 그 한계가 없다.
 * 색인을 쓸 수 없어 후보 전부를 훑어야 하지만, 앞 단계가 모두 0건일 때만 도는
 * 마지막 폴백이고 스팟 수가 수천 건 규모라 감당할 수 있다.
 */
public final class EditDistance {

    // 이보다 짧은 검색어에는 쓰지 않는다. 1~2글자는 거리 1만 허용해도
    // 한 글자만 우연히 들어간 거의 모든 스팟('다락원', '김광석다시그리기길')에 걸려서,
    // 오타를 잡는 게 아니라 엉뚱한 스팟이 채워져 의미 검색(Semantic)으로 넘어가지 못한다.
    public static final int MIN_KEYWORD_LENGTH = 3;

    // 허용 오차의 상한. 검색어가 길어질수록 오타도 늘 수 있지만, 무한정 풀어주면
    // 전혀 다른 이름까지 후보로 들어온다.
    private static final int MAX_ALLOWED_DISTANCE = 3;

    private EditDistance() {
    }

    /**
     * 검색어 길이에 따라 몇 글자까지 틀린 것을 봐줄지.
     *
     * <p>길이에 비례시키는 이유: '오셜록'(3글자)에서 1글자 오타는 흔하지만,
     * '제주올레길일코스'(8글자)에서 2글자쯤은 흔한 오타다. 고정값을 쓰면 짧은 검색어는
     * 엉뚱한 결과가 쏟아지고 긴 검색어는 오타를 못 잡는다.
     */
    public static int allowedDistance(int keywordLength) {
        if (keywordLength < MIN_KEYWORD_LENGTH) {
            return 0;
        }
        return Math.min(MAX_ALLOWED_DISTANCE, keywordLength / 3);
    }

    /**
     * 검색어와 가장 비슷한 <b>부분</b>을 text에서 찾아 그 편집거리를 돌려준다.
     *
     * <p>전체끼리 비교하지 않는 것이 핵심이다. '헙재'와 '협재해수욕장'을 통째로 비교하면
     * 거리가 5(한 글자 고치고 네 글자 더 붙이기)라 오타로 볼 수 없는 값이 나온다.
     * 사용자는 이름의 일부만 치는 게 보통이므로, text의 어느 위치에서 시작해 어디서
     * 끝나든 상관없이 가장 잘 맞는 구간을 찾아 그 거리를 쓴다. 그러면 '헙재'는 1이 된다.
     *
     * <p>구현은 표준 편집거리 표(DP)에서 두 곳만 바꾼 것이다. 첫 줄을 전부 0으로 두면
     * text의 어느 위치에서 시작해도 값을 치르지 않고(시작 자유), 마지막 줄의 최솟값을
     * 취하면 어디서 끝나도 값을 치르지 않는다(끝 자유).
     *
     * @return 가장 잘 맞는 구간과의 편집거리. text가 비었으면 검색어 길이.
     */
    public static int bestSubstringDistance(String keyword, String text) {
        if (keyword == null || keyword.isEmpty()) {
            return 0;
        }
        if (text == null || text.isEmpty()) {
            return keyword.length();
        }

        int keywordLength = keyword.length();
        int textLength = text.length();

        // 표 전체(길이 x 길이)를 들고 있을 필요는 없다. 한 줄을 계산할 때
        // 바로 윗줄만 있으면 되므로 두 줄만 번갈아 쓴다.
        int[] previous = new int[textLength + 1];
        int[] current = new int[textLength + 1];

        for (int i = 1; i <= keywordLength; i++) {
            // 검색어를 i글자 봤는데 text를 하나도 안 봤다면, i글자를 전부 지워야 한다.
            current[0] = i;

            for (int j = 1; j <= textLength; j++) {
                int substitution = keyword.charAt(i - 1) == text.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitution
                );
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        int best = Integer.MAX_VALUE;
        for (int j = 0; j <= textLength; j++) {
            best = Math.min(best, previous[j]);
        }
        return best;
    }
}
