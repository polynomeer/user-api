# user-api

와탭랩스 JAVA 코딩테스트 — `user.txt`를 기동 시 메모리에 적재하고, 조회·검색·파싱 실패 조회·재적재 REST API를 제공하는 Spring Boot 애플리케이션.

## 실행

```bash
./gradlew bootRun
```

| 항목 | 값 |
|---|---|
| 요구 환경 | JDK 21 (Gradle wrapper 포함, 별도 설치 불필요) |
| 기술 스택 | Spring Boot 4.1.1 (Web MVC, Validation), JUnit 5, AssertJ, MockMvc, JaCoCo |
| 데이터 파일 | 실행 디렉터리의 `./user.txt` |
| 경로 변경 | `USER_API_DATA_FILE_PATH=/path/to/user.txt ./gradlew bootRun` |

기동 로그에서 적재 결과를 확인할 수 있다.

```
WARN  user.txt 파싱 실패 - line=16, code=INVALID_SALARY, reason=연봉 형식이 올바르지 않습니다: 45_OOO_000, raw='1016 , 바사삭 , 38 , F , 45_OOO_000'
WARN  user.txt 파싱 실패 - line=31, code=INVALID_FIELD_COUNT, reason=필드 수가 5개가 아닙니다: 6개, raw='1031 , 일본 , 53 , F ,, 60_000_000'
WARN  user.txt 파싱 실패 - line=39, code=INVALID_SALARY, reason=연봉 형식이 올바르지 않습니다: 68_AAA_000, raw='1039 , 농심 , 61 , M , 68_AAA_000'
INFO  user.txt 적재 완료 - path=.../user.txt, success=37, failure=3
```

## API

| Method | Path | 설명 | 성공 | 에러 |
|---|---|---|---|---|
| GET | `/api/users?page=0&size=10` | 전체 목록 (페이징, ID 오름차순) | 200 | 400 |
| GET | `/api/users/{id}` | ID로 단건 조회 | 200 | 400, 404 |
| GET | `/api/users/search?name={문자열}&page=0&size=10` | 이름 부분 일치 검색 (페이징) | 200 | 400 |
| GET | `/api/users/parse-failures` | 마지막 적재의 파싱 실패 목록 | 200 | - |
| POST | `/api/users/reload` | `user.txt` 재적재 | 200 | 500 |

- `page`는 0부터, `size`는 1~100 (기본 10). 범위를 넘는 `page`는 에러가 아닌 빈 `content`
- 검색어는 앞뒤 공백을 제거하고 부분 일치로 비교. 결과가 없으면 빈 `content`

### 예시

```bash
curl -s 'localhost:8080/api/users?page=0&size=2'
```
```json
{
  "content": [
    {"id": 1001, "name": "김다나", "age": 23, "gender": "M", "salary": 30000000},
    {"id": 1002, "name": "김나나", "age": 24, "gender": "F", "salary": 31000000}
  ],
  "page": 0, "size": 2, "totalElements": 37, "totalPages": 19
}
```

```bash
curl -s localhost:8080/api/users/1018
```
```json
{"id": 1018, "name": "와탭랩스", "age": 40, "gender": "M", "salary": 47000000}
```

```bash
curl -s -G localhost:8080/api/users/search --data-urlencode 'name=파이'
```
```json
{
  "content": [
    {"id": 1011, "name": "파이리", "age": 33, "gender": "M", "salary": 40000000},
    {"id": 1013, "name": "파이추", "age": 35, "gender": "M", "salary": 42000000}
  ],
  "page": 0, "size": 10, "totalElements": 2, "totalPages": 1
}
```

```bash
curl -s localhost:8080/api/users/parse-failures
```
```json
{
  "loadedAt": "2026-10-12T01:28:18.834544Z",
  "count": 3,
  "failures": [
    {"lineNumber": 16, "rawLine": "1016 , 바사삭 , 38 , F , 45_OOO_000", "errorCode": "INVALID_SALARY", "message": "연봉 형식이 올바르지 않습니다: 45_OOO_000"},
    {"lineNumber": 31, "rawLine": "1031 , 일본 , 53 , F ,, 60_000_000", "errorCode": "INVALID_FIELD_COUNT", "message": "필드 수가 5개가 아닙니다: 6개"},
    {"lineNumber": 39, "rawLine": "1039 , 농심 , 61 , M , 68_AAA_000", "errorCode": "INVALID_SALARY", "message": "연봉 형식이 올바르지 않습니다: 68_AAA_000"}
  ]
}
```

```bash
curl -s -X POST localhost:8080/api/users/reload
```
```json
{"successCount": 37, "failureCount": 3, "loadedAt": "2026-10-12T01:46:15.710371Z"}
```

### 에러 응답

모든 에러는 [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `ProblemDetail` 형식에 `code` 필드를 더해 응답한다. `detail`은 한글이며 서버 내부 정보(파일 경로, 예외 메시지)는 포함하지 않는다.

```json
{
  "title": "Bad Request", "status": 400,
  "detail": "size는 1 이상 100 이하여야 합니다.",
  "instance": "/api/users", "code": "INVALID_PARAMETER"
}
```

| code | HTTP | 상황 |
|---|---|---|
| `INVALID_PARAMETER` | 400 | 타입 불일치(`/api/users/abc`, `page=abc`), 필수 파라미터 누락, page/size/name 범위 위반 |
| `USER_NOT_FOUND` | 404 | 없는 ID (파싱에 실패한 행의 ID 포함) |
| `NOT_FOUND` | 404 | 없는 경로 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
| `DATA_LOAD_FAILED` | 500 | 재적재 시 파일을 읽을 수 없음 (기존 데이터 유지) |
| `INTERNAL_ERROR` | 500 | 그 외 처리되지 않은 예외 |

## 데이터 처리 규칙

한 줄 형식: `ID , 이름 , 나이 , 성별 , 연봉` (UTF-8, 각 필드 앞뒤 공백 제거)

| 필드 | 규칙 | 실패 코드 |
|---|---|---|
| 전체 | 필드 정확히 5개 (빈 필드 `,,`도 1개로 셈) | `INVALID_FIELD_COUNT` |
| ID | 양의 정수 (부호 불가) | `INVALID_ID` |
| 이름 | 공백 아님 | `INVALID_NAME` |
| 나이 | 0~150 정수 | `INVALID_AGE` |
| 성별 | `M` 또는 `F` | `INVALID_GENDER` |
| 연봉 | `30000000` 또는 `30_000_000` (`_`는 3자리 단위만), int 범위 | `INVALID_SALARY` |
| (파일) ID 중복 | 먼저 나온 행 유지, 이후 행 실패 | `DUPLICATE_ID` |

- 빈 줄, 구분선(`====`), 첫 데이터 행 이전의 헤더(`ID , 이름 , ...`)는 실패가 아닌 건너뛰기. 데이터 행 이후에 `ID ,`로 시작하는 행은 `INVALID_ID` 실패
- CRLF/LF 혼재, 마지막 행 개행 없음, 파일 선두 BOM 처리
- 샘플 데이터 결과: **성공 37건, 실패 3건** (16행 연봉의 영문 `O`, 31행 빈 필드, 39행 연봉의 `AAA`)

## 설계 결정 요약

| 결정 | 이유 |
|---|---|
| 파싱 실패를 예외가 아닌 값(`ParseResult`)으로 반환 | 한 줄의 실패가 전체 적재를 멈추지 않고, 실패 사유를 그대로 모아 조회 API로 제공 |
| 잘못된 데이터를 자동 보정하지 않음 | 1031행의 `,,`를 무시하면 적재는 되지만 필드 밀림 같은 오류를 숨김 |
| 불변 스냅샷을 `AtomicReference`로 통째 교체 | Map을 비우고 다시 채우면 그 사이 조회가 빈/부분 데이터를 봄. 조회는 락 없이 항상 완전한 데이터를 봄 |
| 파일을 끝까지 읽은 뒤에만 교체 | 재적재가 실패해도 서비스 중인 데이터를 잃지 않음 |
| 재적재는 `synchronized`로 직렬화 | 동시 재적재 시 늦게 끝난 쪽이 덮어쓰는 순서 문제 방지 (조회에는 영향 없음) |
| 기동 시 파일이 없어도 빈 데이터로 기동 | 재적재 API로 복구 가능하므로 fail-fast보다 가용성 우선. 원인은 ERROR 로그 |
| 조회마다 스냅샷을 한 번만 읽음 | 재적재와 겹쳐도 `totalElements`와 `content`가 다른 데이터에서 나오지 않음 |
| `ResponseEntityExceptionHandler` 상속 | 스프링 표준 예외의 상태 코드 결정은 프레임워크에 맡기고 `code`·한글 `detail`만 채움 |
| 파서는 스프링 비의존 순수 클래스 | 컨텍스트 없이 빠르게 단위 테스트 |

명세에 없는 부분의 가정(A-1~A-10)은 [docs/01-requirements.md](docs/01-requirements.md)에 정리했다.

## 패키지 구조

```
com.whatap.userapi
├── api            UserController, dto(PageResponse, ParseFailuresResponse, ReloadResponse)
├── config         UserDataProperties (user-api.data.file-path)
├── domain         User (record)
├── exception      GlobalExceptionHandler, UserNotFoundException, DataLoadException
├── loader         UserLineParser, UserFileLoader, UserDataInitializer, ParseResult, ParseFailure, ParseErrorCode, LoadResult
├── repository     UserRepository (AtomicReference), UserSnapshot (불변)
└── service        UserService (조회·검색·재적재)
```

## 테스트

```bash
./gradlew test
```

81건, 라인 커버리지 99%. 리포트: `build/reports/jacoco/test/html/index.html`

| 테스트 | 범위 |
|---|---|
| `UserLineParserTest` | 정상 행, 오류 행 3종, 필드별 경계값 (29건) |
| `UserFileLoaderTest` | 샘플 파일 37/3건, 파싱 실패 WARN 로그, 헤더·빈 줄, 데이터 이후 헤더형 행 실패, 중복 ID, 파일 없음, CRLF·BOM, 비 UTF-8 |
| `UserControllerTest` | 목록·단건·검색·파싱 실패 API, 페이지 경계, int 오버플로 |
| `UserReloadApiTest` | 파일 변경 후 재적재 반영, 실패 시 500 + 기존 데이터 유지 |
| `ApiErrorResponseTest` | 400/404/405/406/500 응답 형식과 내부 정보 비노출 |
| `UserServiceConcurrencyTest` | 재적재 200회 동안 4개 스레드 조회 → 부분 갱신 비노출 (뮤테이션으로 검출력 확인, 한계는 테스트 계획 참고) |
| `UserDataStartupWithoutFileTest` | 파일 없이 기동 시 빈 데이터 |
| `PageResponseTest`, `UserSnapshotTest`, `UserServiceReloadTest` | 페이징 경계, 정렬·불변성, 재적재 교체·유지 |

테스트는 원본 `user.txt`를 수정하지 않고 임시 복사본을 사용한다. 상세 계획: [docs/05-test-plan.md](docs/05-test-plan.md)

## 진행 과정

작업 계획([docs/02-work-plan.md](docs/02-work-plan.md))의 단계마다 테스트 우선 작성 → 구현 → 명세 대조 검증 → 실제 기동·curl 확인 → 커밋 순으로 진행했다. 각 커밋 바디에 **변경 내용 / 변경 이유 / 설계 결정 / 검증**을 한글로 남겼다 ([커밋 규칙](docs/06-commit-convention.md)).

```bash
git log --reverse --format='%h %s'
```

## 범위 밖 (의도적으로 하지 않은 것)

- DB/JPA, 인증·인가: 과제가 힙 메모리 적재를 허용
- 파일 변경 자동 감지(WatchService): 요구사항은 "API로 갱신". 확장 시 감지 후 `UserService.reload()` 호출로 연결 가능
- 별도 응답 DTO: `User`의 필드가 요구사항과 1:1이고 숨길 필드가 없어 record를 그대로 응답

## AI 활용

**Claude Code (Claude Opus 5.5)** 를 사용했다. 요구사항·데이터 분석, 설계 문서 초안, 테스트 케이스 도출, 코드 작성 보조, 단계별 명세 검증(서브에이전트)에 활용했고, 모든 산출물은 검토 후 반영했다. 단계별 기록: [docs/AI_USAGE.md](docs/AI_USAGE.md)

## 문서

| 문서 | 내용 |
|---|---|
| [01-requirements](docs/01-requirements.md) | 요구사항 분석, 가정 |
| [02-work-plan](docs/02-work-plan.md) | 작업 계획, 제출 체크리스트 |
| [03-design](docs/03-design.md) | 패키지 구조, API 명세, 예외 처리 매트릭스 |
| [04-data-analysis](docs/04-data-analysis.md) | 샘플 데이터 분석 |
| [05-test-plan](docs/05-test-plan.md) | 테스트 계획, 보강 테스트 |
| [06-commit-convention](docs/06-commit-convention.md) | 커밋 규칙 |
| [AI_USAGE](docs/AI_USAGE.md) | AI 활용 내역 |
