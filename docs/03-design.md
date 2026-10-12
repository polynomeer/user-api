# 03. 설계

## 1. 기술 스택

| 항목 | 선택 | 이유 |
|---|---|---|
| Language | Java 21 | 로컬 JDK(Corretto 21). `record` 사용 |
| Framework | Spring Boot 4.1.1 (`starter-webmvc`, `starter-validation`) | 과제 요구. start.spring.io 기본 stable. Actuator는 생성 서버 오류로 제외(필수 아님) |
| Build | Gradle (Groovy DSL, wrapper 포함) | |
| Test | JUnit 5, AssertJ, MockMvc | |
| 저장소 | 인메모리 (불변 스냅샷 + `AtomicReference`) | 과제가 힙 메모리 허용 |

> Lombok은 사용하지 않는다. `record`로 충분하고, 인터뷰에서 설명할 코드를 줄인다.

## 2. 패키지 구조

```
com.whatap.userapi
├── UserApiApplication.java
├── config
│   └── UserDataProperties.java        # user-api.data.file-path 바인딩
├── domain
│   └── User.java                      # record(id, name, age, gender, salary)
├── loader
│   ├── UserLineParser.java            # 한 줄 → ParseResult (순수 함수, 스프링 비의존)
│   ├── ParseResult.java               # sealed: Success(User) | Failure(ParseFailure)
│   ├── ParseFailure.java              # record(lineNumber, rawLine, errorCode, message)
│   ├── ParseErrorCode.java            # enum
│   ├── UserFileLoader.java            # 파일 → LoadResult (I/O, 중복검사, 로그)
│   └── LoadResult.java                # record(users, failures, loadedAt)
├── repository
│   ├── UserSnapshot.java              # 불변 스냅샷(정렬 목록, ID 맵, 실패 목록, 적재 시각)
│   └── UserRepository.java            # 스냅샷 보관/원자적 교체
├── service
│   ├── UserService.java               # 페이징·검색·재적재 유스케이스
│   └── PageResult.java                # 페이지 결과(content, page, size, totalElements, totalPages)
├── startup
│   └── UserDataInitializer.java       # ApplicationRunner: 기동 시 1회 적재
├── api
│   ├── UserController.java
│   ├── dto
│   │   ├── ParseFailuresResponse.java
│   │   └── ReloadResponse.java
│   └── error
│       └── GlobalExceptionHandler.java  # @RestControllerAdvice → ProblemDetail (HTTP 경계)
└── exception                          # 계층 공통 예외 (웹 의존 없음)
    ├── UserNotFoundException.java     # service에서 던짐
    └── DataLoadException.java         # loader에서 던짐
```

**책임 분리 원칙**: 파싱(순수 로직) / 파일 I/O / 저장 / 유스케이스 / HTTP를 분리해 파서를 스프링 없이 단위 테스트한다.

**의존 방향** (패키지 순환 없음): `api → service → loader·repository → domain`, `startup → service`, `loader·service·api → exception`. 예외 타입은 웹 의존이 없는 `exception`에, HTTP 변환(`GlobalExceptionHandler`)은 `api.error`에 두어 인프라 계층(`loader`)이 웹 계층 클래스와 같은 패키지에 의존하지 않게 했다. 기동 훅(`startup`)을 `loader`와 분리해 `loader ↔ service` 순환을 없앴고, 페이지 결과 타입을 `service`에 두어 서비스가 API DTO에 의존하지 않는다.

## 3. 데이터 흐름

```
[기동] UserDataInitializer ─┐
                            ├─▶ UserService.reload() ─▶ UserFileLoader.load(path)
[POST /reload] Controller ──┘          │                     │ 줄 단위 UserLineParser.parse()
                                       │                     │ 실패 → WARN 로그 + failures 수집
                                       │                     ▼
                                       │               LoadResult(users, failures)
                                       ▼
                         UserRepository.replace(snapshot)   ← AtomicReference.set (원자적 교체)
```

## 4. 핵심 설계

### 4.1 파서 (`UserLineParser`)
- 입력: `lineNumber`, `rawLine` / 출력: `ParseResult` (예외를 흐름 제어에 쓰지 않음. 내부 `NumberFormatException`은 잡아서 Failure로 변환)
- 처리 순서: BOM 제거 → `split(",", -1)` → 필드 수 5 검증 → 각 필드 trim → 필드별 검증
- 검증 규칙

| 필드 | 규칙 | 에러 코드 |
|---|---|---|
| 필드 수 | 정확히 5 | `INVALID_FIELD_COUNT` |
| ID | 양의 정수(int) | `INVALID_ID` |
| 이름 | 비어있지 않음 | `INVALID_NAME` |
| 나이 | 정수, 0~150 | `INVALID_AGE` |
| 성별 | `M` 또는 `F` | `INVALID_GENDER` |
| 연봉 | `^\d{1,3}(_\d{3})*$` 또는 `^\d+$`, `_` 제거 후 int 범위 0 이상 | `INVALID_SALARY` |
| (로더) ID 중복 | 먼저 나온 행 우선 | `DUPLICATE_ID` |

### 4.2 로더 (`UserFileLoader`)
- `Files.newBufferedReader(path, UTF_8)`로 줄 단위 읽기 (CRLF 대응)
- 빈 줄 / 헤더 / 구분선 skip
- 실패마다 `log.warn("[user.txt 파싱 실패] line={}, code={}, raw='{}', reason={}")`
- 완료 시 `log.info("적재 완료: success={}, failure={}")`
- 파일 없음·읽기 실패 → `DataLoadException`(cause 포함) 던짐 → **스냅샷 교체 안 함**
- `MalformedInputException`(UTF-8 아님)도 `DataLoadException`으로 변환

### 4.3 저장소 (`UserRepository`)
```java
private final AtomicReference<UserSnapshot> snapshot = new AtomicReference<>(UserSnapshot.empty());
record UserSnapshot(List<User> usersSortedById, Map<Integer, User> usersById, List<ParseFailure> failures, Instant loadedAt) {}
```
- 스냅샷은 생성 시 `Map.copyOf`/`List.copyOf`로 **불변**. 조회는 락 없이 `snapshot.get()` 한 번만 읽고 그 스냅샷으로 끝까지 처리 → 재적재 중에도 일관된 결과 (NFR-3)
- `UserService.reload()`는 `synchronized` (동시 재적재 직렬화, 파일 중복 읽기 방지)

### 4.4 설정
```yaml
user-api:
  data:
    file-path: ./user.txt   # 실행 디렉터리 기준. 환경변수 USER_API_DATA_FILE_PATH로 재정의 가능
```

## 5. API 명세

공통: `Content-Type: application/json`, 에러는 RFC 9457 `ProblemDetail` + `code` 확장 필드.

| # | Method | Path | 설명 | 성공 | 에러 |
|---|---|---|---|---|---|
| 1 | GET | `/api/users?page=0&size=10` | 전체 목록 페이징 (ID 오름차순) | 200 | 400 (page<0, size∉[1,100], 숫자 아님) |
| 2 | GET | `/api/users/{id}` | ID 단건 조회 | 200 | 400 (숫자 아님), 404 (없음) |
| 3 | GET | `/api/users/search?name=김&page=0&size=10` | 이름 부분 일치 검색 (페이징) | 200 (결과 없으면 빈 content) | 400 (name 누락/공백) |
| 4 | GET | `/api/users/parse-failures` | 마지막 적재의 파싱 실패 목록 | 200 | - |
| 5 | POST | `/api/users/reload` | 파일 재적재 | 200 | 500 (`DATA_LOAD_FAILED`, 기존 데이터 유지) |

> `/search`, `/parse-failures`는 리터럴 경로라 `/{id}` 패턴보다 우선 매칭된다. `{id}`는 `int` 바인딩 → 타입 불일치 시 400.

### 응답 예시

**1. 목록**
```json
{
  "content": [{"id":1001,"name":"김다나","age":23,"gender":"M","salary":30000000}],
  "page": 0, "size": 10, "totalElements": 37, "totalPages": 4
}
```
범위를 넘는 page는 에러가 아닌 빈 `content` 반환.

**4. 파싱 실패**
```json
{
  "loadedAt": "2026-10-12T10:30:00Z",
  "count": 3,
  "failures": [
    {"lineNumber":16,"rawLine":"1016 , 바사삭 , 38 , F , 45_OOO_000","errorCode":"INVALID_SALARY","message":"연봉 형식이 올바르지 않습니다: 45_OOO_000"}
  ]
}
```

**5. 재적재**
```json
{ "successCount": 37, "failureCount": 3, "loadedAt": "2026-10-12T10:31:00Z" }
```

**에러 (404)**
```json
{
  "type": "about:blank", "title": "Not Found", "status": 404,
  "detail": "ID 9999에 해당하는 사용자가 없습니다.",
  "instance": "/api/users/9999", "code": "USER_NOT_FOUND"
}
```

## 6. 예외 처리 매트릭스

| 상황 | 발생 위치 | 처리 | HTTP |
|---|---|---|---|
| 줄 파싱 실패 | Parser | Failure 수집 + WARN 로그, 적재 계속 | - |
| 파일 없음/권한 없음/인코딩 오류 (기동 시) | Loader | ERROR 로그, 빈 데이터로 기동 (A-8) | - |
| 파일 없음/읽기 실패 (재적재) | Loader | `DataLoadException`, 기존 스냅샷 유지 | 500 `DATA_LOAD_FAILED` |
| 빈 파일·헤더만 있는 파일·쓰는 중인 파일 (재적재) | Loader | **예외 아님.** 읽은 결과(0건 또는 일부)로 스냅샷 교체 (6.1 참조) | 200 |
| 기동 시 파일 외 예외 (파서 버그, 잘못된 `file-path` 등) | Initializer/Service | 잡지 않음 → **기동 실패** (fail-fast, 6.1 참조) | - |
| 없는 ID | Service | `UserNotFoundException` | 404 `USER_NOT_FOUND` |
| 경로/쿼리 타입 불일치 | Spring | `MethodArgumentTypeMismatchException` | 400 `INVALID_PARAMETER` |
| 필수 파라미터 누락 | Spring | `MissingServletRequestParameterException` | 400 `INVALID_PARAMETER` |
| page/size/name 범위 위반 | Validation | `HandlerMethodValidationException` (Spring 6.1+ 내장 메서드 검증. 메시지는 컨트롤러 제약 선언에 한글로 정의) | 400 `INVALID_PARAMETER` |
| 없는 경로 | Spring | `NoResourceFoundException` | 404 `NOT_FOUND` |
| 지원하지 않는 메서드 | Spring | `HttpRequestMethodNotSupportedException` | 405 `METHOD_NOT_ALLOWED` |
| 그 외 스프링 표준 예외 (415, 406 등) | Spring | `ResponseEntityExceptionHandler` 기본 처리 + `code`에 상태 이름 | 4xx `<HttpStatus 이름>` |
| 그 외 | - | ERROR 로그(스택트레이스), 내부 메시지 미노출 | 500 `INTERNAL_ERROR` |

> 구현: `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를 상속해 스프링 표준 예외의 상태 코드 결정은 위임하고, 한글 `detail`과 `code`만 채운다.

### 6.1 예외 처리 경계와 알려진 동작

**(1) 재적재 실패의 기준은 "파일을 읽을 수 없음"뿐이다.** 빈 파일, 헤더만 있는 파일은 `IOException`이 아니므로 `success=0` 스냅샷으로 교체되고 200을 반환한다. 다른 프로세스가 파일을 쓰는 도중에 재적재하면 그 시점까지 쓰인 행만 적재된다.
- 결정: 현재 동작 유지. "0건"이 유효한 데이터 상태일 수 있고, 행 수 하한 같은 정책은 요구사항에 근거가 없다. 응답의 `successCount`·`failureCount`로 결과를 바로 확인할 수 있다
- 운영 시 보완: 파일은 임시 파일에 쓴 뒤 rename으로 원자적 교체한다(쓰기 측 규약). 필요하면 "성공 0건이면 교체 거부"(`EMPTY_DATA` 등) 가드 추가 (07 문서 3.5)

**(2) 기동 시 빈 데이터로 계속 진행하는 것은 `DataLoadException`(파일 문제)만이다.** `UserDataInitializer`는 `DataLoadException`만 잡는다.
- 파서·스냅샷 생성 중의 다른 `RuntimeException`은 코드 버그이므로 잡지 않고 기동을 실패시킨다. 버그를 빈 데이터로 덮어 조용히 서비스하지 않기 위함
- 잘못된 `file-path`(`InvalidPathException`)는 `UserService` 생성 시점에 기동 실패. 설정 오류도 fail-fast

**(3) 적재 실패 상태 노출.** 기동 시 적재에 실패하면 `/api/users`는 200 빈 목록이라 "0건"과 구별되지 않는다. 대신 `GET /api/users/parse-failures`의 `loadedAt`이 `null`이면 한 번도 적재에 성공하지 못한 상태다. 운영에서는 헬스체크로 노출한다 (8.3)

**(4) `DATA_LOAD_FAILED`의 상태 코드는 500.** 원인(파일 없음·권한·인코딩)은 클라이언트 요청이 아닌 서버 측 문제이고, 일시적이라는 보장이 없다. 503은 "잠시 후 재시도"를 뜻하므로 재시도로 해결되지 않는 설정 오류에는 맞지 않는다. 또 핸들러는 `DataLoadException`이 HTTP로 전파되는 경로가 재적재뿐이라는 전제로 "기존 데이터 유지"를 안내한다. 다른 경로에서 던지게 되면 문구를 바꿔야 한다

**(5) 알려진 사소한 동작 (변경하지 않음)**
- `GET /api/users/reload`는 405가 아니라 400 `INVALID_PARAMETER`다. GET 매핑 `/{id}`에 매칭되어 `id='reload'` 타입 불일치가 되기 때문이다. 재적재를 `/admin` 경로로 분리하면 해소된다 (8.3)
- `DispatcherServlet` 밖(서블릿 필터 등)에서 난 오류는 Spring Boot 기본 `/error` 응답 형식이다. 현재 커스텀 필터가 없어 해당 사항이 없다
- `UserNotFoundException`은 404마다 스택트레이스를 생성한다. 요청량에 비해 비용이 무시할 수준이라 최적화하지 않는다

## 7. 의도적으로 하지 않은 것 (범위 밖)

- DB/JPA, 인증/인가, 파일 변경 자동 감지(WatchService) — 요구사항은 "API로 갱신". 시간이 남으면 확장 포인트로만 언급
- Swagger — 시간 여유 시 README의 curl 예시로 대체

## 8. 운영 확장 설계 (구현하지 않음)

과제 범위에서는 2절의 계층형 구조로 충분하다. 실제 운영으로 가면 바뀌어야 할 지점과 목표 구조를 정리한다. **시간 제약상 코드에는 반영하지 않았다.**

### 8.1 현재 구조의 한계

| # | 한계 | 운영 시 문제 |
|---|---|---|
| 1 | `UserService`가 `UserFileLoader`(구체 클래스)에 직접 의존 | 데이터 출처가 S3·DB·외부 API로 바뀌면 서비스 수정 필요 |
| 2 | `loader` 패키지에 순수 파싱(도메인 규칙)과 파일 I/O·`ApplicationRunner`(인프라)가 공존 | "파서는 스프링 비의존" 원칙이 패키지 경계로 드러나지 않음 |
| 3 | 저장소가 단일 구현 클래스 | 다중 인스턴스·외부 캐시로 전환할 교체 지점이 없음 |
| 4 | `POST /reload`에 인증 없음 | 누구나 재적재를 호출해 부하·장애 유발 가능 |
| 5 | 인스턴스별 인메모리 적재 | 다중 인스턴스에서 reload가 한 대에만 반영 → 인스턴스 간 데이터 불일치 |
| 6 | 헬스체크·메트릭 없음 (Actuator 제외) | 적재 실패·파싱 실패율을 모니터링할 수 없음 |

### 8.2 목표 구조 (포트-어댑터)

스프링 비의존 도메인을 중심에 두고, 데이터 출처와 저장소를 포트(인터페이스) 뒤로 숨긴다. 의존 방향은 `web`·`infrastructure` → `application` → `domain` 한 방향이다.

```
com.whatap.userapi
├── domain                      # 순수 Java, 스프링 비의존
│   ├── User, UserSnapshot
│   └── parse/                  # UserLineParser, ParseResult, ParseFailure, ParseErrorCode
├── application                 # 유스케이스 + 포트
│   ├── UserQueryService        # 조회·검색·페이징
│   ├── UserReloadService       # 재적재 (단일 실행 보장)
│   └── port/
│       ├── UserDataSource      # 원천 행 스트림 제공 → 파일/S3/HTTP 구현 교체
│       └── UserSnapshotStore   # 스냅샷 보관 → 인메모리/외부 저장소 교체
├── infrastructure              # 포트 구현(어댑터)
│   ├── file/FileUserDataSource
│   ├── memory/InMemorySnapshotStore   # AtomicReference 기반 (현 UserRepository)
│   └── startup/UserDataInitializer
└── web                         # HTTP 경계
    ├── UserController
    ├── AdminController         # /admin/reload (인증 대상 분리)
    ├── dto/
    └── error/GlobalExceptionHandler
```

현재 클래스와의 대응: `loader.UserLineParser` 등 → `domain.parse`, `UserFileLoader` → `FileUserDataSource` + 적재 로직은 `UserReloadService`, `UserRepository` → `InMemorySnapshotStore`, `UserService` → 조회/재적재 서비스로 분리.

### 8.3 운영 확장 포인트

| 관심사 | 방안 |
|---|---|
| 데이터 출처 교체 | `UserDataSource` 구현 추가만으로 대응. 유스케이스·파서 변경 없음 |
| 다중 인스턴스 | (a) reload 이벤트를 메시지(Redis pub/sub 등)로 전 인스턴스에 전파하거나, (b) 공유 저장소 기반 `UserSnapshotStore`로 전환. 조회 빈도가 높고 데이터가 작으면 (a)가 유리 |
| 보안 | reload를 `/admin` 경로로 분리하고 인증 적용. 재적재 진행 중 중복 요청은 대기 대신 409로 거절(`tryLock`) |
| 관측성 | Actuator 헬스체크에 적재 상태(마지막 성공 시각, 실패 여부) 포함, `user_parse_failures`·`user_reload_duration` 메트릭 노출 |
| 대용량 파일 | 현재도 줄 단위 스트리밍. 파일 크기·행 수 상한을 두고 초과 시 적재 실패로 처리해 메모리 보호 |
| 자동 갱신 | 필요 시 WatchService 또는 주기 폴링(파일 수정 시각·해시 비교)으로 reload 트리거. 수동 API는 유지 |
