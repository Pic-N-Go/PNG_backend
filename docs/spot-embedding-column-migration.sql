-- 의미 검색용 임베딩 컬럼(spot.embedding) 추가 마이그레이션
--
-- ⚠️ 반드시 새 버전 애플리케이션을 배포하기 "전에" 실행해야 한다.
--
--    운영은 ddl-auto=validate 라서(application-prod.yaml) Hibernate가 엔티티와
--    실제 테이블을 대조한 뒤, 없는 컬럼이 있으면 기동을 거부한다. 로컬은
--    ddl-auto=update 라 알아서 만들어지므로 이 차이를 놓치기 쉽다.
--    이 컬럼 없이 배포하면 앱이 아예 뜨지 않는다:
--      Schema-validation: missing column [embedding] in table [spot]
--
-- 적용:
--   mysql -u root -p --default-character-set=utf8mb4 picngo -e "source docs/spot-embedding-column-migration.sql"
--
--   EC2(도커 컨테이너로 MySQL을 띄운 경우):
--   docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo \
--     < docs/spot-embedding-column-migration.sql
--
-- 재실행 안전(멱등): 이미 컬럼이 있으면 건너뛴다.


-- ─────────────────────────────────────────────────────────────
-- 1) 컬럼 추가
--
-- 타입은 엔티티의 @Column(columnDefinition = "MEDIUMBLOB")과 정확히 같아야 한다.
-- 다르면 위 validate에서 타입 불일치로 다시 막힌다.
--
-- 저장하는 값: float32 벡터를 그대로 이진화한 것(리틀엔디언).
-- text-embedding-3-small 기준 1536차원 x 4바이트 = 6,144바이트.
-- MEDIUMBLOB(최대 16MB)은 넉넉하다 - 나중에 더 큰 모델로 바꿔도 컬럼은 그대로 쓴다.
--
-- NULL 허용이어야 한다. 기존 스팟은 전부 NULL로 시작하고, 백필 배치나
-- 관리자 API(POST /admin/embeddings/backfill)가 나중에 채운다.
-- NULL인 스팟은 의미 검색 후보에서 조용히 빠질 뿐 오류가 나지 않는다.
DROP PROCEDURE IF EXISTS add_spot_embedding_column;
DELIMITER $$
CREATE PROCEDURE add_spot_embedding_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'spot'
          AND COLUMN_NAME = 'embedding'
    ) THEN
        ALTER TABLE spot
            ADD COLUMN embedding MEDIUMBLOB NULL
                COMMENT '의미 검색용 임베딩 벡터. float32 배열을 그대로 이진 저장(4층 폴백 전용, 응답 DTO에 노출 안 함)';
    END IF;
END$$
DELIMITER ;

CALL add_spot_embedding_column();
DROP PROCEDURE add_spot_embedding_column;


-- ─────────────────────────────────────────────────────────────
-- 2) 검증
--
-- COLUMN_TYPE이 'mediumblob', IS_NULLABLE이 'YES'로 나와야 한다.
-- 한 줄도 안 나오면 1)이 적용되지 않은 것이니 배포하면 안 된다.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'spot'
  AND COLUMN_NAME = 'embedding';

-- 채워진 정도. 방금 추가했다면 with_embedding이 0인 게 정상이다.
-- 배포 후 관리자 API로 백필하고 나면 total과 같아진다.
--   GET  /admin/embeddings           현황 조회
--   POST /admin/embeddings/backfill  일괄 채우기
SELECT
    COUNT(*)                                    AS total,
    SUM(embedding IS NOT NULL)                  AS with_embedding,
    SUM(embedding IS NULL)                      AS missing
FROM spot
WHERE status = 'APPROVED' AND is_active = true;


-- ─────────────────────────────────────────────────────────────
-- 되돌리기
--
--   ALTER TABLE spot DROP COLUMN embedding;
--
-- ⚠️ 컬럼을 지우면 계산해둔 임베딩이 전부 사라진다(다시 채우려면 외부 API 비용이
--    또 든다). 그리고 엔티티에 필드가 남아 있는 한 validate에서 다시 막히므로,
--    되돌리려면 이전 버전 애플리케이션으로 함께 롤백해야 한다.
