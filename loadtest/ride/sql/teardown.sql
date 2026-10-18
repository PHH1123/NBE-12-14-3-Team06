-- ============================================================================
-- 이동수단 부하테스트 시드 전부 삭제
--
--   docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/ride/sql/teardown.sql
--
-- 삭제 범위는 제목이 '[LT-RIDE]' 로 시작하는 공고와 거기 달린 행뿐입니다.
-- BaseInitData 데이터와 다른 도메인의 시드([LT-PAY-...])는 건드리지 않습니다.
-- ============================================================================

DROP TEMPORARY TABLE IF EXISTS lt_posts;

CREATE TEMPORARY TABLE lt_posts (id BIGINT PRIMARY KEY) AS
SELECT id
FROM post
WHERE title LIKE '[LT-RIDE]%';

SELECT COUNT(*) AS posts_to_delete FROM lt_posts;

-- 공고에 매달린 행부터. ride 가 본체이고 나머지는 혹시 모를 잔여분입니다
-- (이 세트는 읽기 전용이라 보통 0건입니다).
DELETE r FROM ride r JOIN lt_posts p ON p.id = r.post_id;
DELETE a FROM application a JOIN lt_posts p ON p.id = a.post_id;
DELETE pay FROM payment pay JOIN lt_posts p ON p.id = pay.post_id;

-- 결제 알림 URL 은 '/client/posts/{postId}' 입니다 (NotificationUrl).
-- CONCAT 으로 조인하면 인덱스를 못 써서 알림 x 공고 전체를 훑습니다.
-- URL 에서 공고 id 를 뽑아 PK 로 조인합니다.
DELETE n
FROM notification n
         JOIN lt_posts p ON p.id = CAST(SUBSTRING_INDEX(n.url, '/', -1) AS UNSIGNED)
WHERE n.url LIKE '/client/posts/%';

DELETE po FROM post po JOIN lt_posts p ON p.id = po.id;

DROP TEMPORARY TABLE IF EXISTS lt_posts;

-- ── 결과 확인 ───────────────────────────────────────────────────────────────
SELECT (SELECT COUNT(*) FROM post)                                      AS posts,
       (SELECT COUNT(*) FROM ride)                                      AS rides,
       (SELECT COUNT(*) FROM post WHERE title LIKE '[LT-RIDE]%')        AS leftover_seed_posts;
