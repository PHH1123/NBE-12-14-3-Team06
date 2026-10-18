-- ============================================================================
-- 부하테스트 시드를 전부 삭제합니다.
--
--   docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/payment/sql/teardown.sql
--
-- 제목이 [LT-PAY-...] 로 시작하는 공고와, 그 공고에 딸린 결제·지원·이동수단·알림을
-- 하드 삭제합니다. post 에 FK 를 가진 테이블은 application / payment / ride 뿐이라
-- 이 순서로 지우면 제약 위반이 나지 않습니다.
--
-- 다시 시딩하려면 teardown -> seed 순서로 돌리세요.
-- (상태만 되돌리고 시드를 유지하려면 reset.sql 을 쓰세요 — 훨씬 빠릅니다.)
-- ============================================================================

DROP TEMPORARY TABLE IF EXISTS lt_posts;

CREATE TEMPORARY TABLE lt_posts (id BIGINT PRIMARY KEY) AS
SELECT id
FROM post
WHERE title LIKE '[LT-PAY-%';

SELECT COUNT(*) AS posts_to_delete FROM lt_posts;

-- 결제 알림 URL 은 '/client/posts/{postId}' 입니다 (NotificationUrl).
-- CONCAT 으로 조인하면 인덱스를 못 써서 알림 x 공고 전체를 훑습니다 (reset.sql 6번 주석 참고).
-- URL 에서 공고 id 를 뽑아 PK 로 조인합니다.
DELETE n
FROM notification n
         JOIN lt_posts p ON p.id = CAST(SUBSTRING_INDEX(n.url, '/', -1) AS UNSIGNED)
WHERE n.url LIKE '/client/posts/%';

DELETE a FROM application a JOIN lt_posts p ON p.id = a.post_id;
DELETE pay FROM payment pay JOIN lt_posts p ON p.id = pay.post_id;
DELETE r FROM ride r JOIN lt_posts p ON p.id = r.post_id;
DELETE po FROM post po JOIN lt_posts p ON p.id = po.id;

DROP TEMPORARY TABLE IF EXISTS lt_posts;

-- 부하테스트로 생긴 escort 계정(지원자 볼륨용)까지 지웁니다.
DELETE ush
FROM user_status_history ush
         JOIN users u ON u.id = ush.user_id
WHERE u.username LIKE 'lt-escort-%';

DELETE ep
FROM escort_profiles ep
         JOIN users u ON u.id = ep.user_id
WHERE u.username LIKE 'lt-escort-%';

DELETE FROM users WHERE username LIKE 'lt-escort-%';

-- ── 결과 확인 ───────────────────────────────────────────────────────────────
SELECT (SELECT COUNT(*) FROM post)                                          AS posts,
       (SELECT COUNT(*) FROM payment)                                       AS payments,
       (SELECT COUNT(*) FROM application)                                   AS applications,
       (SELECT COUNT(*) FROM users)                                         AS users,
       (SELECT COUNT(*) FROM post WHERE title LIKE '[LT-PAY-%')             AS leftover_seed_posts;
