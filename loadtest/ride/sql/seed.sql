-- ============================================================================
-- 이동수단(ride) 부하테스트용 데이터 시딩
--
--   docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/ride/sql/seed.sql
--
-- 만드는 것
--   [LT-RIDE] 공고 @post_count 건 + 공고당 ride 2건 (TO_HOSPITAL / TO_HOME)
--
-- 왜 직접 심나
--   ride 는 PostService.createPost 안에서 RideService.createRide 로만 생깁니다.
--   BaseInitData 는 postRepository.save() 로 공고를 바로 저장해 그 경로를 안 타기 때문에,
--   **dev DB 의 ride 테이블은 비어 있습니다.** 시딩 없이는 테스트할 대상이 없습니다.
--
-- 읽기 전용 도메인이라 reset.sql 이 없습니다
--   노출된 API 가 GET 둘뿐이라 테스트가 상태를 소모하지 않습니다. 반복 실행해도 됩니다.
--   (RideService 의 updateRide / move / arrive 는 RideController 에서 주석 처리돼 있거나
--    application 도메인에서 내부 호출됩니다 — 이 세트의 대상이 아닙니다.)
--
-- ⚠️ 멱등하지 않습니다 — 돌릴 때마다 공고+ride 한 묶음이 더 쌓입니다.
--    원하는 건수를 정확히 맞추려면 teardown.sql 을 먼저 돌리세요.
-- ============================================================================

SET SESSION cte_max_recursion_depth = 1000000;

-- 시드 공고의 주인. ride/lib/config.js 의 LT_CLIENT 와 같아야 합니다.
SET @lt_username := 'client03';

-- 만들 공고 수 (ride 는 이것의 2배가 생깁니다)
SET @post_count := 2000;

SET @hourly_pay := 10000;

SET @lt_client := (SELECT id FROM users WHERE username = @lt_username);

-- NULL 이면 BaseInitData 가 아직 안 돌았습니다. 아래 INSERT 가 client_id NOT NULL 로 실패합니다.
SELECT @lt_username AS lt_username, @lt_client AS lt_client_id, @post_count AS posts_to_create;

-- ── 번호 테이블 ─────────────────────────────────────────────────────────────
DROP TEMPORARY TABLE IF EXISTS lt_seq;

CREATE TEMPORARY TABLE lt_seq (n INT PRIMARY KEY) AS
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @post_count
)
SELECT n FROM seq;

-- ── 1. 공고 ([LT-RIDE]) ────────────────────────────────────────────────────
INSERT INTO post (client_id, title, content, patient_note, region,
                  hospital_name, hospital_address, hospital_lat, hospital_lng,
                  pickup_address, pickup_lat, pickup_lng,
                  hourly_pay, recruit_start_at, recruit_end_at,
                  escort_start_at, escort_end_at,
                  post_status, report_required, created_at, updated_at, deleted_at)
SELECT @lt_client,
       CONCAT('[LT-RIDE] 부하테스트 공고 ', n),
       '이동수단 부하테스트용 공고입니다. loadtest/ride/sql/teardown.sql 로 지웁니다.',
       NULL,
       '서울',
       'LT병원', '서울시 종로구 1', 37.5665351, 126.9780000,
       '서울시 종로구 2', 37.5700000, 126.9820000,
       @hourly_pay,
       NOW(),
       DATE_ADD(NOW(), INTERVAL 6 DAY),
       DATE_ADD(NOW(), INTERVAL 7 DAY),
       DATE_ADD(DATE_ADD(NOW(), INTERVAL 7 DAY), INTERVAL 2 HOUR),
       'OPEN', b'1', NOW(), NOW(), NULL
FROM lt_seq;

DROP TEMPORARY TABLE IF EXISTS lt_seq;

-- ── 2. 이동수단 ─────────────────────────────────────────────────────────────
-- RideService.createRide 와 같은 모양으로 공고당 2건을 만듭니다.
--   TO_HOSPITAL + TO_HOME, 상태는 ACCEPTED 로 시작.
-- selected / ride_status 는 공고 id 로 섞어서 한쪽 값만 나오지 않게 합니다
-- (RideResponse 가 direction 에 따라 좌표를 뒤집으므로 두 방향이 다 필요합니다).
--
-- 이미 ride 가 달린 공고는 제외합니다 — 시딩을 두 번 돌려도 같은 공고에 ride 가
-- 네 건씩 붙지 않게 하려는 것입니다.
DROP TEMPORARY TABLE IF EXISTS lt_ride_posts;

CREATE TEMPORARY TABLE lt_ride_posts (id BIGINT PRIMARY KEY) AS
SELECT p.id
FROM post p
         LEFT JOIN ride r ON r.post_id = p.id
WHERE p.title LIKE '[LT-RIDE]%'
  AND r.id IS NULL;

SELECT COUNT(*) AS posts_without_ride FROM lt_ride_posts;

INSERT INTO ride (post_id, direction, ride_status, selected, created_at, updated_at)
SELECT id,
       'TO_HOSPITAL',
       ELT(1 + MOD(id, 3), 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED'),
       ELT(1 + MOD(id, 4), 'BUS', 'OWN_CAR', 'TAXI', 'WALK'),
       NOW(), NOW()
FROM lt_ride_posts;

INSERT INTO ride (post_id, direction, ride_status, selected, created_at, updated_at)
SELECT id,
       'TO_HOME',
       ELT(1 + MOD(id + 1, 3), 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED'),
       ELT(1 + MOD(id + 2, 4), 'BUS', 'OWN_CAR', 'TAXI', 'WALK'),
       NOW(), NOW()
FROM lt_ride_posts;

DROP TEMPORARY TABLE IF EXISTS lt_ride_posts;

-- ── 결과 확인 ───────────────────────────────────────────────────────────────
SELECT (SELECT COUNT(*) FROM post WHERE title LIKE '[LT-RIDE]%')                      AS seed_posts,
       (SELECT COUNT(*) FROM ride r JOIN post p ON p.id = r.post_id
         WHERE p.title LIKE '[LT-RIDE]%')                                             AS seed_rides;

SELECT direction, ride_status, COUNT(*) AS cnt
FROM ride r
         JOIN post p ON p.id = r.post_id
WHERE p.title LIKE '[LT-RIDE]%'
GROUP BY direction, ride_status
ORDER BY direction, ride_status;
