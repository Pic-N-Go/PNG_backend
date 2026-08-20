package com.project.picngo.external;

import java.util.Optional;

/**
 * 텍스트를 의미 검색용 벡터로 바꾼다. 4층 검색 폴백과 스팟 임베딩 백필이 이 인터페이스만 본다.
 *
 * <p>실패는 예외가 아니라 빈 값으로 돌려준다. 이 호출 하나 때문에 스팟 등록이나
 * 검색 요청 전체가 실패하면 안 된다 - 카카오 로컬 검색 클라이언트가 실패를
 * {@code PlaceSearchResult.error()}로 흡수하는 것과 같은 이유다.
 */
public interface EmbeddingClient {

    Optional<float[]> embed(String text);
}
