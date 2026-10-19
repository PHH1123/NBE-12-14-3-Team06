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

---

## 4. 운영 스펙으로 묶어서 재기 (권장)

맥북에서 무제한으로 재면 **그 맥북의 상한**이 나옵니다. 운영은 훨씬 작은 박스일 테니
**컨테이너로 CPU·메모리를 묶어 재는 쪽이 훨씬 쓸모 있습니다.**

### 운영 기준: AWS EC2 t3.micro (2 vCPU / 1 GiB, 버스터블)

t3.micro 는 버스터블이라 **CPU 크레딧 유무로 성능이 갈립니다.** 둘 다 쟀습니다.

| | 버스트 (크레딧 있음, `--cpus=2`) | 베이스라인 (크레딧 소진, `--cpus=0.2`) |
|---|---|---|
| 기동 시간 | 13초 | **181초** |
| 결제 읽기 피크 | 1,627 req/s *(실패 1.99%, p95 1.6s — 이미 무너진 뒤)* | **5 req/s** *(실패 84.5%)* |
| 정산 목록 **안전 한계** | **~20 req/s** | **~3 req/s** *(실패 31%)* |
| JVM CPU | 100% | 100% |

정산 목록은 고정 rate 를 올려가며 무너지는 지점을 찾았습니다 (버스트 기준).

| 목표 rate | 달성 | 실패율 | p95 | Hikari pending | GC 점유 |
|---|---|---|---|---|---|
| 10/s | 10.9/s | 0% | **101ms** | 0 | 0.04% |
| **20/s** | 20.6/s | **0%** | **131ms** | **0** | 0.05% |
| 40/s | 40.0/s | 0.16% | **1.76s** | 54 | 0.36% |
| 60/s | 55.5/s | **35.1%** | 4.25s | 190 | 0.8% |
| 80/s | 61.1/s | 39.1% | 6.38s | 189 | 0.71% |

**20 → 40 req/s 사이에서 무너집니다.** 40 에서 이미 p95 가 1.76초이고 pending 이 쌓이기 시작합니다.
GC 점유는 1% 미만이라 **병목은 GC 가 아니라 CPU + 커넥션 풀**입니다.

> **t3.micro 운영 가정이라면 정산 목록은 20 req/s 가 상한**이라고 보고 설계하세요.
> 맥북 무제한에서 90 req/s 였던 것과 4.5배 차이입니다.

### ⚠️ 1 GiB 에서는 JVM 이 SerialGC + 힙 256MB 를 고릅니다

옵션을 안 주면 t3.micro 에서 실제로 이렇게 뜹니다.

```
UseSerialGC  = true        # G1 아님
MaxHeapSize  = 268435456   # 256MB (컨테이너 메모리의 1/4)
```

HotSpot 은 **"2 CPU 이상 + 메모리 2GB 이상"** 을 server-class 로 보고 G1 을 고르는데,
1 GiB 는 그 기준에 못 미쳐 client-class 로 떨어집니다. 측정에서 GC 점유가 1% 미만이라
지금 워크로드에서는 문제가 아니었지만, **기본값이 그렇다는 건 알고 있어야 합니다.**

### 참고: 스펙별 비교 (결제 읽기)

| 스펙 | 피크 처리량 | 실패율 | JVM CPU |
|---|---|---|---|
| 무제한 (맥북 10코어) | 3,096 req/s | 0% | 13.7% |
| 2 vCPU / 2GB (heap 1g, G1) | 1,833 req/s | 1.5% | 100% |
| **2 vCPU / 1GB (t3.micro 버스트, SerialGC)** | 1,627 req/s | 2.0% | 100% |
| 1 vCPU / 1GB (heap 512m) | 55 req/s | 73.6% | 100% |
| **0.2 vCPU / 1GB (t3.micro 베이스라인)** | **5 req/s** | **84.5%** | 100% |

```bash
cd /Users/admin/DevCProject
set -a && . ./.env && set +a
./gradlew bootJar -x test

docker run -d --name gaji-backend-limited   --cpus=1 --memory=1g   -p 8080:8080   --add-host host.docker.internal:host-gateway   -v "$PWD/build/libs:/app:ro"   -e MYSQL_DATABASE="$MYSQL_DATABASE" -e MYSQL_USER="$MYSQL_USER" -e MYSQL_PASSWORD="$MYSQL_PASSWORD"   -e JWT_SECRET="$JWT_SECRET" -e JWT_EXPIRATION="$JWT_EXPIRATION"   -e REFRESH_TOKEN_EXPIRATION="$REFRESH_TOKEN_EXPIRATION"   -e CLAUDE_API_KEY="$CLAUDE_API_KEY" -e TOSS_SECRET="$TOSS_SECRET"   -e ADMIN_USERNAME="$ADMIN_USERNAME" -e ADMIN_PASSWORD="$ADMIN_PASSWORD"   -e ADMIN_EMAIL="$ADMIN_EMAIL" -e ADMIN_PHONE_NUM="$ADMIN_PHONE_NUM"   gradle:9.4.0-jdk25   java -Xmx512m -jar /app/NBE-12-14-2-Team06-0.0.1.jar     --server.port=8080 --spring.profiles.active=dev     --spring.datasource.url="jdbc:mysql://host.docker.internal:3307/$MYSQL_DATABASE?allowPublicKeyRetrieval=true&useSSL=false"     --spring.jpa.hibernate.ddl-auto=update     --logging.level.org.hibernate.SQL=WARN --logging.level.org.hibernate.orm.jdbc.bind=WARN
```

- **JVM 옵션은 일부러 주지 않습니다.** t3.micro 에 그냥 올렸을 때 실제로 뭐가 선택되는지 보려는 것입니다.
- `gradle:9.4.0-jdk25` 는 **이미 로컬에 받아져 있는 이미지**라 그냥 씁니다 (JDK 25 포함).
  빌드 toolchain 이 Java 25 라 그대로 실행됩니다. 전용 JRE 이미지를 받을 수 있으면 그쪽이 가볍습니다.
- MySQL 은 호스트(3307)에 그대로 두고 `host.docker.internal` 로 붙습니다.
  Prometheus 가 `host.docker.internal:8080` 을 스크랩하므로 **포트만 8080 으로 열어 두면 지표도 그대로** 들어옵니다.
- 기동 로그에서 `Effective CPU Count` 로 제한이 먹었는지 확인하세요.

### 해석할 때 주의

**① 한 번에 하나씩 바꾸세요.** 위 표에서 0.2 → 2 vCPU 가 300배 넘게 차이 납니다. CPU 만의 효과가
아니라 **GC·JIT 컴파일러 스레드가 전부 그 적은 코어에서 요청 처리와 싸우는 복합 효과**입니다.
스펙 하나만 움직여야 원인이 보입니다.

**② 운영 스펙을 그대로 넣으세요.** 사이 값을 보간하지 마세요 — ①의 이유로 선형이 아닙니다.

**②-1 버스터블 인스턴스는 두 상태를 다 재세요.** t3/t4g 는 크레딧이 떨어지면 베이스라인으로
떨어집니다(t3.micro = 2 vCPU x 10%). AWS 콘솔 기본값인 **unlimited 모드**면 크레딧을 초과해도
돈을 더 내고 버스트를 유지하지만, **standard 모드면 위 베이스라인 수치가 그대로 현실**이 됩니다.

**③ 맥북 Docker 는 VM 을 한 겹 거칩니다.** Apple Silicon(ARM) 과 운영(x86) 의 차이, VM 을 거치는
디스크 I/O 때문에 **절대값은 여전히 근사치**입니다. 그래도 무제한으로 재는 것보다 훨씬 가깝고,
**스펙 A vs B / 코드 전 vs 후 비교에는 충분히 신뢰할 수 있습니다.**

**④ 부하 생성기 경쟁 문제도 상당 부분 풀립니다.** 서버가 CPU 할당량을 따로 받으므로 k6 가 남은
코어를 써도 서버 몫을 뺏지 않습니다. 위 두 측정 모두 **JVM CPU 가 100% 에 붙었다는 건
"측정 구성의 한계" 가 아니라 "그 스펙의 진짜 한계" 에 도달했다는 뜻**입니다.

**⑤ 포화가 에러로 드러납니다.** `connection-timeout: 3000` 때문에 1 vCPU 구간에서 획득 대기가
3.3초에 걸리며 **73.6% 가 실패**했습니다. 느린 성공으로 숨지 않는다는 점에서 의도대로지만,
**운영 스펙에서는 이 지점이 생각보다 낮다**는 것도 같이 보여 줍니다.
