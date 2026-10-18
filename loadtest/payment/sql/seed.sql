-- ============================================================================
-- 결제 부하테스트용 데이터 시딩
--
--   docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/payment/sql/seed.sql
--
-- 만드는 것
--   [LT-PAY-R] 공고 + READY 결제   -> 결제 승인(confirm) 부하용. 한 건당 한 번 승인 가능.
--   [LT-PAY-D] 공고 + DONE  결제   -> 결제 취소(cancel) 부하용. 한 건당 한 번 취소 가능.
--
-- 공고 제목의 [LT-PAY-...] 접두어가 유일한 식별자입니다. reset.sql / teardown.sql 도
-- 이 접두어로 대상을 찾으니 바꾸지 마세요.
--
-- ⚠️ 이 스크립트는 멱등하지 않습니다 — 돌릴 때마다 공고+결제 한 묶음이 **더 쌓입니다**
--    (기존 공고에 결제가 중복으로 붙거나 유니크 충돌이 나지는 않습니다. 4·5번 섹션 참고).
--    원하는 건수를 정확히 맞추려면 teardown.sql 을 먼저 돌리세요.
-- ⚠️ ddl-auto: update 인 dev DB 를 그대로 쓰면 시드가 화면에도 보입니다.
--    가능하면 부하테스트용 DB/프로파일을 따로 두세요.
-- ============================================================================

SET SESSION cte_max_recursion_depth = 1000000;

-- ── 설정 ────────────────────────────────────────────────────────────────────
-- 쓰기 부하 대상 의뢰인. loadtest/lib/config.js 의 LT_CLIENT 와 같아야 합니다.
SET @lt_username := 'client03';

-- 승인 부하가 소모할 READY 결제 수.
-- "RATE x DURATION(초)" 보다 넉넉해야 합니다. (예: 50rps x 10분 = 30,000건)
SET @ready_count := 32000;

-- 취소 부하가 소모할 DONE 결제 수. (예: 20rps x 5분 = 6,000건)
SET @done_count := 7000;

SET @hourly_pay := 10000;
SET @hours := 2.0;
-- Post.getTotalPay() = 시급 x 동행시간. PaymentService.verifyAmount 가 문자열로
-- 비교하므로 amount 는 정수로 딱 맞아야 합니다.
SET @amount := @hourly_pay * 2;

SET @lt_client := (SELECT id FROM users WHERE username = @lt_username);

-- NULL 이면 BaseInitData 가 아직 안 돌았습니다. 아래 INSERT 가 client_id NOT NULL 로 실패합니다.
SELECT @lt_username AS lt_username, @lt_client AS lt_client_id;

-- ── 번호 테이블 ─────────────────────────────────────────────────────────────
DROP TEMPORARY TABLE IF EXISTS lt_seq;

CREATE TEMPORARY TABLE lt_seq (n INT PRIMARY KEY) AS
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < GREATEST(@ready_count, @done_count)
)
SELECT n FROM seq;

-- ── 1. 승인 부하용 공고 ([LT-PAY-R]) ───────────────────────────────────────
INSERT INTO post (client_id, title, content, patient_note, region,
                  hospital_name, hospital_address, hospital_lat, hospital_lng,
                  pickup_address, pickup_lat, pickup_lng,
                  hourly_pay, recruit_start_at, recruit_end_at,
                  escort_start_at, escort_end_at,
                  post_status, report_required, created_at, updated_at, deleted_at)
SELECT @lt_client,
       CONCAT('[LT-PAY-R] 부하테스트 공고 ', n),
       '부하테스트용 공고입니다. loadtest/payment/sql/teardown.sql 로 지웁니다.',
       NULL,
       '서울',
       'LT병원', '서울시 종로구 1', 37.5665351, 126.9780000,
       '서울시 종로구 2', 37.5700000, 126.9820000,
       @hourly_pay,
       NOW(),
       DATE_ADD(NOW(), INTERVAL 6 DAY),
       DATE_ADD(NOW(), INTERVAL 7 DAY),
       DATE_ADD(DATE_ADD(NOW(), INTERVAL 7 DAY), INTERVAL 2 HOUR),
       -- 취소 가능 조건이 OPEN/EXPIRED 이므로 OPEN 으로 둡니다.
       'OPEN', b'1', NOW(), NOW(), NULL
FROM lt_seq
WHERE n <= @ready_count;

-- ── 2. 취소 부하용 공고 ([LT-PAY-D]) ───────────────────────────────────────
INSERT INTO post (client_id, title, content, patient_note, region,
                  hospital_name, hospital_address, hospital_lat, hospital_lng,
                  pickup_address, pickup_lat, pickup_lng,
                  hourly_pay, recruit_start_at, recruit_end_at,
                  escort_start_at, escort_end_at,
                  post_status, report_required, created_at, updated_at, deleted_at)
SELECT @lt_client,
       CONCAT('[LT-PAY-D] 부하테스트 공고 ', n),
       '부하테스트용 공고입니다. loadtest/payment/sql/teardown.sql 로 지웁니다.',
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
FROM lt_seq
WHERE n <= @done_count;

-- ── 3. 이미 결제가 달린 공고 목록 ──────────────────────────────────────────
-- 아래 두 INSERT 는 "제목이 [LT-PAY-...] 인 모든 공고"를 대상으로 합니다. 그래서
-- 시딩을 두 번 돌리면 **먼저 만들어 둔 공고에까지 결제가 또 붙습니다**
-- (R 세트는 결제 중복, D 세트는 order_id 유니크 충돌로 중간에 실패).
-- 이미 결제가 있는 공고를 걸러내 그 사고를 막습니다.
-- INSERT 대상 테이블(payment)을 같은 문장의 서브쿼리로 다시 읽을 수 없어 임시 테이블로 우회합니다.
DROP TEMPORARY TABLE IF EXISTS lt_paid_posts;

CREATE TEMPORARY TABLE lt_paid_posts (post_id BIGINT PRIMARY KEY) AS
SELECT DISTINCT pay.post_id
FROM payment pay
WHERE pay.post_id IS NOT NULL;

-- ── 4. READY 결제 ──────────────────────────────────────────────────────────
INSERT INTO payment (amount, hourly_pay_snapshot, hours,
                     order_id, payment_key, method, payment_status,
                     approved_at, canceled_at, cancel_reason,
                     balance_amount, post_id, settlement_id,
                     created_at, updated_at, deleted_at)
SELECT @amount, @hourly_pay, @hours,
       NULL, NULL, NULL, 'READY',
       NULL, NULL, NULL,
       0, p.id, NULL,
       NOW(), NOW(), NULL
FROM post p
         LEFT JOIN lt_paid_posts paid ON paid.post_id = p.id
WHERE p.client_id = @lt_client
  AND p.title LIKE '[LT-PAY-R]%'
  AND paid.post_id IS NULL;

-- ── 5. DONE 결제 ───────────────────────────────────────────────────────────
-- payment_key 가 'INIT-PAYKEY-' 로 시작하면 TossPaymentClient 가 실제 토스 호출을
-- 건너뜁니다(isSeedPaymentKey). 취소 부하가 외부 API 없이 돌 수 있는 이유입니다.
-- 값을 post.id 로 만들어 두면 reset.sql 이 같은 값을 다시 계산해 복구할 수 있습니다.
INSERT INTO payment (amount, hourly_pay_snapshot, hours,
                     order_id, payment_key, method, payment_status,
                     approved_at, canceled_at, cancel_reason,
                     balance_amount, post_id, settlement_id,
                     created_at, updated_at, deleted_at)
SELECT @amount, @hourly_pay, @hours,
       CONCAT('LT-ORDER-D-', p.id), CONCAT('INIT-PAYKEY-LT-D-', p.id), '카드', 'DONE',
       NOW(), NULL, NULL,
       @amount, p.id, NULL,
       NOW(), NOW(), NULL
FROM post p
         LEFT JOIN lt_paid_posts paid ON paid.post_id = p.id
WHERE p.client_id = @lt_client
  AND p.title LIKE '[LT-PAY-D]%'
  AND paid.post_id IS NULL;

DROP TEMPORARY TABLE IF EXISTS lt_seq;
DROP TEMPORARY TABLE IF EXISTS lt_paid_posts;

-- ── 결과 확인 ───────────────────────────────────────────────────────────────
SELECT 'READY (승인 부하용)' AS kind,
       COUNT(*) AS cnt, MIN(pay.id) AS min_payment_id, MAX(pay.id) AS max_payment_id
FROM payment pay
         JOIN post p ON p.id = pay.post_id
WHERE p.title LIKE '[LT-PAY-R]%'
UNION ALL
SELECT 'DONE (취소 부하용)',
       COUNT(*), MIN(pay.id), MAX(pay.id)
FROM payment pay
         JOIN post p ON p.id = pay.post_id
WHERE p.title LIKE '[LT-PAY-D]%';
