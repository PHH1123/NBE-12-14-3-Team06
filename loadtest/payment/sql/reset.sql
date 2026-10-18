-- ============================================================================
-- 부하테스트가 소모한 결제 행을 원래 상태로 되돌립니다.
--
--   docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/payment/sql/reset.sql
--
-- 승인·취소 스크립트는 결제 상태를 소모합니다(READY -> DONE, DONE -> CANCELED).
-- 되돌리지 않고 다시 돌리면 쓸 행이 없어 전부 400 이 되거나, order_id / payment_key
-- 유니크 충돌이 납니다.
--
-- teardown + seed 를 다시 하는 것보다 훨씬 빠르므로, 반복 측정은 이 스크립트를 쓰세요.
-- 되돌리는 것: 결제 상태·금액·키, 공고 상태, 테스트가 만든 알림, 거절된 지원.
-- ============================================================================

SET @lt_username := 'client03';
SET @hourly_pay := 10000;
SET @hours := 2.0;
SET @amount := @hourly_pay * 2;
SET @lt_client := (SELECT id FROM users WHERE username = @lt_username);

-- ── 1. 취소가 만든 "재결제용" 결제 행 제거 ─────────────────────────────────
-- PaymentPersistenceService.paymentCancelDb(createNewPayment=true) 가 취소할 때마다
-- 같은 공고에 READY 결제를 하나 더 만듭니다. 공고마다 가장 작은 id(= 시드가 만든
-- 원본)만 남기고 나머지를 지웁니다.
DROP TEMPORARY TABLE IF EXISTS lt_keep;

CREATE TEMPORARY TABLE lt_keep (post_id BIGINT PRIMARY KEY, keep_id BIGINT) AS
SELECT pay.post_id, MIN(pay.id) AS keep_id
FROM payment pay
         JOIN post p ON p.id = pay.post_id
WHERE p.title LIKE '[LT-PAY-%'
GROUP BY pay.post_id;

DELETE pay
FROM payment pay
         JOIN lt_keep k ON k.post_id = pay.post_id
WHERE pay.id <> k.keep_id;

DROP TEMPORARY TABLE IF EXISTS lt_keep;

-- ── 2. 승인 부하용 결제를 READY 로 복구 ────────────────────────────────────
UPDATE payment pay
    JOIN post p ON p.id = pay.post_id
SET pay.payment_status = 'READY',
    pay.order_id       = NULL,
    pay.payment_key    = NULL,
    pay.method         = NULL,
    pay.approved_at    = NULL,
    pay.canceled_at    = NULL,
    pay.cancel_reason  = NULL,
    pay.amount         = @amount,
    pay.hours          = @hours,
    pay.balance_amount = 0,
    pay.deleted_at     = NULL,
    pay.updated_at     = NOW()
WHERE p.title LIKE '[LT-PAY-R]%';

-- ── 3. 취소 부하용 결제를 DONE 으로 복구 ───────────────────────────────────
-- 키를 post.id 에서 다시 계산하므로 seed.sql 과 같은 값이 됩니다.
UPDATE payment pay
    JOIN post p ON p.id = pay.post_id
SET pay.payment_status = 'DONE',
    pay.order_id       = CONCAT('LT-ORDER-D-', p.id),
    pay.payment_key    = CONCAT('INIT-PAYKEY-LT-D-', p.id),
    pay.method         = '카드',
    pay.approved_at    = NOW(),
    pay.canceled_at    = NULL,
    pay.cancel_reason  = NULL,
    pay.amount         = @amount,
    pay.hours          = @hours,
    pay.balance_amount = @amount,
    pay.deleted_at     = NULL,
    pay.updated_at     = NOW()
WHERE p.title LIKE '[LT-PAY-D]%';

-- ── 4. 공고 상태 복구 ──────────────────────────────────────────────────────
UPDATE post
SET post_status = 'OPEN',
    deleted_at  = NULL,
    updated_at  = NOW()
WHERE title LIKE '[LT-PAY-%';

-- ── 5. 거절된 지원 되돌리기 ────────────────────────────────────────────────
-- 결제 취소는 그 공고의 PENDING 지원을 전부 REJECTED 로 바꿉니다
-- (PaymentPersistenceService.rejectApplication). active_post_id 는 상태와 짝이
-- 맞아야 하므로(Application.syncActivePostId) 같이 되돌립니다.
UPDATE application a
    JOIN post p ON p.id = a.post_id
SET a.status         = 'PENDING',
    a.active_post_id = p.id,
    a.updated_at     = NOW()
WHERE p.title LIKE '[LT-PAY-%'
  AND a.status = 'REJECTED';

-- ── 6. 테스트가 만든 알림 제거 ─────────────────────────────────────────────
-- 결제 알림 URL 은 '/client/posts/{postId}' 입니다 (NotificationUrl).
--
-- ⚠️ `ON n.url = CONCAT('/client/posts/', p.id)` 로 조인하면 안 됩니다. 조인 조건이
--    계산식이라 어느 쪽도 인덱스를 못 쓰고 알림 x 공고 전체 조합을 훑습니다
--    (알림 3만 x 공고 5만 = 15억 비교, 실측 12분 이상).
--    URL 에서 공고 id 를 뽑아 post 의 PK 로 조인하면 알림 건수만큼의 인덱스 조회로 끝납니다.
DELETE n
FROM notification n
         JOIN post p ON p.id = CAST(SUBSTRING_INDEX(n.url, '/', -1) AS UNSIGNED)
WHERE n.url LIKE '/client/posts/%'
  AND p.title LIKE '[LT-PAY-%';

-- ── 결과 확인 ───────────────────────────────────────────────────────────────
SELECT CONCAT('[LT-PAY-', SUBSTRING(p.title, 9, 1), ']') AS seed_set,
       pay.payment_status,
       COUNT(*)                                          AS cnt
FROM payment pay
         JOIN post p ON p.id = pay.post_id
WHERE p.title LIKE '[LT-PAY-%'
GROUP BY seed_set, pay.payment_status
ORDER BY seed_set, pay.payment_status;
