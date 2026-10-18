-- ============================================================================
-- 정산(settlement) 부하테스트용 데이터 시딩
--
--   docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/settlement/sql/seed.sql
--
-- 만드는 것 (공고 1 : 지원 1 : 정산 1)
--   [LT-SETL-P] 공고 + ACCEPTED 지원 + PENDING   정산  -> 정산 요청(request) 부하용. 한 건당 한 번.
--   [LT-SETL-C] 공고 + ACCEPTED 지원 + COMPLETED 정산  -> 조회 부하용 (상태 다양성)
--
-- 왜 셋을 같이 만드나
--   settlement.application_id 가 UNIQUE 라 정산 1건당 지원 1건이 필요하고,
--   목록 API 가 p.escortStartAt 으로 필터·정렬하므로 공고까지 있어야 합니다.
--   (SettlementRepository.findAllByUserIdAndDate)
--
-- 정산 주인은 escort01 입니다
--   AccountDto 조회가 EscortProfile 을 조인하므로(ep.userId=e.id) 계좌 정보가 있는
--   계정이어야 합니다. BaseInitData 의 escort01~03 에는 escort_profiles 가 있습니다.
--
-- ⚠️ 멱등하지 않습니다 — 돌릴 때마다 한 묶음이 더 쌓입니다.
--    원하는 건수를 정확히 맞추려면 teardown.sql 을 먼저 돌리세요.
-- ⚠️ 정산 요청은 상태를 소모합니다(PENDING -> COMPLETED). 매 실행 후 reset.sql.
-- ============================================================================

SET SESSION cte_max_recursion_depth = 1000000;

-- 정산을 받을 동행 매니저. settlement/lib/config.js 의 LT_ESCORT 와 같아야 합니다.
SET @lt_escort_name := 'escort01';
-- 공고 주인(의뢰인). 정산 로직과는 무관하지만 post.client_id 가 NOT NULL 입니다.
SET @lt_client_name := 'client03';

-- 정산 요청 부하용 (한 건당 한 번만 요청 가능)
SET @pending_count := 8000;
-- 조회 부하용
SET @completed_count := 4000;

SET @hourly_pay := 10000;
SET @payout := 18000;      -- 실지급액
SET @platform_fee := 2000; -- 플랫폼 수수료

SET @lt_escort := (SELECT id FROM users WHERE username = @lt_escort_name);
SET @lt_client := (SELECT id FROM users WHERE username = @lt_client_name);

-- NULL 이면 BaseInitData 가 아직 안 돌았습니다. 아래 INSERT 가 NOT NULL 로 실패합니다.
SELECT @lt_escort_name AS escort, @lt_escort AS escort_id,
       @lt_client_name AS client, @lt_client AS client_id,
       (SELECT COUNT(*) FROM escort_profiles WHERE user_id = @lt_escort) AS escort_profile_rows;

-- ── 번호 테이블 ─────────────────────────────────────────────────────────────
DROP TEMPORARY TABLE IF EXISTS lt_seq;

CREATE TEMPORARY TABLE lt_seq (n INT PRIMARY KEY) AS
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < GREATEST(@pending_count, @completed_count)
)
SELECT n FROM seq;

-- ── 1. 공고 ─────────────────────────────────────────────────────────────────
-- escort_start_at 은 "지난 한 달" 안에 둡니다. 목록 API 의 기본 조회 구간이
-- now-1month ~ now 이기 때문입니다 (SettlementController.settlementList).
INSERT INTO post (client_id, title, content, patient_note, region,
                  hospital_name, hospital_address, hospital_lat, hospital_lng,
                  pickup_address, pickup_lat, pickup_lng,
                  hourly_pay, recruit_start_at, recruit_end_at,
                  escort_start_at, escort_end_at,
                  post_status, report_required, created_at, updated_at, deleted_at)
SELECT @lt_client,
       CONCAT('[LT-SETL-P] 정산 부하테스트 공고 ', n),
       '정산 부하테스트용 공고입니다. loadtest/settlement/sql/teardown.sql 로 지웁니다.',
       NULL, '서울',
       'LT병원', '서울시 종로구 1', 37.5665351, 126.9780000,
       '서울시 종로구 2', 37.5700000, 126.9820000,
       @hourly_pay,
       DATE_SUB(NOW(), INTERVAL 40 DAY),
       DATE_SUB(NOW(), INTERVAL 30 DAY),
       DATE_SUB(NOW(), INTERVAL (1 + MOD(n, 25)) DAY),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL (1 + MOD(n, 25)) DAY), INTERVAL 2 HOUR),
       'COMPLETED', b'1', NOW(), NOW(), NULL
FROM lt_seq
WHERE n <= @pending_count;

INSERT INTO post (client_id, title, content, patient_note, region,
                  hospital_name, hospital_address, hospital_lat, hospital_lng,
                  pickup_address, pickup_lat, pickup_lng,
                  hourly_pay, recruit_start_at, recruit_end_at,
                  escort_start_at, escort_end_at,
                  post_status, report_required, created_at, updated_at, deleted_at)
SELECT @lt_client,
       CONCAT('[LT-SETL-C] 정산 부하테스트 공고 ', n),
       '정산 부하테스트용 공고입니다. loadtest/settlement/sql/teardown.sql 로 지웁니다.',
       NULL, '서울',
       'LT병원', '서울시 종로구 1', 37.5665351, 126.9780000,
       '서울시 종로구 2', 37.5700000, 126.9820000,
       @hourly_pay,
       DATE_SUB(NOW(), INTERVAL 40 DAY),
       DATE_SUB(NOW(), INTERVAL 30 DAY),
       DATE_SUB(NOW(), INTERVAL (1 + MOD(n, 25)) DAY),
       DATE_ADD(DATE_SUB(NOW(), INTERVAL (1 + MOD(n, 25)) DAY), INTERVAL 2 HOUR),
       'COMPLETED', b'1', NOW(), NOW(), NULL
FROM lt_seq
WHERE n <= @completed_count;

DROP TEMPORARY TABLE IF EXISTS lt_seq;

-- ── 2. 지원 ─────────────────────────────────────────────────────────────────
-- 아직 지원이 안 달린 시드 공고에만 붙입니다 (시딩을 두 번 돌려도 중복되지 않게).
--   · accepted_post_id 는 UNIQUE 라 공고 id 를 그대로 넣습니다.
--   · active_post_id 는 UNIQUE(active_post_id, escort_id) 라 NULL 로 둡니다
--     (MySQL 은 NULL 중복을 허용합니다). 정산 로직은 이 값을 보지 않습니다.
DROP TEMPORARY TABLE IF EXISTS lt_posts_no_app;

CREATE TEMPORARY TABLE lt_posts_no_app (id BIGINT PRIMARY KEY) AS
SELECT p.id
FROM post p
         LEFT JOIN application a ON a.post_id = p.id
WHERE p.title LIKE '[LT-SETL-%'
  AND a.id IS NULL;

SELECT COUNT(*) AS posts_without_application FROM lt_posts_no_app;

INSERT INTO application (post_id, escort_id, status, accepted_post_id, active_post_id,
                         created_at, updated_at)
SELECT id, @lt_escort, 'ACCEPTED', id, NULL, NOW(), NOW()
FROM lt_posts_no_app;

DROP TEMPORARY TABLE IF EXISTS lt_posts_no_app;

-- ── 3. 정산 ─────────────────────────────────────────────────────────────────
-- 아직 정산이 안 달린 시드 지원에만 붙입니다 (settlement.application_id 가 UNIQUE).
DROP TEMPORARY TABLE IF EXISTS lt_apps_no_settlement;

CREATE TEMPORARY TABLE lt_apps_no_settlement (app_id BIGINT PRIMARY KEY, seed_set VARCHAR(16)) AS
SELECT a.id                                                                  AS app_id,
       CASE WHEN p.title LIKE '[LT-SETL-P]%' THEN 'PENDING' ELSE 'COMPLETED' END AS seed_set
FROM application a
         JOIN post p ON p.id = a.post_id
         LEFT JOIN settlement s ON s.application_id = a.id
WHERE p.title LIKE '[LT-SETL-%'
  AND s.id IS NULL;

SELECT seed_set, COUNT(*) AS apps_without_settlement
FROM lt_apps_no_settlement GROUP BY seed_set;

INSERT INTO settlement (application_id, escort_id, payout_amount, platform_fee, penalty_amount,
                        settlement_status, settled_date, created_at, updated_at, deleted_at)
SELECT app_id, @lt_escort, @payout, @platform_fee, 0,
       seed_set,          -- 'PENDING' 또는 'COMPLETED'
       CURDATE(), NOW(), NOW(), NULL
FROM lt_apps_no_settlement;

DROP TEMPORARY TABLE IF EXISTS lt_apps_no_settlement;

-- ── 결과 확인 ───────────────────────────────────────────────────────────────
SELECT s.settlement_status, COUNT(*) AS cnt,
       MIN(s.id) AS min_id, MAX(s.id) AS max_id
FROM settlement s
         JOIN application a ON a.id = s.application_id
         JOIN post p ON p.id = a.post_id
WHERE p.title LIKE '[LT-SETL-%'
GROUP BY s.settlement_status
ORDER BY s.settlement_status;
