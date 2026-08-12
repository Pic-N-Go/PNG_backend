package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.enums.SpotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SpotRepository extends JpaRepository<Spot, Long> {

    Optional<Spot> findByTourContentId(String tourContentId);

    @Query(value = """
            SELECT *, (6371 * acos(cos(radians(:lat)) * cos(radians(latitude))
                * cos(radians(longitude) - radians(:lng)) + sin(radians(:lat)) * sin(radians(latitude)))) AS distance
            FROM spot
            WHERE is_active = true
            HAVING distance < :radiusKm
            ORDER BY distance
            LIMIT :limit
            """, nativeQuery = true)
    List<Spot> findNearbySpots(@Param("lat") Double lat, @Param("lng") Double lng,
                               @Param("radiusKm") Double radiusKm, @Param("limit") int limit);

    // 유저 관심테마와 겹치는 스팟을 앞으로, 나머지는 기존 인기순.
    // 관심테마가 없는 유저(소셜 가입 등)는 EXISTS가 전부 0이라 인기순으로 자연 폴백된다.
    // ponytail: 행마다 EXISTS + 전체 정렬. 스팟 수천 건 규모에선 충분, 수십만 건 되면 매칭 스팟만 먼저 뽑아 합치는 방식으로 교체
    @Query(value = """
            SELECT s.* FROM spot s
            WHERE s.is_active = true
            ORDER BY EXISTS (
                SELECT 1 FROM spot_categories sc
                JOIN user_spot_categories uc ON uc.category = sc.category
                WHERE sc.spot_id = s.id AND uc.user_id = :userId
            ) DESC,
            (s.review_count + s.bookmark_count) DESC, RAND()
            LIMIT :limit
            """, nativeQuery = true)
    List<Spot> findRecommendedSpots(@Param("userId") Long userId, @Param("limit") int limit);

    Page<Spot> findAllByStatusAndIsActiveTrue(
            SpotStatus status,
            Pageable pageable
    );

    // 요청한 카테고리 중 하나라도 가진 스팟(OR 조합). 스팟이 다중 태그라 AND면 교집합이 거의 안 남는다.
    // 파생 메서드로 표현 불가 → 컬렉션 상관 서브쿼리
    @Query("""
select s from Spot s
where exists (select c from s.categories c where c in :categories)
and s.status = :status and s.isActive = true
""")
    Page<Spot> findAllByCategoriesAndStatusAndIsActiveTrue(
            @Param("categories") Collection<SpotCategory> categories,
            @Param("status") SpotStatus status,
            Pageable pageable
    );

    List<Spot> findListByStatusAndIsActiveTrue(
            SpotStatus status,
            Pageable pageable
    );

    @Query("""
select s from Spot s
where exists (select c from s.categories c where c in :categories)
and s.status = :status and s.isActive = true
""")
    List<Spot> findListByCategoriesAndStatusAndIsActiveTrue(
            @Param("categories") Collection<SpotCategory> categories,
            @Param("status") SpotStatus status,
            Pageable pageable
    );

    // 키워드로 스팟 이름, 주소, 설명 검색 (카테고리 필터 없음)
    @Query("""
select s
from Spot s
where s.status = :status
and s.isActive = true
and (
lower(s.name) like lower(concat('%', :keyword, '%'))
or lower(s.address) like lower(concat('%', :keyword, '%'))
or lower(coalesce(s.overview, '')) like lower(concat('%', :keyword, '%'))
)
""")
    Page<Spot> searchSpots(
            @Param("keyword") String keyword,
            @Param("status") SpotStatus status,
            Pageable pageable
    );

    // 위와 동일하되 요청한 카테고리 중 하나라도 가진 스팟으로 한정(OR 조합).
    // 컬렉션 파라미터는 null을 넘기면 IN 절 렌더링이 깨지므로, 필터 없는 경우는 위 메서드로 분기한다.
    @Query("""
select s
from Spot s
where s.status = :status
and s.isActive = true
and exists (select c from s.categories c where c in :categories)
and (
lower(s.name) like lower(concat('%', :keyword, '%'))
or lower(s.address) like lower(concat('%', :keyword, '%'))
or lower(coalesce(s.overview, '')) like lower(concat('%', :keyword, '%'))
)
""")
    Page<Spot> searchSpotsByCategories(
            @Param("keyword") String keyword,
            @Param("categories") Collection<SpotCategory> categories,
            @Param("status") SpotStatus status,
            Pageable pageable
    );

    // ── FULLTEXT(ngram) 방식. 위 LIKE 방식과 대조하기 위한 것으로, 둘 중 무엇을 쓸지는
    //    picngo.search.engine 설정이 정한다. 인덱스는 ddl-auto가 만들지 않으므로
    //    docs/search-fulltext-index-migration.sql을 먼저 적용해야 한다.
    //
    //    JPQL에는 MATCH ... AGAINST가 없어 네이티브 쿼리로 쓴다. 그래서:
    //      - status를 enum이 아니라 String으로 받는다. 네이티브 쿼리에서 enum 바인딩은
    //        문자열/서수 중 무엇으로 나갈지 명확하지 않아, 호출부에서 name()을 넘기게 했다.
    //      - ORDER BY를 쿼리에 직접 박고 정렬 없는 Pageable을 받는다. 네이티브 쿼리에
    //        Sort가 붙으면 Spring이 ORDER BY를 덧붙여 절이 두 번 생긴다.
    //        정렬 기준을 LIKE 방식(createdAt DESC)과 똑같이 맞춰야 두 방식의 차이가
    //        인덱스 때문이라고 말할 수 있다. 관련도 정렬로 바꾸면 변수가 둘이 된다.
    //      - MATCH()의 컬럼 목록은 FULLTEXT 인덱스 정의와 순서까지 같아야 한다.
    //        다르면 ERROR 1191로 인덱스를 못 찾는다.
    @Query(value = """
            select s.* from spot s
            where s.status = :status
            and s.is_active = true
            and match(s.name, s.address, s.overview) against (:keyword in boolean mode)
            order by s.created_at desc
            """,
            countQuery = """
            select count(*) from spot s
            where s.status = :status
            and s.is_active = true
            and match(s.name, s.address, s.overview) against (:keyword in boolean mode)
            """,
            nativeQuery = true)
    Page<Spot> searchSpotsFullText(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );

    @Query(value = """
            select s.* from spot s
            where s.status = :status
            and s.is_active = true
            and exists (
                select 1 from spot_categories sc
                where sc.spot_id = s.id and sc.category in (:categories)
            )
            and match(s.name, s.address, s.overview) against (:keyword in boolean mode)
            order by s.created_at desc
            """,
            countQuery = """
            select count(*) from spot s
            where s.status = :status
            and s.is_active = true
            and exists (
                select 1 from spot_categories sc
                where sc.spot_id = s.id and sc.category in (:categories)
            )
            and match(s.name, s.address, s.overview) against (:keyword in boolean mode)
            """,
            nativeQuery = true)
    Page<Spot> searchSpotsFullTextByCategories(
            @Param("keyword") String keyword,
            @Param("categories") Collection<String> categories,
            @Param("status") String status,
            Pageable pageable
    );

    // ── 띄어쓰기 무시 폴백. spot.search_norm은 이름/주소에서 공백을 뺀 생성 컬럼이다
    //    (docs/search-normalized-column-migration.sql). 위 전문검색이 0건일 때만 탄다.
    //
    //    엔티티에 매핑하지 않은 컬럼이라 JPQL로는 접근할 수 없어 네이티브 쿼리로 쓴다.
    //    나머지 제약(String status, 정렬 없는 Pageable)은 위 FULLTEXT 쿼리와 같은 이유다.
    @Query(value = """
            select s.* from spot s
            where s.status = :status
            and s.is_active = true
            and match(s.search_norm) against (:keyword in boolean mode)
            order by s.created_at desc
            """,
            countQuery = """
            select count(*) from spot s
            where s.status = :status
            and s.is_active = true
            and match(s.search_norm) against (:keyword in boolean mode)
            """,
            nativeQuery = true)
    Page<Spot> searchSpotsNormalized(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );

    @Query(value = """
            select s.* from spot s
            where s.status = :status
            and s.is_active = true
            and exists (
                select 1 from spot_categories sc
                where sc.spot_id = s.id and sc.category in (:categories)
            )
            and match(s.search_norm) against (:keyword in boolean mode)
            order by s.created_at desc
            """,
            countQuery = """
            select count(*) from spot s
            where s.status = :status
            and s.is_active = true
            and exists (
                select 1 from spot_categories sc
                where sc.spot_id = s.id and sc.category in (:categories)
            )
            and match(s.search_norm) against (:keyword in boolean mode)
            """,
            nativeQuery = true)
    Page<Spot> searchSpotsNormalizedByCategories(
            @Param("keyword") String keyword,
            @Param("categories") Collection<String> categories,
            @Param("status") String status,
            Pageable pageable
    );

    // ── 유사도 검색. 앞의 두 단계가 모두 0건일 때만 타는 마지막 수단이다.
    //
    //    위 폴백과 같은 색인(ft_spot_search_norm)을 쓰지만 조회 방식이 다르다.
    //    구문 검색은 조각들이 그 순서로 붙어 있어야 하는데, 오타가 난 검색어는
    //    원본과 정확히 일치할 수 없어 영원히 못 찾는다. 여기서는 조건을 풀고
    //    "두 글자 조각이 몇 개나 겹치는지"로 점수를 매겨 비슷한 것을 찾는다.
    //
    //    정렬이 created_at이 아니라 관련도순인 것이 앞 단계들과 다른 점이다.
    //    조각 하나만 겹쳐도 후보에 들어오므로, 최신순으로 정렬하면 엉뚱한 스팟이
    //    위로 올라와 쓸모가 없어진다. 점수순이어야 비슷한 것이 먼저 나온다.
    //
    //    MATCH를 WHERE와 ORDER BY에 두 번 쓰지만 MySQL이 한 번만 계산한다.
    @Query(value = """
            select s.* from spot s
            where s.status = :status
            and s.is_active = true
            and match(s.search_norm) against (:keyword)
            order by match(s.search_norm) against (:keyword) desc
            """,
            countQuery = """
            select count(*) from spot s
            where s.status = :status
            and s.is_active = true
            and match(s.search_norm) against (:keyword)
            """,
            nativeQuery = true)
    Page<Spot> searchSpotsSimilar(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );

    @Query(value = """
            select s.* from spot s
            where s.status = :status
            and s.is_active = true
            and exists (
                select 1 from spot_categories sc
                where sc.spot_id = s.id and sc.category in (:categories)
            )
            and match(s.search_norm) against (:keyword)
            order by match(s.search_norm) against (:keyword) desc
            """,
            countQuery = """
            select count(*) from spot s
            where s.status = :status
            and s.is_active = true
            and exists (
                select 1 from spot_categories sc
                where sc.spot_id = s.id and sc.category in (:categories)
            )
            and match(s.search_norm) against (:keyword)
            """,
            nativeQuery = true)
    Page<Spot> searchSpotsSimilarByCategories(
            @Param("keyword") String keyword,
            @Param("categories") Collection<String> categories,
            @Param("status") String status,
            Pageable pageable
    );

    // 지도 화면의 현재 영역 안에 있는 스팟 조회 (카테고리 필터 없음)
    @Query("""
select s
from Spot s
where s.status = :status
and s.isActive = true
and s.latitude between :southWestLat and :northEastLat
and s.longitude between :southWestLng and :northEastLng
order by s.photogenicScore desc, s.bookmarkCount desc
""")
    List<Spot> findSpotsInMapBounds(
            @Param("southWestLat") Double southWestLat,
            @Param("southWestLng") Double southWestLng,
            @Param("northEastLat") Double northEastLat,
            @Param("northEastLng") Double northEastLng,
            @Param("status") SpotStatus status,
            Pageable pageable
    );

    // 위와 동일하되 요청한 카테고리 중 하나라도 가진 스팟으로 한정(OR 조합).
    @Query("""
select s
from Spot s
where s.status = :status
and s.isActive = true
and exists (select c from s.categories c where c in :categories)
and s.latitude between :southWestLat and :northEastLat
and s.longitude between :southWestLng and :northEastLng
order by s.photogenicScore desc, s.bookmarkCount desc
""")
    List<Spot> findSpotsInMapBoundsByCategories(
            @Param("southWestLat") Double southWestLat,
            @Param("southWestLng") Double southWestLng,
            @Param("northEastLat") Double northEastLat,
            @Param("northEastLng") Double northEastLng,
            @Param("categories") Collection<SpotCategory> categories,
            @Param("status") SpotStatus status,
            Pageable pageable
    );

    Optional<Spot> findByIdAndStatusAndIsActiveTrue(
            Long id,
            SpotStatus status
    );

    List<Spot> findByIdIn(List<Long> ids);

    long countByIdIn(Collection<Long> ids);
}
