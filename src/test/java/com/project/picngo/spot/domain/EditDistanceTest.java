package com.project.picngo.spot.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 단계가 존재하는 이유가 되는 두 케이스를 고정한다.
 *
 * <p>'헙재'(2글자)와 '오셜록'(3글자)은 앞 단계의 ngram 유사도 검색으로는 원리적으로
 * 잡을 수 없다 - 실제 DB에서 MATCH 점수가 0으로 확인됐다. 여기서 1이 나와야
 * 이 단계를 추가한 값어치가 있다.
 */
class EditDistanceTest {

    @Test
    @DisplayName("가운데 글자 오타 - '오셜록'으로 '오설록티뮤지엄'을 찾는다")
    void findsSpotWithMiddleCharacterTypo() {
        assertThat(EditDistance.bestSubstringDistance("오셜록", "오설록티뮤지엄")).isEqualTo(1);
    }

    @Test
    @DisplayName("두 글자 검색어 오타 - '헙재'로 '협재해수욕장'을 찾는다")
    void findsSpotWithTwoCharacterTypo() {
        assertThat(EditDistance.bestSubstringDistance("헙재", "협재해수욕장")).isEqualTo(1);
    }

    @Test
    @DisplayName("이름의 일부만 쳐도 거리가 늘지 않는다 - 부분 일치라 뒷부분은 값을 치르지 않는다")
    void partialKeywordCostsNothingForTheRest() {
        // 통째로 비교하면 '협재' vs '협재해수욕장'은 4글자를 더 붙여야 해서 거리가 4다.
        // 사용자는 이름의 앞부분만 치는 게 보통이므로 그 비용을 물리면 안 된다.
        assertThat(EditDistance.bestSubstringDistance("협재", "협재해수욕장")).isZero();
    }

    @Test
    @DisplayName("이름 가운데에 있는 말로도 찾는다 - 시작 위치가 자유롭다")
    void matchesInTheMiddleOfText() {
        assertThat(EditDistance.bestSubstringDistance("해수욕", "협재해수욕장")).isZero();
        assertThat(EditDistance.bestSubstringDistance("해숙욕", "협재해수욕장")).isEqualTo(1);
    }

    @Test
    @DisplayName("전혀 다른 이름은 거리가 크게 나온다")
    void unrelatedTextHasLargeDistance() {
        int distance = EditDistance.bestSubstringDistance("한라산", "협재해수욕장");

        assertThat(distance).isGreaterThan(EditDistance.allowedDistance(3));
    }

    @Test
    @DisplayName("글자가 빠지거나 더 들어간 오타도 잡는다")
    void handlesMissingAndExtraCharacters() {
        assertThat(EditDistance.bestSubstringDistance("오설", "오설록티뮤지엄")).isZero();
        assertThat(EditDistance.bestSubstringDistance("오설로록", "오설록티뮤지엄")).isEqualTo(1);
    }

    @Test
    @DisplayName("허용 오차는 3글자 이상부터 유효하며 검색어가 길수록 늘지만 상한이 있다")
    void allowedDistanceGrowsWithLengthUpToACap() {
        assertThat(EditDistance.allowedDistance(2)).isEqualTo(0);
        assertThat(EditDistance.allowedDistance(3)).isEqualTo(1);
        assertThat(EditDistance.allowedDistance(6)).isEqualTo(2);
        assertThat(EditDistance.allowedDistance(9)).isEqualTo(3);
        // 아무리 길어도 3을 넘지 않는다. 더 풀어주면 전혀 다른 이름이 후보에 들어온다.
        assertThat(EditDistance.allowedDistance(60)).isEqualTo(3);
    }

    @Test
    @DisplayName("빈 문자열을 넘겨도 예외 없이 처리한다")
    void handlesEmptyInput() {
        assertThat(EditDistance.bestSubstringDistance("", "협재해수욕장")).isZero();
        assertThat(EditDistance.bestSubstringDistance(null, "협재해수욕장")).isZero();
        assertThat(EditDistance.bestSubstringDistance("협재", "")).isEqualTo(2);
        assertThat(EditDistance.bestSubstringDistance("협재", null)).isEqualTo(2);
    }
}
