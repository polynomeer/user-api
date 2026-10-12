# user-api

> JAVA 코딩테스트 과제 — `user.txt`를 기동 시 메모리에 적재하고 **목록·단건·검색·파싱 실패 조회·재적재** REST API를 제공하는 Spring Boot 애플리케이션

## 한눈에 보기

| 항목 | 결과 |
|---|---|
| 기능 | 과제의 API 5종 모두 구현 ([요구사항 대응표](#요구사항-대응표)) |
| 샘플 데이터 | **성공 37건 / 실패 3건** — 실패 행은 WARN 로그로 남기고 API로 따로 조회할 수 있음 |
| 에러 처리 | 모든 에러가 같은 형식(`ProblemDetail` + `code` + 한글 `detail`). 재적재가 실패해도 **기존 데이터 유지** |
| 동시성 | 재적재 중 조회해도 **반쯤 갱신된 데이터가 보이지 않음** (불변 스냅샷 통째 교체) |
| 테스트 | **91건 통과**, 라인 커버리지 99% (195/197), 분기 89% (50/56) |
| 환경 | JDK 21, Spring Boot 4.1.1 (Web MVC, Validation) — Gradle wrapper 포함 |

## 빠른 시작

```bash
./gradlew bootRun                                         # 실행 (작업 디렉터리의 ./user.txt 적재)
curl -s 'localhost:8080/api/users?page=0&size=2'          # 목록 조회
./gradlew test                                            # 테스트 91건
```

다른 위치의 데이터 파일을 쓰려면 환경변수로 경로를 바꾼다: `USER_API_DATA_FILE_PATH=/path/to/user.txt ./gradlew bootRun`

기동하면 적재 결과가 로그로 출력됩니다.

```
WARN  user.txt 파싱 실패 - line=16, code=INVALID_SALARY, reason=연봉 형식이 올바르지 않습니다: 45_OOO_000, raw='1016 , 바사삭 , 38 , F , 45_OOO_000'
WARN  user.txt 파싱 실패 - line=31, code=INVALID_FIELD_COUNT, reason=필드 수가 5개가 아닙니다: 6개, raw='1031 , 일본 , 53 , F ,, 60_000_000'
WARN  user.txt 파싱 실패 - line=39, code=INVALID_SALARY, reason=연봉 형식이 올바르지 않습니다: 68_AAA_000, raw='1039 , 농심 , 61 , M , 68_AAA_000'
INFO  user.txt 적재 완료 - path=.../user.txt, success=37, failure=3
```

## 요구사항 대응표

과제의 기능 설명이 어디서 구현되고 어떤 테스트로 증명되는지 정리했습니다.

| 과제 요구사항 | 구현 | 검증 테스트 |
|---|---|---|
| 기동 시 `user.txt` → User 변환 후 메모리 적재 | `UserDataInitializer` → `UserService.reload()` | `UserFileLoaderTest`, `UserApiIntegrationTest` |
| 파싱 중 에러가 난 부분은 로그를 남김 | `UserFileLoader` (행마다 WARN, 끝나면 INFO 요약) | `UserFileLoaderTest#logsParseFailures` |
| User 클래스 (ID·나이·연봉은 정수, 이름·성별은 문자열) | `domain.User` record | `UserControllerTest#userJsonFieldTypesMatchSpec` |
| 전체 목록을 페이지 단위로 조회 | `GET /api/users` | `UserControllerTest`, `PageResultTest` |
| ID로 특정 User 조회 | `GET /api/users/{id}` | `UserControllerTest`, `ApiErrorResponseTest` |
| 이름에 포함된 문자열로 검색 | `GET /api/users/search` | `UserControllerTest`, `UserApiIntegrationTest` (한글 인코딩) |
| 파싱에 실패한 데이터를 따로 조회 | `GET /api/users/parse-failures` | `UserControllerTest` |
| 실행 중 바뀐 파일을 다시 읽어 갱신 | `POST /api/users/reload` | `UserReloadApiTest`, `UserApiIntegrationTest`, `UserServiceConcurrencyTest` |

명세에 없는 부분은 가정(A-1~A-10)을 세워 정했습니다 → [docs/01-requirements.md](docs/01-requirements.md)

## API

| Method | Path | 설명 | 성공 | 에러 |
|---|---|---|---|---|
| GET | `/api/users?page=0&size=10` | 전체 목록 (페이징, ID 오름차순) | 200 | 400 |
| GET | `/api/users/{id}` | ID로 단건 조회 | 200 | 400, 404 |
| GET | `/api/users/search?name={문자열}&page=0&size=10` | 이름 부분 일치 검색 (페이징) | 200 | 400 |
| GET | `/api/users/parse-failures` | 마지막 적재의 파싱 실패 목록 | 200 | - |
| POST | `/api/users/reload` | `user.txt` 재적재 | 200 | 500 |

- `page`는 0부터, `size`는 1~100 (기본 10). 범위를 넘는 `page`는 에러가 아닌 빈 `content`를 반환합니다
- 검색어는 앞뒤 공백을 제거한 뒤 부분 일치로 비교합니다. 결과가 없으면 빈 `content`를 반환합니다

<details>
<summary><b>요청·응답 예시</b> (실제 기동한 앱의 응답)</summary>

**목록**
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

**단건**
```bash
curl -s localhost:8080/api/users/1017
```
```json
{"id": 1017, "name": "치킨", "age": 39, "gender": "M", "salary": 46000000}
```

**검색**
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

**파싱 실패 조회**
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

**재적재**
```bash
curl -s -X POST localhost:8080/api/users/reload
```
```json
{"successCount": 37, "failureCount": 3, "loadedAt": "2026-10-12T01:46:15.710371Z"}
```

</details>

## 에러 처리

에러를 **데이터 파일 → 요청 → 예상하지 못한 예외** 세 단계로 나눠, 단계마다 "서비스를 멈추지 않는다"는 원칙으로 처리했습니다.

| 단계 | 상황 | 처리 | 결과 |
|---|---|---|---|
| 데이터 파일 | 잘못된 행 | 그 행만 실패로 기록하고 적재는 계속 진행. 행마다 WARN 로그 | 정상 행은 서비스, 실패 행은 `/parse-failures`로 조회 |
| | 기동 시 파일 없음·읽기 실패 | ERROR 로그 후 빈 데이터로 기동 | 파일을 고친 뒤 재적재 API로 복구 |
| | 재적재 시 파일 없음·읽기 실패 | 새 데이터로 교체하지 않음 | 500 `DATA_LOAD_FAILED`, **기존 데이터 유지** |
| 요청 | 잘못된 파라미터·경로·메서드 | Bean Validation과 스프링 표준 예외를 한곳에서 변환 | 400 / 404 / 405 + `code` |
| | 없는 ID | `UserNotFoundException` | 404 `USER_NOT_FOUND` |
| 예상하지 못한 예외 | 그 외 | 스택트레이스는 로그에만 남김 | 500 `INTERNAL_ERROR`, 내부 메시지 미노출 |

모든 에러 응답은 [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `ProblemDetail` 형식에 `code` 필드를 더한 것입니다. `detail`은 한글이며, 서버 내부 정보(파일 경로, 예외 메시지)는 담지 않습니다.

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

한 줄 형식은 `ID , 이름 , 나이 , 성별 , 연봉`입니다 (UTF-8, 각 필드 앞뒤 공백 제거). **잘못된 값은 고쳐서 받지 않고 실패로 기록합니다.** 임의로 보정하면 데이터 오류가 숨겨지기 때문입니다.

| 필드 | 규칙 | 실패 코드 |
|---|---|---|
| 전체 | 필드가 정확히 5개 (빈 필드 `,,`도 1개로 셈) | `INVALID_FIELD_COUNT` |
| ID | 양의 정수 (부호 불가) | `INVALID_ID` |
| 이름 | 공백 아님 | `INVALID_NAME` |
| 나이 | 0~150 정수 | `INVALID_AGE` |
| 성별 | `M` 또는 `F` | `INVALID_GENDER` |
| 연봉 | `30000000` 또는 `30_000_000` (`_`는 3자리 단위만), int 범위 | `INVALID_SALARY` |
| (파일) ID 중복 | 먼저 나온 행을 유지하고 이후 행은 실패 | `DUPLICATE_ID` |

- 빈 줄, 구분선(`====`), 첫 데이터 행 이전의 헤더(`ID , 이름 , ...`)는 실패가 아니라 건너뜁니다. 데이터 행 이후에 `ID ,`로 시작하는 행은 `INVALID_ID` 실패로 기록합니다
- CRLF/LF 혼재, 마지막 행 개행 없음, 파일 선두 BOM을 처리합니다
- 샘플 데이터의 실패 3건: 16행 연봉의 영문 `O`, 31행 빈 필드(`,,`), 39행 연봉의 `AAA`

## 설계 결정

| 결정 | 이유 |
|---|---|
| 불변 스냅샷을 `AtomicReference`로 통째 교체 | Map을 비우고 다시 채우면 그 사이 조회가 빈/부분 데이터를 봅니다. 참조만 바꾸면 조회는 락 없이 항상 완전한 데이터를 봅니다 |
| 파일을 끝까지 읽은 뒤에만 교체 | 재적재가 실패해도 서비스 중인 데이터를 잃지 않습니다 |
| 조회마다 스냅샷을 한 번만 읽음 | 재적재와 겹쳐도 `totalElements`와 `content`가 서로 다른 데이터에서 나오지 않습니다 |
| 재적재는 `synchronized`로 직렬화 | 동시 재적재 시 늦게 끝난 쪽이 덮어쓰는 순서 문제를 막습니다. 조회에는 영향이 없습니다 |
| 파싱 실패를 예외가 아닌 값(`ParseResult`)으로 반환 | 한 줄 실패가 전체 적재를 멈추지 않고, 실패 사유를 모아 조회 API로 그대로 제공합니다 |
| 파서는 스프링에 의존하지 않는 순수 클래스 | 스프링 컨텍스트 없이 빠르게 단위 테스트할 수 있습니다 |
| 기동 시 파일이 없어도 빈 데이터로 기동 | 재적재 API로 복구할 수 있으므로 즉시 종료(fail-fast)보다 가용성을 우선했습니다. 원인은 ERROR 로그로 남깁니다 |
| `ResponseEntityExceptionHandler` 상속 | 스프링 표준 예외의 상태 코드는 프레임워크에 맡기고, `code`와 한글 `detail`만 채운다 |
| 별도 응답 DTO 없이 `User` record 응답 | 필드가 요구사항과 1:1이고 숨길 필드가 없습니다 |

각 기술을 고른 이유와 대안 비교는 [docs/07-tech-decisions.md](docs/07-tech-decisions.md), 운영 환경으로 확장할 때의 구조는 [docs/03-design.md 8절](docs/03-design.md)에 정리했습니다.

## 패키지 구조

```
com.whatap.userapi
├── api            HTTP 경계     UserController, dto/, error/GlobalExceptionHandler
├── service        유스케이스    UserService (조회·검색·재적재), PageResult
├── loader         파싱·파일 I/O  UserLineParser (순수), UserFileLoader, ParseResult, ParseFailure, ParseErrorCode, LoadResult
├── repository     저장소        UserRepository (AtomicReference), UserSnapshot (불변)
├── domain         도메인        User (record)
├── exception      공통 예외     UserNotFoundException, DataLoadException
├── config         설정          UserDataProperties (user-api.data.file-path)
└── startup        기동          UserDataInitializer (기동 시 1회 적재)
```

## 테스트

```bash
./gradlew test    # 리포트: build/reports/jacoco/test/html/index.html
```

**91건 통과**, 라인 커버리지 99% (195/197), 분기 89% (50/56). 커버리지 수치보다 **아직 증명하지 않은 동작**을 기준으로 테스트를 보강했습니다. 테스트는 원본 `user.txt`를 수정하지 않고 임시 복사본을 사용합니다.

| 범주 | 테스트 (건수) | 무엇을 증명하나 |
|---|---|---|
| 파싱 | `UserLineParserTest` (29) | 정상 행, 오류 행 3종, 필드별 경계값 (`+1`, int 초과, `_` 위치 등) |
| 적재 | `UserFileLoaderTest` (8) | 샘플 37/3건, 실패 WARN 로그, 헤더·빈 줄, 중복 ID, 파일 없음, CRLF·BOM, 비 UTF-8 |
| API | `UserControllerTest` (14) | API 5종, 페이지 경계·전 페이지 순회, JSON 자료형, int 오버플로 |
| 에러 | `ApiErrorResponseTest` (17) | 400/404/405/406/500 응답 형식, 내부 정보 비노출 |
| 재적재 | `UserReloadApiTest` (4), `UserServiceReloadTest` (2) | 파일 변경 반영, 실패 시 500 + 기존 데이터 유지, 빈 파일은 0건으로 교체 |
| 동시성 | `UserServiceConcurrencyTest` (1) | 재적재 200회 동안 4개 스레드가 조회해도 반쯤 갱신된 상태가 보이지 않음 (일부러 결함을 넣어 테스트가 잡아내는지 확인함) |
| 통합 | `UserApiIntegrationTest` (4) | 실제 서버에서 HTTP로 기동 → 조회 → 변경 → 재적재 → 실패 → 복구 전체 흐름 |
| 기타 | `UserDataStartupWithoutFileTest` (1), `PageResultTest` (7), `UserSnapshotTest` (3), `contextLoads` (1) | 파일 없이 기동, 페이징 경계, 정렬·불변성 |

과제 API 5종 × 정상/경계/오류 커버리지 대조표: [docs/05-test-plan.md 7절](docs/05-test-plan.md) · 전체 테스트 목록과 요구사항 추적: [docs/10-test-catalog.md](docs/10-test-catalog.md)

## 진행 방식

1. **설계 먼저** — 구현 전에 요구사항·가정·설계·테스트 계획을 문서로 확정했습니다
2. **단계마다 같은 순서** — 테스트 작성 → 구현 → 명세 대조 → 실제 기동·curl 확인 → 커밋
3. **커밋에 판단 근거** — 모든 커밋 본문에 `변경 내용 / 변경 이유 / 설계 결정 / 검증`을 한글로 남겼습니다 ([커밋 규칙](docs/06-commit-convention.md))

```bash
git log --reverse --format='%h %s'    # 단계별 진행 이력
```

시간순 상세 기록과 회고: [docs/08-process-log.md](docs/08-process-log.md)

## 범위 밖 (의도적으로 하지 않은 것)

| 항목 | 이유 |
|---|---|
| DB/JPA | 과제가 힙 메모리 적재를 허용하고, 37건 규모에는 과합니다 |
| 인증·인가 | 요구사항 범위 밖입니다. 운영 시 재적재 API를 `/admin`으로 분리하고 인증을 걸어야 합니다 |
| 파일 변경 자동 감지 | 요구사항은 "API로 갱신"입니다. 확장한다면 감지 후 `UserService.reload()`를 호출하면 됩니다 |

## AI 활용

**Claude Code (Claude Opus 5.5)** 를 사용했습니다.

- **활용 범위**: 요구사항·데이터 분석, 설계 문서 초안, 테스트 케이스 도출, 코드 작성 보조, 단계별 명세 검증
- **통제 방식**: 규칙을 프롬프트에만 두지 않고 도구로 강제했습니다 — 원본 `user.txt` 수정 차단, 테스트가 실패하면 작업 종료 불가, 검증 전용 서브에이전트 (`.claude/`)
- **검토 원칙**: 모든 산출물은 검토한 뒤 반영했습니다. AI가 틀린 사례와 바로잡은 내용은 문서에 기록했습니다

단계별 기록: [docs/AI_USAGE.md](docs/AI_USAGE.md) · 방법론: [docs/09-ai-methodology.md](docs/09-ai-methodology.md)

## 문서

| 문서 | 내용 |
|---|---|
| [01-requirements](docs/01-requirements.md) | 요구사항 분석, 명세에 없는 부분의 가정 |
| [02-work-plan](docs/02-work-plan.md) | 작업 계획, 시간 배분, 제출 체크리스트 |
| [03-design](docs/03-design.md) | 패키지 구조, API 명세, 예외 처리 매트릭스, 운영 확장 설계 |
| [04-data-analysis](docs/04-data-analysis.md) | 샘플 데이터 분석 (CRLF, 오류 행) |
| [05-test-plan](docs/05-test-plan.md) | 테스트 계획, 보강 테스트, 명세 대비 커버리지 |
| [06-commit-convention](docs/06-commit-convention.md) | 커밋 규칙 |
| [07-tech-decisions](docs/07-tech-decisions.md) | 기술 선택 근거 (대안, 판단 기준, 재검토 조건) |
| [08-process-log](docs/08-process-log.md) | 진행 기록, 회고 |
| [09-ai-methodology](docs/09-ai-methodology.md) | AI 활용 방법론 |
| [10-test-catalog](docs/10-test-catalog.md) | 테스트 전체 목록, 요구사항 추적, 테스트하지 않은 것 |
| [AI_USAGE](docs/AI_USAGE.md) | 단계별 AI 활용 내역 |
