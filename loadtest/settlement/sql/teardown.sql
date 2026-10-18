-- ============================================================================
-- 정산 부하테스트 시드 전부 삭제
--
--   docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/settlement/sql/teardown.sql
--
-- 삭제 범위는 제목이 '[LT-SETL-' 로 시작하는 공고와 거기 달린 행뿐입니다.
-- BaseInitData 데이터와 다른 도메인의 시드([LT-PAY-...], [LT-RIDE])는 건드리지 않습니다.
-- ============================================================================

DROP TEMPORARY TABLE IF EXISTS lt_posts;

CREATE TEMPORARY TABLE lt_posts (id BIGINT PRIMARY KEY) AS
SELECT id
FROM post
WHERE title LIKE '[LT-SETL-%';

DROP TEMPORARY TABLE IF EXISTS lt_apps;

CREATE TEMPORARY TABLE lt_apps (id BIGINT PRIMARY KEY) AS
SELECT a.id
FROM application a
         JOIN lt_posts p ON p.id = a.post_id;

SELECT (SELECT COUNT(*) FROM lt_posts) AS posts_to_delete,
       (SELECT COUNT(*) FROM lt_apps)  AS applications_to_delete;

-- 정산 완료 알림 URL 은 '/escort/{applicationId}' 입니다 (NotificationUrl.U_SETTLEMENT_DONE).
DELETE n
FROM notification n
         JOIN lt_apps a ON a.id = CAST(SUBSTRING_INDEX(n.url, '/', -1) AS UNSIGNED)
WHERE n.url LIKE '/escort/%';

-- 패널티가 정산을 참조할 수 있으므로 정산보다 먼저 끊습니다.
DELETE np
FROM no_show_penalty np
         JOIN settlement s ON s.id = np.settlement_id
         JOIN lt_apps a ON a.id = s.application_id;

DELETE s FROM settlement s JOIN lt_apps a ON a.id = s.application_id;
DELETE a FROM application a JOIN lt_apps t ON t.id = a.id;

-- 공고에 달린 나머지(이 세트는 만들지 않지만 혹시 모를 잔여분)
DELETE r FROM ride r JOIN lt_posts p ON p.id = r.post_id;
DELETE pay FROM payment pay JOIN lt_posts p ON p.id = pay.post_id;

DELETE n
FROM notification n
         JOIN lt_posts p ON p.id = CAST(SUBSTRING_INDEX(n.url, '/', -1) AS UNSIGNED)
WHERE n.url LIKE '/client/posts/%';

DELETE po FROM post po JOIN lt_posts p ON p.id = po.id;

DROP TEMPORARY TABLE IF EXISTS lt_apps;
DROP TEMPORARY TABLE IF EXISTS lt_posts;

-- ── 결과 확인 ───────────────────────────────────────────────────────────────
SELECT (SELECT COUNT(*) FROM post)                                      AS posts,
       (SELECT COUNT(*) FROM application)                               AS applications,
       (SELECT COUNT(*) FROM settlement)                                AS settlements,
       (SELECT COUNT(*) FROM post WHERE title LIKE '[LT-SETL-%')        AS leftover_seed_posts;
