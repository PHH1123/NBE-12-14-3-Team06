# 부하테스트

k6 기반 부하테스트. **도메인별로 폴더를 나눕니다.**

```
loadtest/
├─ README.md            ← 이 파일. 도메인 공통 준비·주의사항·Grafana
├─ lib/                 모든 도메인이 같이 쓰는 코드
│  ├─ config.js         BASE_URL, PASSWORD, STAGE, 프로파일 선택, 반복 횟수 추정
│  ├─ auth.js           로그인 → accessToken 쿠키. 토큰 만료 전 자동 재로그인
│  └─ rsdata.js         RsData 껍질 벗기기 (unwrap, peekStatusCode)
├─ payment/             결제 도메인 ✅
│  ├─ README.md         실행법
│  ├─ lib/              결제 전용 설정·헬퍼
│  ├─ sql/              결제 전용 시딩·정리 SQL
│  ├─ reports/          측정 보고서 + Grafana 캡처
│  └─ *.js              시나리오
├─ ride/                이동수단 도메인 ✅ (조회 전용)
│  └─ (같은 구조)
└─ settlement/          정산 도메인 ✅ (조회 + 쓰기 + 관리자)
   └─ (같은 구조)
```

| 도메인 | 성격 | 상태 | 문서 |
|---|---|---|---|
| 결제 (payment) | 읽기 + 쓰기 + 동시성 | 측정 완료 · **개선 A/B 완료** | [README](payment/README.md) · [보고서](payment/reports/REPORT-2026-10-18-grafana.md) · [벌크 UPDATE A/B](payment/reports/REPORT-2026-10-18-bulk-reject.md) |
| 이동수단 (ride) | **조회 전용** (노출 API 가 GET 둘뿐) | 측정 완료 | [README](ride/README.md) · [보고서](ride/reports/REPORT.md) |
| 정산 (settlement) | 읽기 + 쓰기 + 동시성 + **관리자** | 측정 완료 | [README](settlement/README.md) · [보고서](settlement/reports/REPORT.md) |

도메인마다 테스트 모양이 다릅니다. 쓰기가 없으면 `reset.sql` 도 race 시나리오도 필요 없습니다 —
**그 도메인에 실제로 열려 있는 API 를 먼저 세어 보고** 세트를 정하세요.

## 새 도메인을 추가할 때

```
loadtest/<도메인>/
├─ README.md     실행법
├─ lib/
│  ├─ config.js  그 도메인 전용 계정·상수 (-e 로 덮어쓸 수 있게)
│  └─ *.js       setup() 데이터 수집 등 도메인 헬퍼
├─ sql/          시딩 / reset / teardown
├─ reports/      보고서 + images/
└─ *.js          시나리오 (smoke 부터)
```

`lib/` 에는 **도메인 이름이 등장하지 않는 것만** 둡니다. 특정 계정, 시드 접두어, 엔드포인트
지식은 전부 `<도메인>/lib/` 로 보내세요. `payment/` 가 그 본보기입니다.

시나리오 안에서의 import 모양:

```js
import { BASE_URL, STAGE, pickProfile } from '../lib/config.js';   // 공통
import { jsonAuthHeaders } from '../lib/auth.js';                  // 공통
import { peekStatusCode } from '../lib/rsdata.js';                 // 공통
import { LT_CLIENT } from './lib/config.js';                       // 도메인 전용
import { collectPayments } from './lib/payments.js';               // 도메인 전용
```

---

## 0. 준비

### k6 설치

```bash
brew install k6
```

### 실행 위치: 리포지토리 루트

모든 명령은 **`/Users/admin/DevCProject`** 에서 돌립니다. `frontend/` 안에서 돌리면
`loadtest/` 를 못 찾아 아래 에러가 납니다.

```
ERRO[0000] The moduleSpecifier "loadtest/payment/smoke.js" couldn't be found on local disk.
```

```bash
cd /Users/admin/DevCProject
```

절대 경로로 돌려도 됩니다 (`k6 run /Users/admin/DevCProject/loadtest/payment/smoke.js`).

### ⚠️ 돌리기 전에 꼭 확인할 것 (도메인 공통)

**1) SQL 로깅을 끄세요.** `application.yaml` 의 `format_sql`/`highlight_sql`/`use_sql_comments` 는 주석 처리돼 있지만, **SQL 문 자체의 로깅은 `logback-spring.xml` 에 따로 켜져 있습니다.**

```xml
<!-- dev 프로파일 -->
<logger name="org.hibernate.SQL" level="DEBUG"/>
<logger name="org.hibernate.orm.jdbc.bind" level="TRACE"/>
```

쿼리 전건 + 바인딩 파라미터 전건을 찍으면 측정값이 왜곡됩니다. 측정할 때는 `WARN` 으로 내리세요. `/actuator/loggers` 가 노출돼 있지 않아 런타임 변경이 안 되므로, 기동 시 커맨드라인으로 넘기는 게 가장 간단합니다 ([3절](#3-측정용-인스턴스-띄우기)).

**2) 스케줄러가 간섭합니다.** `PaymentScheduler` 는 매시 30분(`cron = "0 30 * * * *"`)에 삭제된 결제를 훑고, `SettlementScheduler` 는 **평일 10시**(`0 0 10 * * 1-5`)에 PENDING·FAILED 정산 전건을 훑어 정산합니다. 측정 구간이 그 시각과 겹치지 않게 잡거나, 겹친 채로 재서 간섭을 함께 보세요. 정산 시드는 그 시각에 멋대로 COMPLETED 로 바뀝니다.

**3) ⚠️ 백엔드를 재시작하면 시드가 전부 날아갑니다.** dev 프로파일은 `ddl-auto: create` 입니다 (`application-dev.yaml`). 서버가 뜰 때마다 스키마를 드롭·재생성하고 `BaseInitData` 가 다시 심습니다 — `BaseInitData` 주석에도 명시된 의도된 설계입니다.

따라서 순서가 중요합니다.

```
① 백엔드 기동  →  ② 시딩  →  ③ k6 실행  →  (테스트 중 재시작 금지)
```

기동할 때 `--spring.jpa.hibernate.ddl-auto=update` 를 넘기면 파일을 고치지 않고도 피할 수 있습니다 ([3절](#3-측정용-인스턴스-띄우기)).

IntelliJ 로 띄운 경우 `spring-boot-devtools` 가 클래스 변경을 감지해 **자동 재시작**하므로, 측정 중에는 빌드·컴파일도 하지 마세요. 시드가 화면에도 보이니 가능하면 부하테스트용 DB 를 따로 두는 게 좋습니다.

**4) 한 머신에서 k6 + 서버 + MySQL 을 같이 돌리면** CPU 경쟁으로 수치 신뢰도가 떨어집니다. 최소한 `stress` 프로파일의 결과는 그대로 믿지 마세요. 실제로 결제 도메인에서 **같은 스크립트가 3배 다른 처리량을 낸 적**이 있습니다 ([보고서 5절](payment/reports/REPORT-2026-10-18-grafana.md#5-이전-보고서와-다른-결과가-나온-지점)).

---

## 1. 모든 도메인이 기대고 있는 서버 동작

도메인 스크립트를 새로 쓸 때 알고 있어야 하는 공통 사항입니다.

**인증은 쿠키 전용** — `JwtAuthenticationFilter` 가 `Rq.getAccessToken()` 으로 `accessToken`
쿠키만 읽습니다. Authorization 헤더는 보지 않습니다. k6 의 쿠키 항아리는 VU 마다 따로라서
`setup()` 에서 받은 쿠키가 VU 로 넘어가지 않으므로, `lib/auth.js` 가 토큰 값을 꺼내
`Cookie` 헤더로 직접 싣습니다.

**토큰이 10분마다 만료** — `.env` 의 `JWT_EXPIRATION=600`. 10분 넘는 테스트는 중간부터
전부 401 이 됩니다. `lib/auth.js` 가 8분마다 조용히 재로그인하고, 그 요청은
`endpoint:auth_login` 태그가 붙어 도메인 임계값 계산에서 빠집니다.

**HTTP 상태는 RsData 에서 나옵니다** — `ResponseAspect` 가 `RsData.statusCode` 의 앞자리를
그대로 응답 상태로 세팅합니다(`"400-44"` → HTTP 400). 덕분에 `http_req_failed` 가 의미 있는
지표가 됩니다. **단 2xx 도 그대로 반영됩니다** — 결제 취소는 `"204-41"` → HTTP 204 라서
200 을 기대하면 전부 실패로 잡힙니다. 새 도메인에서도 컨트롤러의 statusCode 를 먼저 확인하세요.

---

## 2. Grafana 에 붙이기

이미 `docker-compose.monitoring.yml` 로 Prometheus + Grafana 가 돌고 있으니, k6 지표를 같은 Grafana 에 넣으면 클라이언트 지표와 서버 지표를 한 화면에서 볼 수 있습니다.

Prometheus 가 remote write 를 받아야 합니다. `docker-compose.monitoring.yml` 에는 **이미 적용돼 있습니다** — 참고용으로 남겨 둡니다.

```yaml
  prometheus:
    image: prom/prometheus:v3.7.3
    command:
      - --config.file=/etc/prometheus/prometheus.yml
      - --web.enable-remote-write-receiver   # ← 추가
```

그 다음:

```bash
export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),max'

k6 run -o experimental-prometheus-rw --tag run=<시나리오이름> loadtest/payment/confirm.js
```

`--tag run=...` 을 붙여 두면 Grafana 에서 `run` 라벨로 시나리오를 구분할 수 있습니다.

### ⚠️ 계측 함정 세 가지

**1) Prometheus 는 `BACKEND_PORT`(기본 8080)만 스크랩합니다.** 측정용 인스턴스를 8081 에 띄우면
k6 지표는 들어오지만 **서버 지표(Hikari, http_server_requests)가 하나도 안 들어옵니다.**
8080 에 띄우거나 `BACKEND_PORT` 를 맞춰 주세요.

**2) k6 의 처리량·백분위수 지표는 "URL 이 반복되는 테스트"에서만 맞습니다.** k6 는 `url`/`name` 을
라벨로 싣기 때문에, 요청마다 id 가 달라지는 쓰기 테스트는 시리즈가 1건씩 쪼개져 `rate()` 가
잡히지 않습니다. 서버 집계 패널(`http_server_requests_seconds_*`)을 보거나,
`--system-tags` 에서 `url,name` 을 빼고 돌리세요.

**3) k6 v2 의 remote-write 는 지연 지표를 초 단위로 내보냅니다.** 패널 단위를 `ms` 로 두면
1000배 작게 보입니다 (k6 요약의 `max=455.18ms` ↔ Prometheus 의 `0.455`).

### 서버 쪽에서 볼 지표

`/actuator/prometheus` 로 이미 수집되고 있습니다.

| 지표 | 왜 보는지 |
|---|---|
| `hikaricp_connections_pending`, `hikaricp_connections_active` | **가장 먼저 터질 곳.** 커넥션 풀 크기가 설정에 없어 기본값 10입니다 |
| `hikaricp_connections_acquire_seconds_*` | 요청당 커넥션 획득 횟수·대기시간. 트랜잭션 경계가 쪼개져 있으면 여기 드러납니다 |
| `http_server_requests_seconds_*` | 서버가 본 지연 (네트워크 제외). **백분위수는 없습니다** — 평균과 max 뿐 |
| `jvm_gc_pause_seconds` | soak 에서 누적되는지 |

`tomcat_threads_busy` 와 `http_server_requests_seconds_bucket` 은 **노출돼 있지 않습니다.**
스레드 포화와 서버 측 p95/p99 를 보려면 설정을 켜야 합니다
([보고서 권고 ⑤⑥](payment/reports/REPORT-2026-10-18-grafana.md#-서버-측-응답시간-백분위수를-노출)).

### 대시보드

| 대시보드 | 파일 | uid |
|---|---|---|
| GAJI Backend Monitoring | `monitoring/grafana/dashboards/gaji-backend-monitoring.json` | `ad86lzc` |
| 결제 도메인 부하테스트 (k6 + Hikari) | `monitoring/grafana/dashboards/payment-loadtest.json` | `payment-loadtest` |
| 이동수단 도메인 부하테스트 (k6 + Hikari) | `monitoring/grafana/dashboards/ride-loadtest.json` | `ride-loadtest` |
| 정산 도메인 부하테스트 (k6 + Hikari) | `monitoring/grafana/dashboards/settlement-loadtest.json` | `settlement-loadtest` |

새 도메인은 기존 JSON 을 복사해 `uri=~".*<도메인>.*"` 필터와 `uid`/`title` 만 바꾸면 됩니다.

provisioning 으로 자동 로드되므로 Grafana(<http://localhost:3001>) 의 GAJI 폴더에서 바로 보입니다.
패널 하나만 크게 보려면 URL 에 `&viewPanel=panel-107&kiosk` 를 붙이세요.

---

## 3. 측정용 인스턴스 띄우기

SQL 로깅을 끄고, 시드가 날아가지 않게 `ddl-auto` 를 덮어쓰고, Prometheus 가 스크랩하는
8080 에 띄웁니다. **애플리케이션 설정 파일은 건드리지 않습니다.**

```bash
cd /Users/admin/DevCProject
set -a && . ./.env && set +a

./gradlew bootJar -x test
java -jar build/libs/NBE-12-14-2-Team06-0.0.1.jar \
  --server.port=8080 --spring.profiles.active=dev \
  --spring.jpa.hibernate.ddl-auto=update \
  --logging.level.org.hibernate.SQL=WARN \
  --logging.level.org.hibernate.orm.jdbc.bind=WARN \
  --spring.jpa.properties.hibernate.format_sql=false \
  --spring.jpa.properties.hibernate.highlight_sql=false \
  --spring.jpa.properties.hibernate.use_sql_comments=false
```

그 다음 도메인별 README 로 가서 시딩 → 실행하세요.
