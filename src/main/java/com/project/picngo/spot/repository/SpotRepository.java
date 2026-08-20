package com.project.picngo.spot.repository;

import com.project.picngo.spot.domain.Spot;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.enums.SpotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 유저 관심테마와 겹치는 스팟만 반환한다. 겹치는 테마가 많은 순 → 인기순.
    // INNER JOIN이라 관심테마가 없는 유저(소셜 가입 등)는 빈 목록이 되고, 클라이언트가
    // 그걸 근거로 "관심 테마 설정" 안내를 띄운다 — 인기순으로 채우면 "관심 스팟" 제목 아래
    // 무관한 스팟이 섞이고 사용자는 구분할 방법이 없다.
    //
    // 집계를 파생 테이블로 분리한 이유: spot을 직접 GROUP BY하면 SELECT s.*가 임시 테이블로
    // 들어가면서 embedding(MEDIUMBLOB)과 TEXT 컬럼들을 끌고 가 디스크로 넘어간다.
    // 좁은 조인 테이블만 묶고 spot은 뒤에 붙인다 (findEmbeddingCandidates와 같은 판단).
    // COUNT(DISTINCT)인 이유: user_spot_categories에 (user_id, category) 유니크 제약이 없어
    // 중복 행이 한 카테고리의 가중치를 두 배로 만들 수 있다.
    // 정렬 마지막이 RAND()가 아니라 s.id인 이유: 새로고침마다 순서가 바뀌면 캐시가 무의미하고
    // 목록이 흔들려 보인다.
    // status를 String으로 받는 이유는 위 FULLTEXT 쿼리들과 같다(네이티브 enum 바인딩 문제).
    // 조인 조건 uc.category = sc.category는 spot_categories의 유니크 키(spot_id, category)로는
    // 커버되지 않아 idx_spot_categories_category(category, spot_id)를 V4에서 추가했다.
    //
    // @DataJpaTest로 덮지 못한다: 테스트는 H2(create-drop)에서 도는데 User.spotCategories를 저장하면
    // Hibernate가 만든 체크 제약("CATEGORY" IN (...))에 걸려 insert 자체가 실패한다(값은 맞는데도).
    // 관심테마 행을 못 넣으니 이 쿼리에 줄 입력을 만들 수 없다. 검증은 MySQL 통합 테스트가 붙을 때까지
    // 수동 확인에 의존한다 — 바꾸기 전에 이 사실을 먼저 알고 시작할 것.
    @Query(value = """
            SELECT s.* FROM spot s
            JOIN (SELECT sc.spot_id, COUNT(DISTINCT sc.category) AS match_count
                  FROM spot_categories sc
                  JOIN user_spot_categories uc ON uc.category = sc.category
                  WHERE uc.user_id = :userId
                  GROUP BY sc.spot_id) m ON m.spot_id = s.id
            WHERE s.is_active = true AND s.status = :status
            ORDER BY m.match_count DESC, (s.review_count + s.bookmark_count) DESC, s.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Spot> findRecommendedSpots(@Param("userId") Long userId,
                                    @Param("status") String status,
                                    @Param("limit") int limit);

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

    // 키워드로 스팟 이름, 주소 검색 (카테고리 필터 없음)
    //
    // overview(설명)는 검색 대상에서 뺐다. 긴 산문이라 두 글자만 겹쳐도 걸려서,
    // '테'로 검색하면 405건 중 396건이 설명만 맞은 결과였다('아로마테라피'의 '테').
    // 정렬이 최신순이라 정작 이름이 맞는 47건(테미공원 등)이 첫 페이지 밖으로 밀려났다.
    //
    // 주소는 남긴다 — '해남'처럼 지역으로 찾는 검색이 흔하고, 이름에 지역명이 없는
    // 스팟은 주소가 유일한 경로다. 대신 이름이 맞은 것을 항상 앞에 둔다.
    //
    // ORDER BY를 쿼리에 직접 박으므로 호출부는 정렬 없는 Pageable을 넘겨야 한다.
    // Sort가 함께 오면 Spring이 절을 덧붙여 의도한 우선순위가 뒤로 밀린다.
    @Query("""
select s
from Spot s
where s.status = :status
and s.isActive = true
and (
lower(s.name) like lower(concat('%', :keyword, '%'))
or lower(s.address) like lower(concat('%', :keyword, '%'))
)
order by case when lower(s.name) like lower(concat('%', :keyword, '%')) then 0 else 1 end,
s.createdAt desc
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
)
order by case when lower(s.name) like lower(concat('%', :keyword, '%')) then 0 else 1 end,
s.createdAt desc
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
            and match(s.name, s.address) against (:keyword in boolean mode)
            order by (lower(s.name) like lower(concat('%', :rawKeyword, '%'))) desc, s.created_at desc
            """,
            countQuery = """
            select count(*) from spot s
            where s.status = :status
            and s.is_active = true
            and match(s.name, s.address) against (:keyword in boolean mode)
            """,
            nativeQuery = true)
    Page<Spot> searchSpotsFullText(
            @Param("keyword") String keyword,
            @Param("rawKeyword") String rawKeyword,
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
            and match(s.name, s.address) against (:keyword in boolean mode)
            order by (lower(s.name) like lower(concat('%', :rawKeyword, '%'))) desc, s.created_at desc
            """,
            countQuery = """
            select count(*) from spot s
            where s.status = :status
            and s.is_active = true
            and exists (
                select 1 from spot_categories sc
                where sc.spot_id = s.id and sc.category in (:categories)
            )
            and match(s.name, s.address) against (:keyword in boolean mode)
            """,
            nativeQuery = true)
    Page<Spot> searchSpotsFullTextByCategories(
            @Param("keyword") String keyword,
            @Param("rawKeyword") String rawKeyword,
            @Param("categories") Collection<String> categories,
            @Param("status") String status,
            Pageable pageable
    );

    // ── 띄어쓰기 무시 폴백. spot.search_norm은 이름/주소에서 공백을 뺀 생성 컬럼이다
    //    (docs/search-normalized-column-migration.sql). 위 전문검색이 0건일 때만 탄다.
    //
    //    엔티티에 매핑하지 않은 컬럼이라 JPQL로는 접근할 수 없어 네이티브 쿼리로 쓴다.
    //    나머지 제약(String status, 정렬 없는 Pageable)은 위 FULLTEXT 쿼리와 같은 이유다.
    //
    //    정렬용 rawKeyword는 검색식(:keyword)과 다른 값이다. :keyword는 BOOLEAN MODE 구문식이라
    //    따옴표가 붙어 있어(FullTextKeyword.toSpacelessPhrase) LIKE 비교에 쓰면 아무것도 맞지 않는다.
    //    비교 대상도 s.name이 아니라 s.search_norm이다 — 이 경로에 온 이유가 띄어쓰기 불일치라,
    //    공백이 남아 있는 s.name과는 짝이 맞지 않는다.
    //
    //    이름 먼저 정렬은 search_norm 전체가 아니라 앞 조각으로 따로 본다. search_norm은
    //    CONCAT_WS(' ', 정규화한 name, 정규화한 address)라 이름만 맞은 결과와 주소만 맞은 결과가
    //    같은 값을 받아버리고, 그러면 주소만 맞은 최신 스팟이 이름이 맞는 옛 스팟보다 앞에 온다.
    //    정규화한 name에는 공백이 없으므로 첫 조각(substring_index(..., ' ', 1))이 곧 이름이다.
    //    REGEXP_REPLACE를 다시 쓰지 않는 이유 = 정규화 규칙을 세 곳에 복제하지 않기 위해서다.
    @Query(value = """
            select s.* from spot s
            where s.status = :status
            and s.is_active = true
            and match(s.search_norm) against (:keyword in boolean mode)
            order by (substring_index(s.search_norm, ' ', 1) like concat('%', :rawKeyword, '%')) desc,
                     (s.search_norm like concat('%', :rawKeyword, '%')) desc,
                     s.created_at desc
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
            @Param("rawKeyword") String rawKeyword,
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
            order by (substring_index(s.search_norm, ' ', 1) like concat('%', :rawKeyword, '%')) desc,
                     (s.search_norm like concat('%', :rawKeyword, '%')) desc,
                     s.created_at desc
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
            @Param("rawKeyword") String rawKeyword,
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

    // ── 오타 교정(편집거리) 폴백 전용 ──────────────────────────────────────
    //
    // 이름과 주소만 읽는다. 편집거리는 자바에서 계산하므로 DB는 비교할 원문만
    // 넘겨주면 된다. 위 임베딩 조회와 같은 이유로 프로젝션을 쓴다 - Spot 전체를
    // 로딩하면 EAGER 연관 때문에 스팟마다 추가 SELECT가 붙는다.
    //
    // 설명문(overview)은 일부러 뺐다. 오타 교정의 대상은 사용자가 치려던 '이름'이지
    // 본문이 아니다. 긴 본문까지 넣으면 우연히 비슷한 구간이 어디선가 걸려서
    // 엉뚱한 스팟이 상위로 올라온다.
    @Query("""
select s.id as id, s.name as name, s.address as address
from Spot s
where s.status = :status
and s.isActive = true
""")
    List<FuzzyCandidate> findFuzzyCandidates(@Param("status") SpotStatus status);

    @Query("""
select s.id as id, s.name as name, s.address as address
from Spot s
where s.status = :status
and s.isActive = true
and exists (select c from s.categories c where c in :categories)
""")
    List<FuzzyCandidate> findFuzzyCandidatesByCategories(
            @Param("categories") Collection<SpotCategory> categories,
            @Param("status") SpotStatus status
    );

    interface FuzzyCandidate {
        Long getId();
        String getName();
        String getAddress();
    }

    // ── 4층 검색(의미 검색) 전용 ──────────────────────────────────────────
    //
    // id·embedding만 골라 읽는 이유: 이 조회는 요청마다 활성 스팟 전체를 한 번에
    // 훑는 완전탐색이다(4,500건 규모라 이 방식으로도 충분하다는 게 오늘 실측으로
    // 확인됐다). Spot 엔티티 전체를 로딩하면 accessPoints가 EAGER라 스팟마다
    // 추가 SELECT가 딸려 나가는데, 유사도 계산에는 벡터만 있으면 되므로
    // 프로젝션으로 그 비용을 아예 없앤다.
    @Query("""
select s.id as id, s.embedding as embedding
from Spot s
where s.status = :status
and s.isActive = true
and s.embedding is not null
""")
    List<EmbeddingCandidate> findEmbeddingCandidates(@Param("status") SpotStatus status);

    @Query("""
select s.id as id, s.embedding as embedding
from Spot s
where s.status = :status
and s.isActive = true
and s.embedding is not null
and exists (select c from s.categories c where c in :categories)
""")
    List<EmbeddingCandidate> findEmbeddingCandidatesByCategories(
            @Param("categories") Collection<SpotCategory> categories,
            @Param("status") SpotStatus status
    );

    interface EmbeddingCandidate {
        Long getId();
        byte[] getEmbedding();
    }

    // 임베딩이 아직 없는 스팟을 배치로 뽑는다(백필 대상). 이름·주소·설명문만
    // 있으면 되므로 여기도 프로젝션으로 EAGER 연관을 피한다.
    @Query("""
select s.id as id, s.name as name, s.address as address, s.overview as overview
from Spot s
where s.status = :status
and s.isActive = true
and s.embedding is null
order by s.id
""")
    List<EmbeddingSource> findMissingEmbeddings(@Param("status") SpotStatus status, Pageable pageable);

    interface EmbeddingSource {
        Long getId();
        String getName();
        String getAddress();
        String getOverview();
    }

    // 관리자 화면에서 "얼마나 채워졌나"를 보여주기 위한 집계. 검색 대상이 되는
    // 스팟(승인·활성)만 센다 - 그 밖의 스팟은 어차피 의미 검색에 안 걸린다.
    @Query("select count(s) from Spot s where s.status = :status and s.isActive = true")
    long countSearchable(@Param("status") SpotStatus status);

    @Query("""
select count(s) from Spot s
where s.status = :status
and s.isActive = true
and s.embedding is not null
""")
    long countWithEmbedding(@Param("status") SpotStatus status);

    // 벌크 업데이트로 저장한다. 엔티티를 다시 불러와 저장하면 EAGER 연관까지
    // 딸려온다 - 필드 하나만 바꾸는데 그럴 이유가 없다.
    @Modifying
    @Query("update Spot s set s.embedding = :embedding where s.id = :id")
    void updateEmbedding(@Param("id") Long id, @Param("embedding") byte[] embedding);
}
