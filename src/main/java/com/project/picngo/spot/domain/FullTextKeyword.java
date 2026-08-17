package com.project.picngo.spot.domain;

import java.util.regex.Pattern;

/**
 * 사용자 검색어를 MySQL BOOLEAN MODE 전문검색 식으로 바꾼다.
 *
 * <p>큰따옴표로 감싸 구문(phrase) 검색으로 만드는 것이 핵심이다. ngram 파서는
 * '한라산'을 '한라', '라산' 두 토큰으로 쪼개는데, 따옴표 없이 넘기면 BOOLEAN MODE가
 * 이를 OR로 묶는다. 그러면 '한라'만 들어간 스팟, '라산'만 들어간 스팟까지 전부
 * 걸려서 LIKE '%한라산%'과 전혀 다른 결과가 된다.
 *
 * <p>구문 검색으로 만들면 토큰이 그 순서대로 인접해 나타나는 문서만 매칭되어
 * 부분 문자열 일치와 사실상 같은 의미가 된다. LIKE 방식과 결과 집합을 비교하는 게
 * 실험의 목적이므로, 두 방식의 매칭 의미를 맞춰두지 않으면 비교 자체가 성립하지 않는다.
 */
public final class FullTextKeyword {

    // BOOLEAN MODE가 연산자로 해석하는 문자들. 사용자 입력에 섞여 들어오면
    // 의도치 않은 검색식이 되거나 문법 오류가 나므로 공백으로 바꾼다.
    // 특히 큰따옴표는 우리가 감싸는 따옴표와 충돌해 구문이 깨진다.
    private static final Pattern BOOLEAN_OPERATORS = Pattern.compile("[+\\-<>()~*\"@]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * 띄어쓰기 무시 검색에서 지울 문자 - 한글/영문/숫자가 아닌 전부.
     *
     * <p>⚠️ 이 규칙은 spot.search_norm 생성 컬럼의 정규화 규칙과 <b>정확히 같아야 한다</b>
     * (docs/search-normalized-column-migration.sql). 한쪽만 바꾸면 검색어와 저장값의 짝이
     * 어긋나서, 예외 없이 결과만 조용히 안 나온다.
     *
     * <p>공백만 지우던 초기 버전에서 실제로 그런 일이 있었다. 검색어에서는 BOOLEAN MODE
     * 연산자라는 이유로 괄호를 지웠는데 저장값에는 괄호가 남아 있어서,
     * '극락사(서울)' 같은 이름이 자기 이름으로도 검색되지 않았다.
     *
     * <p>문장부호를 양쪽에서 모두 지우는 편이 맞기도 하다. ngram 파서는 문장부호를
     * 토큰 경계로 취급하므로, 남겨두면 '국립4·19민주묘지'가 '국립4'와 '19민주묘지'로
     * 쪼개져 구문 검색으로는 어차피 찾을 수 없다.
     */
    private static final Pattern NON_SEARCHABLE = Pattern.compile("[^가-힣a-zA-Z0-9]");

    private FullTextKeyword() {
    }

    /**
     * @return 구문 검색식. 연산자를 걷어내고 나면 남는 게 없는 검색어는 null.
     *         (호출부는 null이면 결과 없음으로 처리한다 - 억지로 검색식을 만들면
     *         엉뚱한 결과가 나오고, 예외를 던지면 정상 입력 범위인데도 500이 된다.)
     */
    public static String toPhrase(String keyword) {
        if (keyword == null) {
            return null;
        }

        String cleaned = BOOLEAN_OPERATORS.matcher(keyword).replaceAll(" ");
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim();

        return cleaned.isEmpty() ? null : "\"" + cleaned + "\"";
    }

    /**
     * 공백을 전부 지운 구문 검색식. 띄어쓰기를 무시하고 찾는 폴백 경로에서 쓴다.
     *
     * <p>대상 컬럼(spot.search_norm)도 이름과 주소의 공백을 뺀 값이라, 검색어에서도
     * 같은 방식으로 빼야 짝이 맞는다. 사용자가 공백을 넣었든 뺐든 양방향으로 맞춰진다:
     * '갈 산공원'도 '갈산공원'이 되고, 이미 붙여 쓴 '강남마이스관광특구'는 그대로다.
     *
     * @return 공백 없는 구문 검색식. 남는 게 없으면 null.
     */
    public static String toSpacelessPhrase(String keyword) {
        String cleaned = normalize(keyword);
        return cleaned == null ? null : "\"" + cleaned + "\"";
    }

    /**
     * 따옴표 없는 정규화 검색어. 유사도 검색(NATURAL LANGUAGE MODE)에서 쓴다.
     *
     * <p>{@link #toSpacelessPhrase(String)}와 정규화 규칙은 같고 따옴표만 없다.
     * 그 차이가 검색의 성격을 바꾼다:
     *
     * <ul>
     *   <li>따옴표 O - 조각들이 그 순서 그대로 붙어 있어야 매칭 (부분 문자열 일치와 같음)
     *   <li>따옴표 X - 조각이 몇 개나 겹치는지로 점수를 매김 (비슷한 것 찾기)
     * </ul>
     *
     * <p>오타가 난 검색어는 원본과 정확히 일치할 수 없으므로 구문 검색으로는 영원히 못 찾는다.
     * '갈산공줜'은 두 글자 조각으로 쪼개면 갈산·산공·공줜이 되는데, 저장된 '갈산공원'의
     * 갈산·산공과 셋 중 둘이 겹친다. 이 겹침을 점수로 환산하면 오타를 흡수할 수 있다.
     *
     * <p>대신 조각 하나만 겹쳐도 후보에 들어오므로 엉뚱한 결과가 섞인다. 앞 단계가 전부
     * 0건일 때만 쓰는 마지막 수단인 이유다.
     */
    public static String toSimilarityTerms(String keyword) {
        return normalize(keyword);
    }

    private static String normalize(String keyword) {
        if (keyword == null) {
            return null;
        }

        String cleaned = NON_SEARCHABLE.matcher(keyword).replaceAll("");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
