-- ============================================================================
-- 소모한 정산 상태를 시딩 직후로 되돌립니다 (반복 측정용, teardown+seed 보다 훨씬 빠름)
--
--   docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/settlement/sql/reset.sql
--
-- 되돌리는 것
--   · [LT-SETL-P] 세트의 정산 -> PENDING   (요청 부하가 PROCESSING/COMPLETED/FAILED 로 바꿔 둔 것)
--   · [LT-SETL-C] 세트의 정산 -> COMPLETED
--   · 테스트가 만든 정산 완료 알림 삭제
--
-- 공고·지원·정산 행 자체는 그대로 둡니다. 정산 요청은 행을 만들지도 지우지도 않고
-- 상태만 바꾸기 때문입니다 (SettlementRepository.updateStatus / updateProcessing).
-- ============================================================================

DROP TEMPORARY TABLE IF EXISTS lt_setl;

CREATE TEMPORARY TABLE lt_setl (
    settlement_id BIGINT PRIMARY KEY,
    app_id        BIGINT,
    seed_set      VARCHAR(16),
    KEY (app_id)
) AS
SELECT s.id                                                                  AS settlement_id,
       a.id                                                                  AS app_id,
       CASE WHEN p.title LIKE '[LT-SETL-P]%' THEN 'PENDING' ELSE 'COMPLETED' END AS seed_set
FROM settlement s
         JOIN application a ON a.id = s.application_id
         JOIN post p ON p.id = a.post_id
WHERE p.title LIKE '[LT-SETL-%';

SELECT seed_set, COUNT(*) AS rows_in_scope FROM lt_setl GROUP BY seed_set;

-- ── 1. 상태 복구 ────────────────────────────────────────────────────────────
UPDATE settlement s
    JOIN lt_setl t ON t.settlement_id = s.id
SET s.settlement_status = t.seed_set,
    s.updated_at        = NOW()
WHERE s.settlement_status <> t.seed_set;

SELECT ROW_COUNT() AS settlements_restored;

-- ── 2. 테스트가 만든 알림 삭제 ──────────────────────────────────────────────
-- 정산 완료 알림 URL 은 '/escort/{applicationId}' 입니다 (NotificationUrl.U_SETTLEMENT_DONE).
-- CONCAT 으로 조인하면 인덱스를 못 써서 알림 x 지원 전체를 훑습니다.
-- URL 에서 지원 id 를 뽑아 PK 로 조인합니다.
DELETE n
FROM notification n
         JOIN lt_setl t ON t.app_id = CAST(SUBSTRING_INDEX(n.url, '/', -1) AS UNSIGNED)
WHERE n.url LIKE '/escort/%';

SELECT ROW_COUNT() AS notifications_deleted;

DROP TEMPORARY TABLE IF EXISTS lt_setl;

-- ── 결과 확인 ───────────────────────────────────────────────────────────────
SELECT s.settlement_status, COUNT(*) AS cnt
FROM settlement s
         JOIN application a ON a.id = s.application_id
         JOIN post p ON p.id = a.post_id
WHERE p.title LIKE '[LT-SETL-%'
GROUP BY s.settlement_status
ORDER BY s.settlement_status;
