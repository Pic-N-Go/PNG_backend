package com.project.picngo.spot.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전문검색식 변환은 틀려도 예외가 안 나고 "결과 집합만 조용히 달라진다".
 * LIKE 방식과의 비교가 실험의 전부인데 매칭 의미가 어긋나면 비교가 성립하지 않으므로
 * 여기서 규칙을 고정한다.
 */
class FullTextKeywordTest {

    @Test
    @DisplayName("검색어를 큰따옴표로 감싸 구문 검색식으로 만든다")
    void wrapsInQuotesForPhraseSearch() {
        // 따옴표가 없으면 ngram 토큰(한라, 라산)이 OR로 묶여
        // '한라'만 든 스팟까지 걸린다. LIKE '%한라산%'과 의미가 달라진다.
        assertThat(FullTextKeyword.toPhrase("한라산")).isEqualTo("\"한라산\"");
    }

    @Test
    @DisplayName("공백이 있는 검색어도 하나의 구문으로 유지한다")
    void keepsMultiWordAsSinglePhrase() {
        assertThat(FullTextKeyword.toPhrase("강남 마이스")).isEqualTo("\"강남 마이스\"");
    }

    @Test
    @DisplayName("BOOLEAN MODE 연산자는 공백으로 치환한다")
    void stripsBooleanOperators() {
        assertThat(FullTextKeyword.toPhrase("한라+산")).isEqualTo("\"한라 산\"");
        assertThat(FullTextKeyword.toPhrase("남산*")).isEqualTo("\"남산\"");
        assertThat(FullTextKeyword.toPhrase("-공원")).isEqualTo("\"공원\"");
    }

    @Test
    @DisplayName("사용자가 넣은 큰따옴표는 제거한다 - 우리가 감싸는 따옴표와 충돌해 구문이 깨진다")
    void removesUserSuppliedQuotes() {
        assertThat(FullTextKeyword.toPhrase("\"남산\"")).isEqualTo("\"남산\"");
        assertThat(FullTextKeyword.toPhrase("남\"산")).isEqualTo("\"남 산\"");
    }

    @Test
    @DisplayName("연속 공백은 하나로 접고 양 끝은 잘라낸다")
    void collapsesWhitespace() {
        assertThat(FullTextKeyword.toPhrase("  남산   타워  ")).isEqualTo("\"남산 타워\"");
    }

    @Test
    @DisplayName("연산자만 남는 검색어와 null은 변환 불가(null)로 처리한다")
    void returnsNullWhenNothingSearchableRemains() {
        assertThat(FullTextKeyword.toPhrase("+++")).isNull();
        assertThat(FullTextKeyword.toPhrase("   ")).isNull();
        assertThat(FullTextKeyword.toPhrase(null)).isNull();
    }

    @Test
    @DisplayName("띄어쓰기 무시 검색식은 공백을 전부 지운다 - 대상 컬럼도 공백을 뺀 값이라 짝이 맞아야 한다")
    void spacelessPhraseRemovesAllWhitespace() {
        assertThat(FullTextKeyword.toSpacelessPhrase("갈 산공원")).isEqualTo("\"갈산공원\"");
        assertThat(FullTextKeyword.toSpacelessPhrase("강남 마이스 관광특구")).isEqualTo("\"강남마이스관광특구\"");
    }

    @Test
    @DisplayName("이미 붙여 쓴 검색어는 그대로 둔다")
    void spacelessPhraseKeepsAlreadyJoinedKeyword() {
        assertThat(FullTextKeyword.toSpacelessPhrase("강남마이스관광특구")).isEqualTo("\"강남마이스관광특구\"");
    }

    @Test
    @DisplayName("띄어쓰기 무시 검색식도 BOOLEAN MODE 연산자를 걷어낸다")
    void spacelessPhraseStripsOperators() {
        assertThat(FullTextKeyword.toSpacelessPhrase("갈+산 공원*")).isEqualTo("\"갈산공원\"");
        assertThat(FullTextKeyword.toSpacelessPhrase("+ + +")).isNull();
        assertThat(FullTextKeyword.toSpacelessPhrase(null)).isNull();
    }

    @Test
    @DisplayName("문장부호도 전부 지운다 - 저장 컬럼(search_norm)과 같은 규칙이어야 짝이 맞는다")
    void spacelessPhraseStripsPunctuation() {
        // 괄호만 지우고 저장값에는 남겨뒀더니 '극락사(서울)'이 자기 이름으로도 안 나왔다.
        assertThat(FullTextKeyword.toSpacelessPhrase("극락사(서울)")).isEqualTo("\"극락사서울\"");
        assertThat(FullTextKeyword.toSpacelessPhrase("국립4·19민주묘지")).isEqualTo("\"국립419민주묘지\"");
        assertThat(FullTextKeyword.toSpacelessPhrase("동대문디자인플라자(DDP)")).isEqualTo("\"동대문디자인플라자DDP\"");
    }

    @Test
    @DisplayName("한글·영문·숫자는 남기고 나머지만 지운다")
    void spacelessPhraseKeepsSearchableCharacters() {
        assertThat(FullTextKeyword.toSpacelessPhrase("N서울타워2")).isEqualTo("\"N서울타워2\"");
        assertThat(FullTextKeyword.toSpacelessPhrase("!@#$%")).isNull();
    }
}
