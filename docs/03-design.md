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
│   ├── LoadResult.java                # record(users, failures, loadedAt)
│   └── UserDataInitializer.java       # ApplicationRunner: 기동 시 1회 적재
├── repository
│   ├── UserSnapshot.java              # 불변 스냅샷(정렬 목록, ID 맵, 실패 목록, 적재 시각)
│   └── UserRepository.java            # 스냅샷 보관/원자적 교체
├── service
│   └── UserService.java               # 페이징·검색·재적재 유스케이스
├── api
│   ├── UserController.java
│   └── dto
│       ├── PageResponse.java
│       ├── ParseFailuresResponse.java
│       └── ReloadResponse.java
└── exception
    ├── UserNotFoundException.java
    ├── DataLoadException.java
    └── GlobalExceptionHandler.java    # @RestControllerAdvice → ProblemDetail
```

**책임 분리 원칙**: 파싱(순수 로직) / 파일 I/O / 저장 / 유스케이스 / HTTP를 분리해 파서를 스프링 없이 단위 테스트한다.

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
| 없는 ID | Service | `UserNotFoundException` | 404 `USER_NOT_FOUND` |
| 경로/쿼리 타입 불일치 | Spring | `MethodArgumentTypeMismatchException` | 400 `INVALID_PARAMETER` |
| 필수 파라미터 누락 | Spring | `MissingServletRequestParameterException` | 400 `INVALID_PARAMETER` |
| page/size/name 범위 위반 | Validation | `HandlerMethodValidationException` / `ConstraintViolationException` | 400 `INVALID_PARAMETER` |
| 없는 경로 | Spring | `NoResourceFoundException` | 404 |
| 그 외 | - | ERROR 로그(스택트레이스), 내부 메시지 미노출 | 500 `INTERNAL_ERROR` |

## 7. 의도적으로 하지 않은 것 (범위 밖)

- DB/JPA, 인증/인가, 파일 변경 자동 감지(WatchService) — 요구사항은 "API로 갱신". 시간이 남으면 확장 포인트로만 언급
- Swagger — 시간 여유 시 README의 curl 예시로 대체
