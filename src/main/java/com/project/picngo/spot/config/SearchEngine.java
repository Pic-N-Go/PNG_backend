package com.project.picngo.spot.config;

/**
 * 키워드 검색 구현 방식. picngo.search.engine 설정으로 고른다.
 *
 * <p>두 방식을 코드에 같이 두는 이유는 성능·품질을 같은 조건에서 대조하기 위해서다.
 * 엔드포인트도, 부하 스크립트도, 골든셋도 그대로 두고 설정 하나만 바꿔 재측정하면
 * 두 결과의 차이를 인덱스 방식 차이로만 귀속시킬 수 있다.
 */
public enum SearchEngine {

    /**
     * {@code LOWER(col) LIKE '%키워드%'}. 선행 와일드카드라 B-Tree 인덱스를 쓸 수 없어
     * 항상 풀스캔이 된다. 10만 건 기준 EXPLAIN에서 type=ALL, key=NULL로 확인됨.
     */
    LIKE,

    /**
     * {@code MATCH(...) AGAINST(... IN BOOLEAN MODE)} + ngram 파서.
     * 인덱스는 docs/search-fulltext-index-migration.sql로 따로 만들어야 한다.
     */
    FULLTEXT
}
