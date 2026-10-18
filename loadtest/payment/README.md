# 결제(payment) 도메인 부하테스트

> 도메인 공통 준비·주의사항·Grafana 설정은 [../README.md](../README.md) 를 먼저 보세요.
> 여기에는 **결제 도메인에만 해당하는 것**만 적습니다.

```
payment/
├─ lib/
│  ├─ config.js   LT_CLIENT(쓰기 대상 의뢰인), READ_CLIENT(읽기 대상), 시드 order_id 접두어
│  └─ payments.js setup() 데이터 수집 (collectPayments / onlySeedRows / requireRows)
├─ sql/
│  ├─ seed.sql              공고 + READY/DONE 결제 대량 생성
│  ├─ seed-applications.sql 취소 비용 측정용 PENDING 지원 (선택)
│  ├─ reset.sql             소모한 결제 상태 복구 (반복 측정용, 빠름)
│  └─ teardown.sql          시드 전부 삭제
├─ reports/
│  ├─ REPORT-2026-10-18-bulk-reject.md  최신. 벌크 UPDATE 전후 A/B
│  ├─ REPORT-2026-10-18-grafana.md      전 시나리오 측정 + Grafana 캡처
│  ├─ REPORT.md                         같은 날 1차 측정 (서버 지표 없음)
│  └─ images/                           Grafana 캡처
├─ smoke.js        1 VU 로 전 경로 1회 — 제일 먼저 돌릴 것
├─ read.js         조회 3종 부하 (load / stress / soak)
├─ confirm.js      결제 승인 부하 (load / stress / spike)
├─ confirm-race.js 동일 결제 동시 승인 → 중복 결제 검증
└─ cancel.js       결제 취소 부하
```

---

## 1. 시딩

```bash
cd /Users/admin/DevCProject
set -a && . ./.env && set +a

# 건수는 seed.sql 상단의 @ready_count / @done_count 에서 조절
docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/payment/sql/seed.sql
```

기본값은 READY 32,000건 / DONE 7,000건입니다. **승인·취소는 한 결제를 한 번만 쓸 수 있으므로**
`RATE × DURATION(초)` 보다 넉넉해야 합니다 (50rps × 10분 = 30,000건).

시드는 `client03`(`LT_CLIENT`)에 붙습니다. 읽기 테스트는 `BaseInitData` 데이터만 가진
`client01`(`READ_CLIENT`)을 씁니다 — 결제가 3만 건 붙은 계정으로 목록 조회를 재면
응답 크기가 수치를 다 먹기 때문입니다. 둘 다 `-e LT_CLIENT=` / `-e READ_CLIENT=` 로 바꿀 수 있습니다.

---

## 2. 실행

### 스모크 (제일 먼저)

```bash
k6 run loadtest/payment/smoke.js
```

로그인·시드·승인·취소가 전부 되는지 1 VU 로 확인합니다. 여기서 깨지면 나머지는 의미가 없습니다.

### 읽기 부하 (상태를 안 바꿔서 reset 불필요)

```bash
k6 run loadtest/payment/read.js                        # 100 rps, 10분
k6 run -e STAGE=stress loadtest/payment/read.js         # 50 → 1200 iter/s 단계 증가
k6 run -e STAGE=stress -e STAGE_DURATION=30s loadtest/payment/read.js
k6 run -e STAGE=soak   loadtest/payment/read.js         # 50 rps, 1시간
k6 run -e READ_CLIENT=client03 loadtest/payment/read.js # 3만 건 계정으로 목록 부하
```

### 동시성 검증 (성능보다 이게 먼저)

```bash
k6 run loadtest/payment/confirm-race.js
k6 run -e CONCURRENCY=20 -e ROUNDS=50 loadtest/payment/confirm-race.js
```

같은 `paymentId` 에 동시 N 건을 때려 **정확히 1건만 승인**되는지 봅니다.
`duplicate_approvals` 가 0 이 아니면 중복 결제입니다.

### 결제 승인 부하

```bash
k6 run loadtest/payment/confirm.js                     # 50 rps, 10분
k6 run -e STAGE=spike loadtest/payment/confirm.js      # 20 → 300 rps 급증
k6 run -e STAGE=stress loadtest/payment/confirm.js
k6 run -e STAGE=load -e RATE=100 -e DURATION=3m loadtest/payment/confirm.js
```

### 결제 취소 부하

```bash
k6 run loadtest/payment/cancel.js                      # 20 rps, 5분
```

지원자 수에 따른 비용 변화를 보려면 `sql/seed-applications.sql` 의 `@applicants` 를
0 → 50 → 200 으로 올려가며 같은 조건으로 세 번 재세요.

> ⚠️ 행 수 = `@applicants` × `[LT-PAY-D]` 공고 수 입니다. `@applicants=200` 에 D 공고가
> 7,000개면 지원 **140만 건**이 됩니다. 먼저 `seed.sql` 의 `@done_count` 를 줄이세요.

### 실행 사이에 반드시

```bash
docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/payment/sql/reset.sql
```

`reset.sql` 은 소모한 결제를 원래 상태로 되돌립니다(승인된 건 → READY, 취소된 건 → DONE,
취소가 만든 재결제 행 삭제, 거절된 지원 복구, 테스트 알림 삭제). teardown + seed 보다 훨씬 빠릅니다.

전부 지우려면 `sql/teardown.sql`. 삭제 범위는 `[LT-PAY-%` 접두어 공고와 `lt-escort-%` 계정으로
한정되어 `BaseInitData` 데이터는 건드리지 않습니다.

---

## 3. 이 스크립트가 기대고 있는 결제 도메인 동작

서버가 바뀌면 같이 손봐야 하는 지점들입니다. (인증·RsData 같은 공통 사항은 [../README.md](../README.md#1-모든-도메인이-기대고-있는-서버-동작))

**외부 결제 API 우회** — `TossPaymentClient.isSeedPaymentKey` 가 `INIT-PAYKEY-` 로 시작하는
키는 실제 토스 호출 없이 성공 처리합니다. 승인·취소를 반복 부하할 수 있는 이유입니다.

> ⚠️ **그만큼 외부 API 지연이 0 으로 측정됩니다.** 지금 나오는 승인 p95 는 실서비스보다
> 낙관적인 수치입니다. 현실적인 값을 보려면 그 분기에 고정 지연(200~500ms)을 넣거나
> mock HTTP 서버로 빼고 다시 재세요. 이게 이 테스트 세트의 가장 큰 한계입니다.

**취소는 HTTP 204** — 컨트롤러가 `"204-41"` 을 돌려주고 `ResponseAspect` 가 204 로 세팅합니다.
200 을 기대하면 전부 실패로 잡힙니다.

**금액은 숫자로 비교** — `Payment.verifyAmount` 가 `Integer.parseInt(request.amount())` 로
저장된 `amount(int)` 와 비교합니다. 스크립트는 `GET /api/v1/payments` 로 받은 금액을 그대로
되돌려 보내므로 영향이 없습니다.

> ⚠️ `amount` 가 **null 이거나 숫자가 아니면 `NumberFormatException` 으로 HTTP 500** 이 납니다
> (`PaymentConfirmRequest` 에 검증 애너테이션이 없고 컨트롤러에도 `@Valid` 가 없습니다).
> 예전 문자열 비교 시절에는 같은 입력이 `400-42` 였습니다. 부하 스크립트는 정상 금액만 보내므로
> 측정에는 영향이 없지만, 경계값 테스트를 추가한다면 이 차이를 알고 있어야 합니다.

**중복 승인 방어선은 조건부 UPDATE 한 줄** — `PaymentRepository.paymentInProgress`
(`WHERE id=? AND payment_status='READY'`). 영향 행이 0이면 `400-44`.
`confirm-race.js` 가 검증하는 대상이 바로 이것입니다.

**목록 조회에 페이지네이션이 없습니다** — `GET /api/v1/payments` 는 `findAllByUserId` 로
그 의뢰인의 결제를 **전부** 돌려줍니다. `setup()` 이 이걸 한 번만 긁어 쓰는 이유이자,
`READ_CLIENT` 를 따로 두는 이유입니다.

---

## 4. 측정 결과

| 보고서 | 내용 |
|---|---|
| [REPORT-2026-10-18-bulk-reject.md](reports/REPORT-2026-10-18-bulk-reject.md) | **최신.** `rejectApplication` 벌크 UPDATE 전후 A/B 비교 |
| [REPORT-2026-10-18-grafana.md](reports/REPORT-2026-10-18-grafana.md) | 전 시나리오 측정 + Grafana 캡처 9장 |
| [REPORT.md](reports/REPORT.md) | 같은 날 1차 측정. 8081 인스턴스라 서버 지표가 Prometheus 에 없음 |

`REPORT.md` 와 `REPORT-2026-10-18-grafana.md` 의 **읽기 한계점 수치가 3배 다릅니다.**
승인·취소·동시성 결과는 거의 일치합니다. 원인과 판단은
[grafana 보고서 5절](reports/REPORT-2026-10-18-grafana.md#5-이전-보고서와-다른-결과가-나온-지점)에 있습니다.

### 취소 비용 재측정 시 주의

`rejectApplication` 이 벌크 UPDATE 로 바뀌어 **취소 응답시간이 더는 지원자 수에 비례하지 않습니다.**
`seed-applications.sql` 로 지원자를 늘려도 평평하게 나오는 게 정상입니다
([A/B 보고서](reports/REPORT-2026-10-18-bulk-reject.md)).
