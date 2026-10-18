# 정산(settlement) 도메인 부하테스트

> 도메인 공통 준비·주의사항·Grafana 설정은 [../README.md](../README.md) 를 먼저 보세요.
> 여기에는 **정산 도메인에만 해당하는 것**만 적습니다.

```
settlement/
├─ lib/
│  ├─ config.js      LT_ESCORT, OTHER_ESCORT, ADMIN, 조회 구간
│  └─ settlements.js setup() 데이터 수집 (collectSettlements / requireRows)
├─ sql/
│  ├─ seed.sql       [LT-SETL-P/C] 공고 + 지원 + 정산
│  ├─ reset.sql      소모한 정산 상태 복구 (반복 측정용, 빠름)
│  └─ teardown.sql   시드 전부 삭제
├─ reports/
│  └─ REPORT.md      측정 보고서 + Grafana 캡처
├─ smoke.js          전 경로 + 소유권 + 관리자 — 제일 먼저 돌릴 것
├─ read.js           동행 매니저 목록/상세 부하
├─ request.js        정산 요청 부하 (load / stress / spike)
├─ request-race.js   동일 정산 동시 요청 → 중복 정산 검증 (user / admin)
└─ admin.js          관리자 목록/통계 조회 부하
```

---

## 0. 이 도메인의 특징

**두 역할이 서로 다른 API 를 씁니다.**

| 엔드포인트 | 역할 | 비고 |
|---|---|---|
| `GET /api/v1/settlements` | ESCORT | Page. `post.escortStartAt` 으로 필터·정렬 |
| `GET /api/v1/settlements/{id}` | ESCORT | 정산 주인만 (`403-71`) |
| `POST /api/v1/settlements/{id}` | ESCORT | **상태 소모.** 중복은 `400-71` |
| `GET /api/v1/admin/settlements` | ADMIN | Page |
| `GET /api/v1/admin/settlements/analytics` | ADMIN | **페이징 없음 — 전건을 JVM 으로** |
| `POST /api/v1/admin/settlements/{id}` | ADMIN | **CAS 없음** (PROCESSING 도 통과) |

**⚠️ 정산 요청은 돈이 나가는 경로입니다.** 중복 방어선이 뚫리면 이중 지급입니다.
`request-race.js` 가 그걸 검증하고, **사용자 경로와 관리자 경로의 방어선이 다르다**는 점까지 같이 봅니다.

**⚠️ dev DB 의 settlement 테이블은 기본적으로 비어 있습니다.** 정산은 동행 완료 시
`SettlementService.createSettlement` 로 생기는데 `BaseInitData` 는 그 경로를 타지 않습니다.
시딩 없이는 테스트할 대상이 없습니다.

**스케줄러가 있습니다.** `SettlementScheduler` 가 **평일 10시**(`0 0 10 * * 1-5`)에
PENDING·FAILED 전건을 훑어 정산합니다. 그 시각에 측정하면 시드가 멋대로 COMPLETED 로 바뀝니다.

---

## 1. 시딩

```bash
cd /Users/admin/DevCProject
set -a && . ./.env && set +a

# 건수는 seed.sql 상단의 @pending_count / @completed_count 에서 조절
docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/settlement/sql/seed.sql
```

기본값은 **PENDING 8,000건 / COMPLETED 4,000건**입니다.
`settlement.application_id` 가 UNIQUE 라 **공고 1 : 지원 1 : 정산 1** 로 같이 만듭니다.

정산 주인은 `escort01`(`LT_ESCORT`)입니다. `AccountDto` 조회가 `EscortProfile` 을 조인하므로
(`ep.userId = e.id`) **계좌 정보가 있는 계정이어야 합니다.** `BaseInitData` 의 escort01~03 에는
`escort_profiles` 가 있지만, 결제 도메인의 `seed-applications.sql` 이 만드는 `lt-escort-*` 계정에는
없습니다 — 그 계정으로 바꾸면 전부 `404-71` 이 됩니다.

**정산 요청은 한 건을 한 번만 쓸 수 있으므로** PENDING 건수가 `RATE × DURATION(초)` 보다 넉넉해야 합니다.

---

## 2. 실행

### 스모크 (제일 먼저)

```bash
k6 run loadtest/settlement/smoke.js
```

조회·요청·중복 차단·소유권·관리자까지 1 VU 로 확인합니다. 체크 23종이 전부 통과해야 합니다.

### 조회 부하 (상태를 안 바꿔서 reset 불필요)

```bash
k6 run loadtest/settlement/read.js                          # 100 iter/s, 10분
k6 run -e STAGE=stress loadtest/settlement/read.js           # 50 → 1200 iter/s 단계 증가
k6 run -e STAGE=stress -e STAGE_DURATION=30s loadtest/settlement/read.js
k6 run -e STAGE=soak   loadtest/settlement/read.js           # 50 iter/s, 1시간
```

1 iteration = 목록 1건 + 상세 1건 = **2 요청**입니다.

### 관리자 조회 부하 (상태를 안 바꿔서 reset 불필요)

```bash
k6 run loadtest/settlement/admin.js                         # 20 iter/s, 5분
k6 run -e ANALYTICS=0 loadtest/settlement/admin.js          # 목록만 (통계 제외)
k6 run -e STAGE=stress -e STAGE_DURATION=30s loadtest/settlement/admin.js
```

같은 테이블·같은 구간인데 **목록은 Page, 통계는 전건 로드**입니다. 둘을 나란히 재는 게 목적입니다.

### 동시성 검증 (성능보다 이게 먼저)

```bash
k6 run -e MODE=user  -e CONCURRENCY=20 -e ROUNDS=50 loadtest/settlement/request-race.js
k6 run -e MODE=admin -e CONCURRENCY=20 -e ROUNDS=50 loadtest/settlement/request-race.js
```

- `MODE=user` — **정확히 1건만 200**이어야 합니다. `duplicate_settlements` 가 0이 아니면 이중 지급입니다.
- `MODE=admin` — 관리자 경로는 설계상 PROCESSING 도 통과시키므로 **임계값을 걸지 않고 몇 건이
  통과하는지 측정만** 합니다 (`approved_per_round`).

### 정산 요청 부하

```bash
k6 run loadtest/settlement/request.js                       # 20 rps, 5분
k6 run -e STAGE=spike loadtest/settlement/request.js        # 20 → 300 rps 급증
k6 run -e STAGE=load -e RATE=50 -e DURATION=2m loadtest/settlement/request.js
```

### 실행 사이에 반드시 (요청·race 를 돌린 뒤)

```bash
docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/settlement/sql/reset.sql
```

`reset.sql` 은 소모한 정산을 시딩 직후 상태로 되돌리고(P 세트 → PENDING, C 세트 → COMPLETED)
테스트가 만든 정산 완료 알림을 지웁니다. 행을 만들거나 지우지 않으므로 teardown + seed 보다 훨씬 빠릅니다.

전부 지우려면 `sql/teardown.sql`. 삭제 범위는 `[LT-SETL-` 접두어 공고와 거기 달린
지원·정산·패널티·알림으로 한정됩니다.

---

## 3. 이 스크립트가 기대고 있는 정산 도메인 동작

서버가 바뀌면 같이 손봐야 하는 지점들입니다. (인증·RsData 같은 공통 사항은 [../README.md](../README.md#1-모든-도메인이-기대고-있는-서버-동작))

**외부 정산 API 는 목입니다** — `MockSettlementClient` 가 받은 금액·계좌를 그대로 돌려줍니다.
실제 금융결제원 호출이 없어 **외부 API 지연이 0 으로 측정됩니다.** 지금 나오는 요청 p95 는
실서비스보다 낙관적입니다. 구현체가 실제 클라이언트로 바뀌면 다시 재야 합니다.

**중복 요청 방어선은 조건부 UPDATE 한 줄** — `SettlementRepository.updateProcessing`

```sql
UPDATE settlement SET settlement_status='PROCESSING'
 WHERE id=:id AND (settlement_status='PENDING' OR settlement_status='FAILED')
```

영향 행이 0이면 `400-71`. `request-race.js -e MODE=user` 가 검증하는 대상이 바로 이것입니다.

**관리자 경로는 같은 자리에 CAS 가 없습니다** — `updateProcessingForAdmin` 의 WHERE 절에
`PROCESSING` 이 포함돼 있습니다. 컨트롤러 설명에도 "정산 상태가 PROCESSING이어도 정산이
요청됩니다"라고 적혀 있어 **멈춘 건을 다시 밀어 넣기 위한 의도된 동작**이지만, 동시 요청에서는
여러 건이 같이 통과할 수 있습니다. `MODE=admin` 이 그 수를 셉니다.

**요청 경로는 트랜잭션이 쪼개져 있습니다** — `SettlementService.request` 에 `@Transactional` 이
없고 `SettlementPersistenceService` 의 메서드들이 각자 트랜잭션을 엽니다
(계좌 조회 → 조건부 UPDATE → 외부 호출 → 상태 반영 → 재조회 → 알림). 외부 호출을 트랜잭션에
넣지 않으려는 구조라 방향은 맞지만, 요청 1건이 커넥션을 여러 번 잡습니다.

**상태 코드가 둘로 갈립니다** — 같은 "요청 불가"라도
- `400-71` 조건부 UPDATE 가 0행 (이미 PROCESSING/COMPLETED)
- `404-71` 조회 자체가 빔 (COMPLETED 이거나 **남의 정산**)

`findByIdAndState(userId, settlementId)` 가 escort id 까지 WHERE 에 넣기 때문에 남의 건은
403 이 아니라 404 입니다. 상세 조회(`403-71`)와 기준이 다릅니다.

**통계는 전건을 메모리로 끌어옵니다** — `findAnalytics` 가 `List<Settlement>` 를 그대로 돌려주고
`SettlementAnalyticsResponse` 가 자바 for 문으로 합산합니다. 페이징도, SQL 집계도 없습니다.
기본 조회 구간은 최근 7일입니다.

**목록과 상세의 페치 전략이 다릅니다** — 목록(`findAllByUserIdAndDate`)은 User/Application/Post 를
`join fetch` 하지만, 상세(`findSettlement`)는 `findById` 라 fetch join 이 없고
`SettlementResponse` 가 `getApplication().getPost()` 를 타며 LAZY 로딩이 발생합니다.

---

## 4. 측정 결과

[reports/REPORT.md](reports/REPORT.md)
