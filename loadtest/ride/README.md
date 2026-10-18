# 이동수단(ride) 도메인 부하테스트

> 도메인 공통 준비·주의사항·Grafana 설정은 [../README.md](../README.md) 를 먼저 보세요.
> 여기에는 **이동수단 도메인에만 해당하는 것**만 적습니다.

```
ride/
├─ lib/
│  ├─ config.js   LT_CLIENT(시드 공고 주인), OTHER_CLIENT(소유권 검증용), 수집 상한
│  └─ rides.js    setup() 데이터 수집 (collectSeedPosts / collectRides / requireSeed)
├─ sql/
│  ├─ seed.sql      [LT-RIDE] 공고 + 공고당 ride 2건
│  └─ teardown.sql  시드 전부 삭제
├─ reports/
│  └─ REPORT.md     측정 보고서 + Grafana 캡처
├─ smoke.js        1 VU 로 전 경로 1회 + 소유권 검증 — 제일 먼저 돌릴 것
└─ read.js         조회 2종 부하 (load / stress / soak)
```

---

## 0. 이 도메인의 특징

**노출된 API 가 GET 둘뿐입니다.**

| 엔드포인트 | 인증 | 소유권 |
|---|---|---|
| `GET /api/v1/rides/posts/{postId}` | 로그인만 | **검증 없음** |
| `GET /api/v1/rides/{rideId}` | 로그인만 | 공고 작성자만 (`403-81`) |

`RideController` 의 이동수단 변경(`PUT /posts/{postId}`)은 **주석 처리돼 있습니다.**
`RideService` 의 `move` / `arrive` 는 엔드포인트가 없고 application 도메인의 진행 상태 변경에서
내부 호출됩니다. 그래서 이 세트는 **전부 읽기 테스트**이고, 결제 도메인과 달리

- `reset.sql` 이 없습니다 — 테스트가 상태를 소모하지 않아 반복 실행해도 됩니다
- 동시성(race) 시나리오가 없습니다 — 경쟁할 쓰기가 없습니다

**⚠️ dev DB 의 ride 테이블은 기본적으로 비어 있습니다.** `ride` 는
`PostService.createPost` 안의 `RideService.createRide` 로만 생기는데, `BaseInitData` 는
`postRepository.save()` 로 공고를 바로 저장해 그 경로를 타지 않습니다. 시딩 없이는 테스트할
대상 자체가 없습니다.

---

## 1. 시딩

```bash
cd /Users/admin/DevCProject
set -a && . ./.env && set +a

# 건수는 seed.sql 상단의 @post_count 에서 조절 (ride 는 그 2배가 생깁니다)
docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/ride/sql/seed.sql
```

기본값은 공고 2,000건 / ride 4,000건입니다. 읽기 전용이라 행을 소모하지 않으므로
결제처럼 수만 건을 쌓을 필요가 없습니다. `direction` × `ride_status` 조합이 고르게 섞이도록
공고 id 로 값을 돌려 심습니다.

시드는 `client03`(`LT_CLIENT`)에 붙습니다. 상세 조회가 공고 작성자만 통과시키므로
부하도 이 계정으로 겁니다.

---

## 2. 실행

### 스모크 (제일 먼저)

```bash
k6 run loadtest/ride/smoke.js
```

두 조회 경로 + **소유권 검증**까지 1 VU 로 확인합니다. 체크 12개가 전부 통과해야 합니다.
여기서 깨지면 나머지는 의미가 없습니다.

### 조회 부하

```bash
k6 run loadtest/ride/read.js                          # 100 iter/s, 10분
k6 run -e STAGE=stress loadtest/ride/read.js           # 50 → 1200 iter/s 단계 증가
k6 run -e STAGE=stress -e STAGE_DURATION=30s loadtest/ride/read.js
k6 run -e STAGE=soak   loadtest/ride/read.js           # 50 iter/s, 1시간
```

1 iteration = 목록 1건 + 상세 1건 = **2 요청**입니다. `RATE=100` 이면 200 req/s 입니다.

### Grafana 로 보낼 때는 `--system-tags` 를 꼭 넘기세요

```bash
export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),max'

k6 run -o experimental-prometheus-rw --tag run=ride-load \
  --system-tags 'proto,status,method,group,check,error,error_code,scenario,expected_response' \
  -e STAGE=load -e RATE=100 -e DURATION=2m loadtest/ride/read.js
```

`url`,`name` 을 빼지 않으면 postId/rideId 마다 Prometheus 시리즈가 쪼개져
k6 처리량·백분위수 패널이 과소 집계됩니다 ([공통 README 의 계측 함정](../README.md#️-계측-함정-세-가지)).

### 정리

```bash
docker exec -i escort-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" escort < loadtest/ride/sql/teardown.sql
```

삭제 범위는 `[LT-RIDE]` 접두어 공고와 거기 달린 행뿐입니다. `BaseInitData` 데이터와
결제 시드(`[LT-PAY-...]`)는 건드리지 않습니다.

---

## 3. 이 스크립트가 기대고 있는 이동수단 도메인 동작

서버가 바뀌면 같이 손봐야 하는 지점들입니다. (인증·RsData 같은 공통 사항은 [../README.md](../README.md#1-모든-도메인이-기대고-있는-서버-동작))

**ride id 를 알아내는 경로가 없습니다** — "내 이동수단 목록" 같은 엔드포인트가 없어서
`setup()` 이 `GET /api/v1/posts/me` 로 공고를 먼저 찾고, 공고마다
`GET /api/v1/rides/posts/{postId}` 를 호출해 ride id 를 모읍니다. 그 호출이 곧 setup 비용이라
`COLLECT_POSTS`(기본 300) 로 상한을 둡니다.

**공고당 ride 는 정확히 2건** — `RideService.createRide` 가 TO_HOSPITAL / TO_HOME 한 쌍을
만듭니다. 스크립트가 "목록 2건" 으로 체크하므로, 서버가 이 모양을 바꾸면 깨집니다.

**두 경로 모두 Post 를 LAZY 로 끌어옵니다** — `RideRepository` 의 쿼리는 `join Post p` 를
쓰지만 **fetch join 이 아닙니다.** 그래서
- 목록: `RideResponse` 가 `ride.getPost()` 의 좌표·주소를 읽으며 추가 SELECT
- 상세: `RideService.validUser` 가 `ride.getPost().getClient()` 를 타며 추가 SELECT

가 나갑니다. Grafana 의 "요청 1건당 커넥션 획득 횟수" 와 서버 평균 응답시간으로 확인합니다.

**상세의 거절 코드는 `403-81`** — `ForbiddenException(81)` 이고 `ResponseAspect` 가 403 으로
세팅합니다. 404 는 `404-82`(이동 정보 없음)입니다.

**목록에는 소유권 검증이 없습니다** — `SecurityConfig` 에서 `/api/**` 가 `authenticated()` 로만
걸려 있고 `RideController.getListByPostId` 는 `@AuthenticationPrincipal` 을 받지도 않습니다.
즉 **로그인한 아무나 남의 공고 이동 정보를 볼 수 있습니다.** smoke 가 이 동작을 체크로
박아 두었으니, 서버에 검증이 추가되면 그 체크가 깨지면서 알려 줍니다.

---

## 4. 측정 결과

[reports/REPORT.md](reports/REPORT.md)
