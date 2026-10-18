# 결제 도메인 부하테스트 보고서

측정일 2026-10-18 · 대상 `/api/v1/payments` 전 경로 · 도구 k6 v2.3.0

---

## 1. 요약

| 항목 | 결과 |
|---|---|
| 읽기 처리량 | **약 1,100 req/s** 에서 한계 (에러 0%, p99 609ms) |
| 읽기 병목 | **Hikari 커넥션 풀(크기 10)** — 획득 대기 254ms → **4,083ms** |
| 결제 승인 | 300 req/s 까지 여유 (p95 20.7ms, 에러 0%) |
| 결제 취소 | 20 req/s 에서 p95 15ms. **지원자 1명당 +0.5ms 선형 증가** |
| 중복 결제 방어 | 동시 20건 × 50라운드 **완벽** (중복 승인 0건) |
| 치명적 결함 | 없음 |

가장 먼저 손댈 곳은 **커넥션 풀 크기(기본값 10)** 와 **승인·취소 1건당 커넥션 4회 획득** 구조입니다.

---

## 2. 측정 환경

| 항목 | 값 |
|---|---|
| 애플리케이션 | `java -jar` (bootJar), 포트 8081, profile `dev` |
| JVM | Java 26, JIT 기본(C2 포함) |
| 로깅 | `org.hibernate.SQL=WARN`, `jdbc.bind=WARN`, `format_sql/highlight_sql/use_sql_comments=false` |
| DB | MySQL 8.0 (Docker, 127.0.0.1:3307) |
| Hikari 풀 | **max 10** (설정에 없어 기본값) |
| 부하 생성 | k6 v2.3.0, **동일 머신** (macOS / Apple Silicon) |
| 시드 | `client03` 에 공고:결제 1:1 — READY 40,000건 / DONE 10,000건 |
| 워밍업 | 측정 전 읽기 스모크 30초 선행 (JIT 예열) |
| 스케줄러 | `PaymentScheduler` 시간당 1회(매시 30분) — 끄지 않음. 처리 대상(DELETED 결제)이 없어 영향 미미 |

### 측정하지 않은 환경

처음 떠 있던 IntelliJ 실행 인스턴스(8080)는 **성능 측정에 쓸 수 없어 제외**했습니다.

- `-XX:TieredStopAtLevel=1` — C2 JIT 비활성(빠른 시작 옵션). 지속 부하에서 수 배 느립니다
- `logback-spring.xml` 의 dev 설정 `org.hibernate.SQL=DEBUG` + `org.hibernate.orm.jdbc.bind=TRACE` — 쿼리 전건과 바인딩 파라미터 전건을 로깅합니다

`application.yaml` 의 `format_sql`/`highlight_sql`/`use_sql_comments` 는 측정 전에 이미
주석 처리된 상태였습니다. 다만 SQL 로깅 자체는 `logback-spring.xml` 에 별도로 켜져 있어,
측정용 인스턴스에서는 `--logging.level.org.hibernate.SQL=WARN` 으로 꺼야 했습니다.

같은 MySQL 을 쓰는 별도 인스턴스를 8081 에 띄워 측정했고, 측정 중 8080 은 종료 상태였으므로 **스케줄러 중복 간섭은 없었습니다.**

---

## 3. 결과

### 3-1. 읽기 — 평균 부하 (100 iter/s = 300 req/s, 2분, `client01`)

| 엔드포인트 | p95 | p99 | 실패율 |
|---|---|---|---|
| `GET /payments` (목록) | 2.01ms | — | 0% |
| `GET /payments/{id}` (상세) | 4.44ms | 12.63ms | 0% |
| `GET /payments/posts/{postId}` | 1.67ms | — | 0% |

총 36,055 req / 300.2 req/s / 체크 36,003건 전부 통과. 임계값 전원 통과.

> **상세(4.44ms)가 목록(2.01ms)보다 느립니다.** `client01` 의 목록이 5건(1.2KB)뿐이라 작고,
> 상세는 fetch join + 소유자 검증을 타기 때문입니다. 결제가 많은 계정에서는 역전됩니다.

### 3-2. 읽기 — 한계점 탐색 (50 → 1,200 iter/s 단계 증가, 각 30초)

| 지표 | 값 |
|---|---|
| 달성 처리량 | **1,122 req/s** (총 168,448 req) |
| 실패율 | **0%** |
| p95 / p99 / max | 33.7ms / **608.8ms** / **6.74s** |
| dropped_iterations | 7,866 (maxVUs 800 소진 — 목표 1,200 iter/s 미달) |
| Hikari 획득 대기 (max) | 254ms → **4,083ms** |
| Hikari 획득 대기 (avg) | 0.27ms → **8.54ms** |
| Hikari timeout | 0건 |
| 커넥션 획득 / 요청 | 1.005회 |

**해석.** 에러는 한 건도 없지만 p99 가 609ms, 최악 6.74초까지 늘어납니다. 원인은 명확합니다 —
**커넥션 풀(10)이 포화되어 요청이 최대 4초간 커넥션을 기다립니다.** Hikari 기본 타임아웃이
30초라 timeout 으로 집계되지 않고 전부 "느린 성공"으로 나타난 것이고, 이게 p99 꼬리의 정체입니다.

dropped_iterations 는 k6 가 maxVUs 800 을 다 쓰고도 목표 도착률을 못 맞춘 것으로,
k6 와 서버가 같은 머신에 있어 **1,122 req/s 는 서버 상한이 아니라 이 측정 구성의 상한**입니다.

### 3-3. 결제 승인 — 동시성 정합성 ⭐

같은 `paymentId` 에 **동시 20건**을 `http.batch` 로 때리고, 서로 다른 `paymentKey`/`orderId` 를
보내 방어선이 뚫리면 유니크 제약이 아니라 중복 승인으로 드러나게 했습니다. 50라운드 반복.

| 지표 | 결과 |
|---|---|
| 총 승인 시도 | 1,000건 (20 × 50) |
| 승인 성공 | **정확히 50건** (라운드당 1건) |
| 거절 | 950건 (전부 HTTP 400) |
| `duplicate_approvals` | **0** |
| 승인 후 상태 / 금액 | 전 라운드 `DONE` / 금액 무결 |

`PaymentRepository.paymentInProgress` 의 조건부 UPDATE
(`WHERE id=? AND payment_status='READY'`) 가 CAS 로 정확히 동작합니다.
**결제 도메인에서 가장 중요한 불변식이 동시성 하에서 유지됨을 확인했습니다.**

### 3-4. 결제 승인 — 부하

| 프로파일 | 처리량 | p95 | p99 | max | 실패율 |
|---|---|---|---|---|---|
| load (50 req/s, 2분) | 50.3 req/s (6,062건) | 13.4ms | 27.8ms | 205ms | 0% |
| spike (20 → **300** req/s, 4분) | 124.9 req/s (30,062건) | 20.7ms | 54.6ms | 334ms | 0% |

spike 는 VU 60개로 피크 300 req/s 를 모두 소화했고 `dropped_iterations` 도 0 이었습니다.
Hikari 획득 대기는 피크에서 최대 152ms, `pending`·`timeout` 모두 0.

> **커넥션 획득이 요청 1건당 4.0회입니다.** (승인 6,062건 → 획득 24,125회 / spike 30,062건 → 120,129회)
> `PaymentService.confirm` 이 `@Transactional` 이 아니고 `PaymentPersistenceService` 의 각 메서드가
> 자기 트랜잭션을 열기 때문입니다 — 조회 / 조건부 UPDATE / 상태 반영 / 알림 저장이 각각 1회.
> 풀 크기가 10 이므로 **승인 요청 2~3건이면 풀을 한 번씩 다 돌립니다.**

### 3-5. 결제 취소 — 지원자 수에 따른 비용 ⭐

동일 조건(20 req/s, 2분, 2,401건 취소)에서 공고당 PENDING 지원자 수만 바꿔 3회 측정.

| 공고당 지원자 | p95 | p99 | avg | 기준선 대비 |
|---|---|---|---|---|
| 0명 | **14.96ms** | 18.1ms | 13.2ms | — |
| 50명 | **43.94ms** | 62.6ms | 33.0ms | **2.9배** |
| 200명 | **110.06ms** | 139.4ms | 95.6ms | **7.4배** |

실패율 0%, 커넥션 획득은 세 경우 모두 요청당 4.0회로 동일.

**해석.** 지원자 1명당 약 **+0.48 ~ 0.58ms** 로 거의 완전한 선형입니다
(50명 구간 0.58ms/명, 200명 구간 0.48ms/명 — 많아질수록 약간 유리해지는 건 배치 패치 효과).

원인은 `PaymentPersistenceService.rejectApplication` 입니다.

```java
applicationRepository.findAllByPostAndStatus(post, ApplicationStatus.PENDING)
        .forEach(Application::reject);   // 지원자 1명당 UPDATE 1개
```

커넥션 획득 횟수가 늘지 않은 것은 이 루프가 **한 트랜잭션 안**에서 돌기 때문입니다.
즉 커넥션을 더 쓰는 게 아니라 **커넥션을 더 오래 붙잡습니다** — 풀이 10인 상황에서는
이쪽이 더 위험합니다. 인기 공고(지원자 200명)가 몰려 취소되면 풀 점유 시간이 7배로 늘어납니다.

---

## 4. 권고 (우선순위)

> 앞서 제안한 사전 점검 항목 중 **SQL 포매팅 속성 주석 처리**, **`PaymentScheduler` 주기 완화
> (매분 → 시간당 1회)**, **Prometheus remote-write 수신 활성화** 는 측정 전에 이미 적용돼
> 있었습니다. 아래는 그 다음 단계입니다.

### ① Hikari 풀 크기를 명시적으로 설정 — 가장 효과 큼

현재 설정에 없어 기본값 10 입니다. 읽기 1,100 req/s 에서 4초 대기가 나온 직접적 원인입니다.

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20   # 코어수·DB max_connections 보고 조정
      connection-timeout: 3000  # 30초 기본값은 장애를 "느린 성공"으로 가려 버립니다
```

`connection-timeout` 을 줄이면 풀 포화가 p99 꼬리가 아니라 **에러로 드러나** 탐지가 쉬워집니다.

### ② `rejectApplication` 을 벌크 UPDATE 로

지원자 N명 → UPDATE N개가 그대로 응답시간에 반영됩니다. `@Modifying` 쿼리 하나로 바꾸면
지원자 수와 무관한 상수 시간이 됩니다. 단 `Application.syncActivePostId()` 가 상태와 짝을
맞추는 로직이라, 벌크 쿼리에서 `active_post_id = NULL` 도 같이 세팅해야 합니다.

### ③ 승인·취소의 트랜잭션 경계 정리

요청당 커넥션 4회는 풀 크기 10에서 부담입니다. 외부 결제 API 호출을 트랜잭션에 넣으면
안 되므로 현재의 "DB → 외부호출 → DB" 구조는 유지하되, **외부호출 앞의 DB 작업(조회 + 조건부
UPDATE)과 뒤의 DB 작업(상태 반영 + 알림)을 각각 한 트랜잭션으로 묶으면 4회 → 2회**가 됩니다.

### ④ 외부 API 지연을 넣고 재측정 — 이 보고서의 가장 큰 한계

`paymentKey` 가 `INIT-PAYKEY-` 로 시작하면 `TossPaymentClient` 가 실제 토스 호출을 건너뛰기
때문에 반복 부하가 가능했지만, **외부 API 지연이 0으로 측정됐습니다.** 토스 승인 API 가
실제로 200~500ms 라면 승인 p95 는 13.4ms 가 아니라 **200ms 대**가 됩니다.
위 승인·취소 수치는 모두 **낙관적 하한선**으로 읽어야 합니다.

### ⑤ Tomcat 스레드 지표 노출

`tomcat_threads_busy` / `_config_max_threads` 가 `/actuator/prometheus` 에 없어
스레드 포화를 확인할 수 없었습니다.

```yaml
server:
  tomcat:
    mbeanregistry:
      enabled: true
```

---

## 5. 측정 중 발견한 운영 이슈

### `ddl-auto: create` 가 재시작마다 DB 를 비웁니다

`application-dev.yaml` 이 `ddl-auto: create` 라서 **dev 로 서버를 띄울 때마다 스키마가
드롭·재생성되고 `BaseInitData` 가 다시 심습니다.** `BaseInitData` 주석에도 명시된 의도된
설계지만, 부하테스트 워크플로와 정면으로 충돌합니다.

실제로 측정 중 이 때문에 시드 50,000건이 날아갔습니다. 카나리 행을 넣고 재시작해 확인한
결과 `drop table if exists application`… 이 그대로 찍혔습니다.

→ **부하테스트는 서버를 먼저 띄우고 그 다음에 시딩하고, 테스트 중에는 서버를 재시작하지 말아야 합니다.**
(이 내용은 `loadtest/README.md` 에도 적어 두었습니다.)

### `/actuator/loggers` 미노출

`management.endpoints.web.exposure.include: health, prometheus` 라서 런타임에 로그 레벨을
바꿀 수 없습니다. 측정용 인스턴스를 띄울 때 커맨드라인으로 꺼야 했습니다.

---

## 6. 재현 방법

```bash
cd /Users/admin/DevCProject

# 1) 측정용 인스턴스 (SQL 로깅 off, JIT 정상)
./gradlew bootJar -x test
java -jar build/libs/NBE-12-14-2-Team06-0.0.1.jar \
  --server.port=8081 --spring.profiles.active=dev \
  --logging.level.org.hibernate.SQL=WARN \
  --logging.level.org.hibernate.orm.jdbc.bind=WARN \
  --spring.jpa.properties.hibernate.format_sql=false \
  --spring.jpa.properties.hibernate.highlight_sql=false \
  --spring.jpa.properties.hibernate.use_sql_comments=false

# 2) 서버가 뜬 뒤에 시딩 (순서 중요 — 재시작하면 날아갑니다)
set -a && . ./.env && set +a
docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/payment/sql/seed.sql

# 3) 측정 (쓰기 테스트 사이마다 reset.sql)
export K6="k6 run --no-color -e BASE_URL=http://localhost:8081"
$K6 loadtest/payment/smoke.js
$K6 -e STAGE=load -e RATE=100 -e DURATION=2m loadtest/payment/read.js
$K6 -e STAGE=stress -e STAGE_DURATION=30s loadtest/payment/read.js
$K6 -e CONCURRENCY=20 -e ROUNDS=50 loadtest/payment/confirm-race.js
$K6 -e STAGE=load -e RATE=50 -e DURATION=2m loadtest/payment/confirm.js
$K6 -e STAGE=spike loadtest/payment/confirm.js
$K6 -e STAGE=load -e RATE=20 -e DURATION=2m loadtest/payment/cancel.js

# 취소 비용 비교: seed-applications.sql 의 @applicants 를 0/50/200 으로 바꿔가며 반복

# 4) 정리
docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/payment/sql/teardown.sql
```

---

## 7. 측정 중 수정한 스크립트 결함

보고서 수치는 모두 수정 후 버전으로 측정했습니다.

| 파일 | 문제 | 수정 |
|---|---|---|
| `sql/seed.sql` | 두 번 실행하면 기존 공고에 결제가 중복 부착되고 `order_id` 유니크 충돌로 중단 (실측: R 공고 52,000개에 결제 84,000건) | 이미 결제가 있는 공고를 임시 테이블로 제외 |
| `sql/reset.sql`<br>`sql/teardown.sql` | 알림 삭제를 `ON n.url = CONCAT('/client/posts/', p.id)` 로 조인 → 인덱스 미사용, 알림 3만 × 공고 5만 = 15억 비교. **실측 12분 초과** | URL 에서 공고 id 를 뽑아 PK 로 조인 → **1.8초** |
| `payment/cancel.js`<br>`payment/smoke.js` | `BaseInitData` 결제까지 취소 대상으로 집음 → `400-46` 오탐과 `teardown` 이 못 지우는 잔여 행 발생 | `order_id` 접두어(`LT-ORDER-D-`)로 시드 행만 선택 |
| `payment/{read,confirm,cancel}.js` | stress/spike 단계 길이가 2분 고정 | `-e STAGE_DURATION=30s` 로 조절 가능 |

> `400-46`("이미 종료된 동행이거나 매칭되었기 때문에...")은 **서버가 올바르게 거절한 것**입니다.
> 원인은 스크립트가 공고 상태가 `MATCHED`/`COMPLETED` 인 시드 결제를 대상으로 집은 것이었고,
> 서버 버그가 아닙니다.

---

## 8. 측정 후 DB 상태

테스트 데이터를 전부 정리하고 `BaseInitData` 초기 상태로 복구했습니다.

| 테이블 | 복구 후 |
|---|---|
| post | 16 |
| payment | 16 (DONE 13 / READY 3) |
| application | 0 |
| notification | 0 |
| users | 7 |
