# GAJI Backend Monitoring

가지(GAJI) 프로젝트의 Spring Boot 백엔드 상태와 성능을 모니터링하기 위한 환경입니다.

**Prometheus**를 사용하여 애플리케이션 메트릭을 수집하고, **Grafana** 대시보드에서 CPU, 메모리, HTTP 요청, 응답시간, 오류율, DB 커넥션 상태를 시각화합니다.

본 환경은 Windows 및 macOS의 Docker Desktop을 사용하는 로컬 개발 환경을 기준으로 구성되었습니다.

---

## 1. 모니터링 구조

```text
Spring Boot Backend
        │
        │ Actuator + Micrometer
        │ /actuator/prometheus
        ▼
    Prometheus
        │
        │ 15초마다 메트릭 수집 및 저장
        ▼
      Grafana
        │
        │ 30초마다 데이터 조회 및 갱신
        ▼
GAJI Backend Monitoring Dashboard
```

각 구성 요소의 역할은 다음과 같습니다.

| 구성 요소 | 역할 |
|---|---|
| Spring Boot Actuator | 애플리케이션 상태 및 메트릭 엔드포인트 제공 |
| Micrometer | JVM, HTTP, DB 커넥션 등 메트릭 계측 |
| Prometheus | 메트릭을 주기적으로 수집하고 시계열 데이터로 저장 |
| Grafana | Prometheus 데이터를 조회하여 대시보드로 시각화 |

Prometheus의 메트릭 수집 주기는 **15초**, Grafana 대시보드의 자동 새로고침 주기는 **30초**로 설정되어 있습니다.

---

## 2. 사전 준비

모니터링 실행 전 다음 항목을 준비합니다.

- Docker Desktop 실행
- Docker Compose 2.23.1 이상
- Spring Boot 백엔드 실행 환경
- MySQL 등 백엔드 실행에 필요한 서비스
- 프로젝트 루트의 `.env` 환경변수 설정

### 운영체제별 실행 환경

| 항목 | Windows | macOS |
|---|---|---|
| Docker | Docker Desktop | Docker Desktop |
| 터미널 | PowerShell / IntelliJ Terminal | Terminal / IntelliJ Terminal |
| Docker Compose | `docker compose` | `docker compose` |
| 환경변수 파일 | `.env` | `.env` |

Windows와 macOS에서 동일한 Docker Compose 설정 및 명령어를 사용할 수 있습니다.

Docker Compose 버전은 다음 명령어로 확인할 수 있습니다.

```bash
docker compose version
```

---

## 3. 환경변수 설정

프로젝트 루트의 `.env.example`을 참고하여 `.env` 파일에 모니터링 환경변수를 추가합니다.

기존 프로젝트에서 사용하는 MySQL 및 기타 환경변수는 그대로 유지합니다.

```dotenv
# GAJI Monitoring

# Spring Boot 실행 포트 (기본값: 8080)
BACKEND_PORT=8080

# Grafana 관리자 비밀번호
GRAFANA_ADMIN_PASSWORD=CHANGE_ME
```

`GRAFANA_ADMIN_PASSWORD`는 실제 실행 전 개인별로 안전한 비밀번호로 변경해야 합니다.

### BACKEND_PORT

`BACKEND_PORT`는 Prometheus가 데이터를 수집할 Spring Boot 서버의 포트입니다.

| Spring Boot 실행 포트 | .env 설정 |
|---|---|
| 8080 | `BACKEND_PORT=8080` |
| 9091 | `BACKEND_PORT=9091` |
| 기타 포트 | 해당 포트 번호 지정 |

예를 들어 Spring Boot를 9091 포트에서 실행했다면 다음과 같이 설정합니다.

```dotenv
BACKEND_PORT=9091
```

**주의:** `BACKEND_PORT`는 Spring Boot의 실행 포트를 변경하지 않습니다. 실제 서버 실행 포트와 동일하게 지정해야 합니다.

### Grafana 관리자 비밀번호

Grafana 로그인 계정은 다음과 같습니다.

- ID: `admin`
- Password: `.env`에 설정한 `GRAFANA_ADMIN_PASSWORD`

관리자 비밀번호는 팀원 각자가 설정할 수 있으며, 공통 비밀번호를 공유할 필요는 없습니다.

**보안 주의사항**

- `.env` 파일은 Git에 커밋하지 않습니다.
- `.env.example`에는 실제 비밀번호를 작성하지 않습니다.
- 이미 생성된 Grafana 데이터 볼륨이 있다면 관리자 비밀번호 환경변수 변경만으로 기존 로그인 비밀번호가 바뀌지 않을 수 있습니다.

---

## 4. 모니터링 실행

### 4.1. Spring Boot 백엔드 실행

먼저 MySQL 등 백엔드에 필요한 서비스를 실행한 후 Spring Boot 애플리케이션을 실행합니다.

백엔드는 각자의 로컬 개발 환경에서 실행합니다.

Spring Boot를 실행한 다음 아래 주소에 접속하여 메트릭이 출력되는지 확인합니다.

```text
http://localhost:8080/actuator/prometheus
```

8080 대신 다른 포트를 사용한다면 실제 실행 포트로 변경합니다.

메트릭 텍스트가 출력되면 정상입니다.

### 4.2. Prometheus 및 Grafana 실행

프로젝트 루트에서 다음 명령어를 실행합니다.

```bash
docker compose --env-file .env -f docker-compose.monitoring.yml up -d
```

실행 시 다음 컨테이너가 생성 또는 실행됩니다.

- `gaji-prometheus`
- `gaji-grafana`

컨테이너 상태를 확인하려면 다음 명령어를 사용합니다.

```bash
docker compose --env-file .env -f docker-compose.monitoring.yml ps
```

### 4.3. 접속 주소

| 서비스 | 주소 |
|---|---|
| Spring Boot Metrics | http://localhost:8080/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Prometheus Targets | http://localhost:9090/targets |
| Grafana | http://localhost:3001 |

Spring Boot Metrics 주소는 각자의 실행 포트에 맞게 변경합니다.

### 4.4. Prometheus 수집 상태 확인

브라우저에서 다음 주소로 접속합니다.

http://localhost:9090/targets

`gaji-backend`의 상태를 확인합니다.

- **UP:** Spring Boot 메트릭 수집 정상
- **DOWN:** Spring Boot 연결 실패
- **UNKNOWN:** 아직 수집 상태가 확인되지 않음

정상적으로 연결되었다면 `1 / 1 up`으로 표시됩니다.

### 4.5. Grafana 대시보드 확인

브라우저에서 다음 주소로 접속합니다.

http://localhost:3001

Grafana 로그인 후 다음 경로로 이동합니다.

**Dashboards → GAJI → GAJI Backend Monitoring**

Grafana 데이터 소스와 대시보드는 Provisioning 설정을 통해 자동 등록됩니다.

따라서 데이터 소스 및 6개 모니터링 패널을 수동으로 생성할 필요가 없습니다.

---

## 5. 모니터링 지표

| 지표 | 설명 |
|---|---|
| Backend CPU Usage | Spring Boot 프로세스의 CPU 사용률 |
| JVM Heap Memory Usage | JVM 힙 메모리 사용량 |
| HTTP Request Rate | 초당 HTTP 요청 수 |
| API Average Response Time | 최근 5분간 HTTP 평균 응답시간 |
| HTTP Error Rate | HTTP 요청 중 4xx 및 5xx 응답 비율 |
| DB Connection Pool | HikariCP의 Active, Idle, Max 커넥션 수 |

### Backend CPU Usage

Spring Boot 프로세스의 CPU 사용률을 확인합니다.

CPU 사용률이 지속적으로 높게 유지된다면 요청 처리량 증가나 CPU를 많이 사용하는 작업이 발생했는지 확인할 수 있습니다.

### JVM Heap Memory Usage

JVM에서 사용 중인 힙 메모리를 확인합니다.

가비지 컬렉션(GC)이 실행되면 사용량이 감소할 수 있습니다.

메모리 사용량의 증가 추세와 GC 이후 회수되는 양을 관찰할 수 있습니다.

### HTTP Request Rate

최근 5분간의 초당 평균 HTTP 요청 수를 확인합니다.

`/actuator` 메트릭 관련 요청은 집계에서 제외합니다.

### API Average Response Time

최근 5분간 발생한 HTTP 요청의 평균 응답시간을 확인합니다.

응답시간이 증가하면 처리 지연 여부를 파악하는 데 활용할 수 있습니다.

### HTTP Error Rate

최근 5분간의 HTTP 요청 중 4xx 및 5xx 응답 비율을 표시합니다.

- 4xx: 클라이언트 요청 관련 오류
- 5xx: 서버 내부 처리 관련 오류

4xx에는 인증 실패나 잘못된 요청 등 예상 가능한 응답도 포함되므로, 오류율이 높다고 해서 반드시 서버 장애를 의미하는 것은 아닙니다.

### DB Connection Pool

HikariCP의 데이터베이스 연결 상태를 확인합니다.

| 지표 | 설명 |
|---|---|
| Active | 현재 사용 중인 DB 연결 수 |
| Idle | 사용 가능한 대기 연결 수 |
| Max | 커넥션 풀의 최대 연결 수 |

Active 연결 수가 지속적으로 Max에 가까워진다면 DB 커넥션 부족이나 반환 지연 여부를 확인할 수 있습니다.

**참고:** 실제 HTTP 요청이 없으면 HTTP 관련 패널은 `No data`로 표시될 수 있습니다.

---

## 6. 설정 파일 구조

```text
NBE-12-14-2-Team06/
├── .env
├── .env.example
├── docker-compose.yml
├── docker-compose.monitoring.yml
└── monitoring/
    ├── README.md
    └── grafana/
        ├── dashboards/
        │   └── gaji-backend-monitoring.json
        └── provisioning/
            ├── dashboards/
            │   └── dashboards.yml
            └── datasources/
                └── datasources.yml
```

각 파일의 역할은 다음과 같습니다.

| 파일 | 역할 |
|---|---|
| `docker-compose.monitoring.yml` | Prometheus 및 Grafana 실행과 메트릭 수집 설정 |
| `gaji-backend-monitoring.json` | Grafana 대시보드 6개 패널의 쿼리 및 레이아웃 정의 |
| `datasources.yml` | Prometheus 데이터 소스 자동 등록 |
| `dashboards.yml` | Grafana 대시보드 자동 등록 |
| `.env.example` | 환경변수 설정 예시 |
| `.env` | 실제 실행 환경변수 및 비밀번호 저장 (Git 제외) |

Prometheus 수집 대상 주소는 Docker Compose 설정의 `BACKEND_PORT` 환경변수를 통해 지정합니다.

Grafana는 공통 데이터 소스 UID인 `gaji-prometheus`를 사용합니다.

---

## 7. 문제 해결

### 7.1. Prometheus Targets가 DOWN인 경우

다음 항목을 순서대로 확인합니다.

1. Spring Boot 백엔드가 실행 중인지 확인합니다.
2. `.env`의 `BACKEND_PORT`가 실제 실행 포트와 일치하는지 확인합니다.
3. `http://localhost:<실제포트>/actuator/prometheus`에 접속되는지 확인합니다.
4. Docker Desktop이 실행 중인지 확인합니다.
5. Docker Compose 설정을 다시 적용합니다.

```bash
docker compose --env-file .env -f docker-compose.monitoring.yml up -d
```

Prometheus는 `host.docker.internal`을 통해 호스트 컴퓨터에서 실행 중인 Spring Boot에 접근합니다.

### 7.2. Grafana에 접속할 수 없는 경우

컨테이너 상태를 확인합니다.

```bash
docker ps -a --filter "name=gaji-grafana"
```

Grafana 로그를 확인합니다.

```bash
docker logs gaji-grafana --tail 100
```

`Restarting` 상태가 반복된다면 Grafana 설정 파일 및 로그의 오류 메시지를 확인합니다.

### 7.3. Grafana에 No data가 표시되는 경우

- Prometheus Targets가 `UP`인지 확인합니다.
- Spring Boot 백엔드가 실행 중인지 확인합니다.
- HTTP 관련 패널은 실제 API 요청이 발생했는지 확인합니다.
- Grafana 대시보드 조회 기간을 확인합니다.
- 데이터 소스가 `gaji-prometheus`로 연결되어 있는지 확인합니다.

### 7.4. Grafana 데이터 소스 연결 확인

Grafana에서 다음 경로로 이동합니다.

**Connections → Data sources → gaji-prometheus**

**Save & test**를 클릭합니다.

다음 메시지가 표시되면 연결이 정상입니다.

```text
Successfully queried the Prometheus API.
```

### 7.5. 환경변수가 적용되지 않는 경우

프로젝트 루트에서 Docker Compose 설정을 확인합니다.

```bash
docker compose --env-file .env -f docker-compose.monitoring.yml config
```

출력된 Prometheus 설정의 수집 대상 포트가 `BACKEND_PORT`와 일치하는지 확인합니다.

**주의:** 이 명령어의 출력에는 환경변수의 실제 비밀번호가 포함될 수 있으므로 로그 공유 시 민감한 정보를 제거해야 합니다.

---

## 8. 모니터링 종료 및 재실행

### 종료

프로젝트 루트에서 다음 명령어를 실행합니다.

```bash
docker compose --env-file .env -f docker-compose.monitoring.yml down
```

이 명령어는 모니터링 Compose에 정의된 컨테이너와 네트워크를 종료 및 제거합니다. 데이터 볼륨은 유지됩니다.

### 재실행

```bash
docker compose --env-file .env -f docker-compose.monitoring.yml up -d
```

### 주의사항

- `down -v` 옵션을 사용하면 저장된 Prometheus 및 Grafana 볼륨 데이터가 삭제될 수 있습니다.
- MySQL은 별도의 Compose 설정으로 관리하고 있으므로, 모니터링 종료 시 MySQL 컨테이너를 함께 삭제하지 않습니다.
- `--remove-orphans` 옵션은 다른 Compose 설정으로 실행 중인 MySQL 컨테이너에 영향을 줄 수 있으므로 사용하지 않습니다.

---

## 9. 참고사항 및 제한사항

- 본 설정은 **Windows 및 macOS Docker Desktop 기반 로컬 개발 환경**을 위한 구성입니다.
- 각 팀원은 자신의 컴퓨터에서 실행 중인 Spring Boot 백엔드를 모니터링합니다.
- 팀원끼리 동일한 대시보드 구성을 사용하지만, 모니터링 데이터는 각자의 로컬 Prometheus에 저장됩니다.
- 현재 모니터링 대상은 실제 배포 서버가 아닌 로컬 Spring Boot 애플리케이션입니다.
- HTTP 관련 메트릭은 실제 요청 발생 여부 및 조회 기간에 따라 표시되지 않을 수 있습니다.
- 배포 환경에서 사용할 경우 메트릭 엔드포인트 접근 제한, 인증 및 네트워크 보안 설정을 별도로 검토해야 합니다.