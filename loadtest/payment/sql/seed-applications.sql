-- ============================================================================
-- 취소 비용이 지원자 수에 비례하는지 재기 위한 추가 시딩 (선택)
--
--   docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/payment/sql/seed-applications.sql
--
-- 왜 필요한가
--   DELETE /api/v1/payments/{id} 는 마지막에 그 공고의 PENDING 지원을 전부
--   REJECTED 로 바꿉니다 (PaymentPersistenceService.rejectApplication —
--   목록을 가져와 하나씩 더티 체킹). 즉 **지원자 1명당 UPDATE 1개**가 늘어납니다.
--   지원자 0명인 공고로 취소 부하를 재면 이 비용이 아예 안 잡힙니다.
--
-- 쓰는 법
--   @applicants 를 0 -> 50 -> 200 으로 올려가며 cancel.js 를 같은 RATE/DURATION 으로
--   세 번 돌리고, p95 가 선형으로 늘어나는지 봅니다.
--
-- ⚠️ 행 수 = @applicants x [LT-PAY-D] 공고 수 입니다. @applicants=200 에 D 공고가
--    7,000개면 지원 140만 건이 됩니다. 볼륨 테스트를 할 때는 seed.sql 의
--    @done_count 를 먼저 줄이세요 (예: 1,000).
-- ⚠️ 멱등하지 않습니다. 다시 돌리려면 teardown.sql 부터.
-- ============================================================================

SET SESSION cte_max_recursion_depth = 1000000;

-- 공고 한 건당 심을 PENDING 지원자 수
SET @applicants := 50;

-- 비밀번호 해시는 기존 escort01 에서 복사합니다 (평문 'password1!').
-- INSERT 대상 테이블을 서브쿼리에서 다시 읽지 않도록 미리 변수에 담습니다.
SET @escort_pw := (SELECT password FROM users WHERE username = 'escort01');

SELECT @applicants AS applicants_per_post,
       (SELECT COUNT(*) FROM post WHERE title LIKE '[LT-PAY-D]%') AS d_posts,
       @applicants * (SELECT COUNT(*) FROM post WHERE title LIKE '[LT-PAY-D]%') AS total_applications;

-- ── 번호 테이블 ─────────────────────────────────────────────────────────────
DROP TEMPORARY TABLE IF EXISTS lt_seq;

CREATE TEMPORARY TABLE lt_seq (n INT PRIMARY KEY) AS
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @applicants
)
SELECT n FROM seq;

-- ── 1. 지원자용 동행 매니저 계정 ───────────────────────────────────────────
-- escort_profiles 는 만들지 않습니다. rejectApplication 은 application 만 건드립니다.
INSERT INTO users (username, password, email, name, role, gender, birth_date,
                   phone_num, region, user_status, version, created_at, updated_at, deleted_at)
SELECT CONCAT('lt-escort-', n),
       @escort_pw,
       CONCAT('lt-escort-', n, '@example.com'),
       CONCAT('부하동행', n),
       'ESCORT', 'MALE', '1990-06-15',
       CONCAT('010-7', LPAD(n, 8, '0')),
       '서울', 'ACTIVE', 0, NOW(), NOW(), NULL
FROM lt_seq;

DROP TEMPORARY TABLE IF EXISTS lt_seq;

-- ── 2. [LT-PAY-D] 공고마다 PENDING 지원 심기 ───────────────────────────────
-- unique (active_post_id, escort_id) 제약이 있어 (공고, 동행인) 조합은 유일해야 합니다.
-- 아래 CROSS JOIN 이 정확히 그 조합을 만듭니다.
-- active_post_id 는 PENDING/ACCEPTED 일 때 post.id 여야 합니다 (Application.syncActivePostId).
INSERT INTO application (post_id, escort_id, status, accepted_post_id, active_post_id,
                         lat, lng, accuracy, location_updated_at, created_at, updated_at)
SELECT p.id, e.id, 'PENDING', NULL, p.id,
       NULL, NULL, NULL, NULL, NOW(), NOW()
FROM post p
         JOIN users e ON e.username LIKE 'lt-escort-%'
WHERE p.title LIKE '[LT-PAY-D]%';

-- ── 결과 확인 ───────────────────────────────────────────────────────────────
SELECT COUNT(*) AS applications,
       COUNT(DISTINCT a.post_id) AS posts,
       COUNT(*) / COUNT(DISTINCT a.post_id) AS per_post
FROM application a
         JOIN post p ON p.id = a.post_id
WHERE p.title LIKE '[LT-PAY-D]%';
