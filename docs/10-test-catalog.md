# 10. 테스트 명세 (전체 목록)

> 현재 저장소에 있는 **모든 테스트**를 클래스·메서드 단위로 정리합니다.
> [`05-test-plan.md`](05-test-plan.md)는 단계별로 세운 계획과 보강 이력이고, 이 문서는 그 결과 실제로 존재하는 테스트의 목록입니다.
> 계획 번호(P/L/API/R/I/FE-x)가 붙은 테스트는 번호를 함께 적었습니다. 번호가 없는 테스트는 `클래스#메서드`로 식별합니다.

## 1. 요약

| 항목 | 값 |
|---|---|
| 테스트 클래스 | 13개 |
| 실행 건수 | **97건** (파라미터 케이스 포함) |
| 실행 | `./gradlew test` (커버리지 리포트: `build/reports/jacoco/test/html/index.html`) |
| 단일 클래스 실행 | `./gradlew test --tests '*UserLineParserTest'` |

### 계층별 구성

| 계층 | 클래스 (건수) | 방식 | 스프링 컨텍스트 |
|---|---|---|---|
| 파싱 | `UserLineParserTest` (29) | 순수 단위 테스트 | 없음 |
| 적재 | `UserFileLoaderTest` (8) | 단위 + `@TempDir` 실제 파일 | 없음 |
| 도메인·서비스 | `UserSnapshotTest` (3), `PageResultTest` (7), `UserServiceReloadTest` (2), `UserServiceConcurrencyTest` (1) | 단위 (객체 직접 조립) | 없음 |
| 웹 슬라이스 | `ApiErrorResponseTest` (17), `StaticFrontendTest` (5) | `@WebMvcTest` + `UserService` 목 | 웹 계층만 |
| API | `UserControllerTest` (14), `UserReloadApiTest` (4), `UserDataStartupWithoutFileTest` (1) | `@SpringBootTest` + MockMvc | 전체 |
| 통합 | `UserApiIntegrationTest` (5) | `RANDOM_PORT` 실제 서버 + `RestClient` | 전체 + 내장 톰캣 |
| 기동 | `UserApiApplicationTests` (1) | `@SpringBootTest` | 전체 |

계층마다 도구를 다르게 쓴 이유:
- **파서·로더는 스프링 없이** 테스트합니다. 오류 데이터 경우의 수가 가장 많은 곳이라 빠르게 많이 돌립니다
- **에러 응답 형식은 `@WebMvcTest` + 목**으로 테스트합니다. 500처럼 실제 데이터로는 만들기 어려운 상황을 목으로 일으킵니다
- **API 동작은 실제 샘플 데이터로 `@SpringBootTest`** 를 띄워 테스트합니다. 기동 시 적재(`ApplicationRunner`)부터 응답까지 실제 흐름을 탑니다
- **통합 테스트는 실제 서버**로 합니다. MockMvc가 거치지 않는 서블릿 컨테이너 라우팅, 한글 URL 인코딩, `Content-Type` 협상을 확인합니다

## 2. 공통 규칙

- **원본 `user.txt`는 수정하지 않습니다.** 파일을 읽는 테스트는 모두 임시 디렉터리 복사본을 씁니다
  - 단위 테스트: JUnit `@TempDir`
  - 스프링 테스트: `static` 임시 파일을 만들고 `@DynamicPropertySource`로 `user-api.data.file-path`에 주입. 기동 시 적재가 읽을 수 있도록 컨텍스트 생성 전에 파일을 준비합니다
  - 파일을 바꾸는 테스트(`UserReloadApiTest`, `UserApiIntegrationTest`)는 `@BeforeEach`에서 샘플로 되돌리고 재적재해, 테스트 순서에 영향을 받지 않습니다
- **`@DisplayName`은 한글로**, 무엇을 증명하는지 문장으로 씁니다. 계획 번호가 있으면 앞에 붙입니다
- **에러 응답은 `code`와 `detail` 문구까지 검증**합니다. 상태 코드만 보면 응답 형식이 깨져도 통과하기 때문입니다
- 기대값은 샘플 데이터의 실제 값(37건, 실패 행 16·31·39, 1017 치킨 등)을 씁니다. 데이터 특이사항은 [`04-data-analysis.md`](04-data-analysis.md)

## 3. 클래스별 명세

### 3.1 `UserLineParserTest` — 한 줄 파싱 (29건)

`UserLineParser.parse(lineNumber, rawLine)`가 `ParseResult.Success` 또는 `Failure`를 반환하는지 검증합니다.

| 메서드 | 계획 | 검증 내용 |
|---|---|---|
| `parsesValidLine` | P-1 | 정상 행 → `User(1001, 김다나, 23, M, 30000000)`. 연봉 `_` 제거 |
| `parsesLineWithTrailingWhitespace` | P-2 | 끝에 공백과 `\r`이 붙은 행도 정상 파싱 (CRLF 파일의 마지막 필드) |
| `parsesSalaryWithoutSeparator` | P-12 | `_` 없는 연봉 `30000000` 허용 |
| `parsesLineWithBom` | - | 행 앞의 BOM(`﻿`)을 제거하고 파싱 |
| `failureContainsLineInfo` | - | 실패 결과에 행 번호·원본 행·에러 코드·메시지(문제 값 포함)가 모두 담김 |
| `failsOnInvalidLine` (22) | P-3~P-11 | 아래 표 |
| `acceptsAgeBoundaries` (2) | - | 나이 경계값 `0`, `150` 허용 |

`failsOnInvalidLine` 케이스:

| 입력 (요지) | 기대 코드 | 계획 | 의도 |
|---|---|---|---|
| 연봉 `45_OOO_000` | `INVALID_SALARY` | P-3 | 샘플 16행. 숫자 0 대신 영문 O |
| 연봉 `68_AAA_000` | `INVALID_SALARY` | P-4 | 샘플 39행 |
| `F ,, 60_000_000` | `INVALID_FIELD_COUNT` | P-5 | 샘플 31행. 빈 필드 때문에 6개 |
| 필드 4개 | `INVALID_FIELD_COUNT` | P-6 | |
| ID `abc`, `0`, `-1` | `INVALID_ID` | P-7 | 숫자 아님, 0, 음수 |
| ID `+1` | `INVALID_ID` | P-7 | `Integer.parseInt`는 받아들이는 부호를 패턴으로 거름 |
| ID `9999999999` | `INVALID_ID` | P-7 | int 범위 초과 (`NumberFormatException`을 실패로 변환) |
| 이름 공백 | `INVALID_NAME` | P-10 | |
| 나이 `-1`, `151`, `20살` | `INVALID_AGE` | P-8 | 범위 밖, 숫자 아님 |
| 성별 `X`, `m`, 빈 값 | `INVALID_GENDER` | P-9 | 소문자 `m`도 거부 (대소문자 보정 안 함) |
| 연봉 `1__000`, `_1000`, `1000_` | `INVALID_SALARY` | P-11 | `_` 연속·앞·뒤 |
| 연봉 `10_00` | `INVALID_SALARY` | P-11 | `_`는 3자리 그룹만 |
| 연봉 `-1_000` | `INVALID_SALARY` | P-11 | 음수 |
| 연봉 `9_999_999_999` | `INVALID_SALARY` | P-11 | int 범위 초과 |

### 3.2 `UserFileLoaderTest` — 파일 적재 (8건)

`UserFileLoader.load(path)`가 파일 전체를 `LoadResult`로 만들고, 파일 자체를 못 읽으면 `DataLoadException`을 던지는지 검증합니다.

| 메서드 | 계획 | 검증 내용 |
|---|---|---|
| `loadsSampleFile` | L-1 | 샘플 복사본 → 성공 37건, 실패 3건(16 `INVALID_SALARY`, 31 `INVALID_FIELD_COUNT`, 39 `INVALID_SALARY`). 마지막 사용자 1040이 정상 적재됨 = **마지막 행 개행 없음** 처리 확인 |
| `logsParseFailures` | R-5 | FR-3: 실패 행마다 WARN 로그(행 번호·코드·원본), 적재 요약 INFO 로그(`success=37, failure=3`). `OutputCaptureExtension`으로 출력 캡처 |
| `skipsBlankHeaderAndSeparatorLines` | L-2 | 헤더·구분선·빈 줄·공백만 있는 줄은 건너뛰고 실패로 기록하지 않음 |
| `treatsHeaderLikeLineAfterDataAsFailure` | - | 헤더는 첫 데이터 행 이전에만 인정. 이후의 `id ,` 행은 3행 `INVALID_ID` 실패 |
| `recordsDuplicateId` | L-3 | 중복 ID는 먼저 나온 행 유지, 2행 `DUPLICATE_ID` |
| `throwsWhenFileMissing` | L-4 | 파일 없음 → `DataLoadException` (메시지에 파일명) |
| `handlesMixedLineEndingsAndBom` | L-5 | 파일 선두 BOM + CRLF/LF 혼재에도 2건 정상 적재 |
| `throwsWhenNotUtf8` | - | EUC-KR 파일 → `MalformedInputException`이 `DataLoadException`으로 변환 |

### 3.3 `UserSnapshotTest` — 불변 스냅샷 (3건)

| 메서드 | 계획 | 검증 내용 |
|---|---|---|
| `sortsByIdAndIndexes` | R-4 | 파일 순서와 관계없이 ID 오름차순 정렬, ID 조회 성공·실패 |
| `emptySnapshot` | R-4 | 빈 스냅샷은 목록·실패 없음, `loadedAt` null (기동 실패 상태 표시) |
| `isImmutable` | R-4 | 목록·맵을 외부에서 바꾸면 `UnsupportedOperationException` |

### 3.4 `PageResultTest` — 페이징 계산 (7건)

`paginates` 하나를 7개 경계값으로 실행합니다 (R-3). 각 케이스에서 content 건수, `totalElements`, `totalPages`, 첫 요소 위치를 확인합니다.

| total | page | size | content | totalPages | 의도 |
|---|---|---|---|---|---|
| 0 | 0 | 10 | 0 | 0 | 데이터 없음 |
| 10 | 0 | 10 | 10 | 1 | 정확히 한 페이지 |
| 10 | 1 | 10 | 0 | 1 | 나누어떨어질 때 다음 페이지는 비어 있음 |
| 11 | 1 | 10 | 1 | 2 | 마지막 페이지 1건 |
| 5 | 0 | 100 | 5 | 1 | size가 전체보다 큼 |
| 5 | 4 | 1 | 1 | 5 | 마지막 인덱스 |
| 5 | 5 | 1 | 0 | 5 | 범위 밖 |

### 3.5 `UserServiceReloadTest` — 재적재 (서비스 단위, 2건)

| 메서드 | 검증 내용 |
|---|---|
| `reloadReplacesSnapshot` | 파일 내용을 바꿔 재적재하면 이전 데이터(1001)는 사라지고 새 데이터(1002)와 실패 1건으로 교체 |
| `reloadFailureKeepsPreviousSnapshot` | 파일 삭제 후 재적재 → `DataLoadException`, 저장소에는 이전 스냅샷(1001) 유지 |

### 3.6 `UserServiceConcurrencyTest` — 재적재 중 조회 일관성 (1건)

| 메서드 | 계획 | 검증 내용 |
|---|---|---|
| `readersNeverSeePartialState` | R-1 | NFR-3. 3건/50건 파일을 번갈아 200회 재적재하는 동안 조회 스레드 4개가 `findAll(0, 100)` 반복 |

판정 기준:
- 한 응답 안에서 `totalElements`와 `content` 건수가 같습니다 (같은 스냅샷에서 나왔음)
- 한 응답의 ID가 한 파일에서만 왔습니다 (1xxx와 2xxx가 섞이지 않음)
- 3건 상태와 50건 상태가 **둘 다 관측**됐습니다. 조회가 재적재 사이사이에 실제로 실행됐다는 뜻으로, 이것이 없으면 테스트가 아무것도 증명하지 못한 채 통과할 수 있습니다

이 테스트가 결함을 실제로 잡는지 확인한 기록(일부러 결함을 넣어 보는 뮤테이션 확인)과 한계는 [`05-test-plan.md` 5절](05-test-plan.md)에 있습니다.

### 3.7 `ApiErrorResponseTest` — 에러 응답 형식 (17건)

`@WebMvcTest(UserController.class)` + `@MockitoBean UserService`. 모든 에러가 `ProblemDetail` + `code` + 한글 `detail`로 나오는지 검증합니다.

| 메서드 | 계획 | 요청 | 기대 |
|---|---|---|---|
| `rejectsOutOfRangePaging` (4) | API-3 | `size=0`, `size=101`, `page=-1`, 검색의 `size=0` | 400 `INVALID_PARAMETER`, 제약별 한글 사유 |
| `rejectsTypeMismatch` (4) | API-3, API-6 | `page=abc`, `size=1.5`, `/users/abc`, `/users/99999999999` | 400 `INVALID_PARAMETER`, `파라미터 'x'의 값 'y'가 올바른 형식이 아닙니다.` |
| `rejectsMissingName` | API-8 | `/search` (name 없음) | 400, `필수 파라미터 'name'이(가) 없습니다.` |
| `rejectsBlankName` (2) | API-8 | `name=`, `name=␣␣␣` | 400, `name은 공백일 수 없습니다.` |
| `returns404ForUnknownPath` | - | `GET /api/unknown` | 404 `NOT_FOUND` |
| `returns405ForUnsupportedMethod` | - | `DELETE /api/users/1001` | 405 `METHOD_NOT_ALLOWED` |
| `abbreviatesLongInputInDetail` | - | 60자 ID | `detail`의 입력값이 50자 + `...`로 잘림 |
| `fillsCodeAndKoreanDetailForOtherSpringExceptions` | - | 핸들러 직접 호출 (406) | `code=NOT_ACCEPTABLE`, 한글 공통 문구. MockMvc로는 본문 없는 406이 되어 직접 호출 |
| `hidesInternalMessageOnUnexpectedError` | - | 서비스가 `IllegalStateException` | 500 `INTERNAL_ERROR`, 고정 문구 (내부 메시지 미노출) |
| `unexpectedHandlerDoesNotLeakMessage` | - | 핸들러 직접 호출 | 응답에 원본 예외 메시지(`secret`) 없음 |

### 3.8 `UserControllerTest` — API 동작 (14건)

`@SpringBootTest` + MockMvc. 샘플 데이터 복사본을 기동 시 적재한 상태에서 호출합니다.

| 메서드 | 계획 | 요청 | 검증 내용 |
|---|---|---|---|
| `listsFirstPageByDefault` | API-1 | `GET /api/users` | 기본 page 0·size 10, 첫 행 1001 김다나, total 37, 4페이지 |
| `listsLastPage` | - | `page=3&size=10` | 마지막 페이지 7건(1033~1040). 실패 ID 1016은 목록에 없음 |
| `returnsEmptyContentWhenPageOutOfRange` | API-2 | `page=100` | 200, 빈 content, total은 37 유지 |
| `handlesHugePageWithoutOverflow` | - | `page=2147483647&size=100` | `page × size` int 오버플로 없이 빈 content |
| `pagesCoverAllUsersWithoutGapOrDuplicate` | - | 0~3페이지 순회 | 37건이 누락·중복 없이 ID 오름차순 (1001~1040에서 16·31·39 제외) |
| `findsById` | API-4 | `GET /api/users/1017` | 5개 필드 값 (치킨, 39, M, 46000000) |
| `userJsonFieldTypesMatchSpec` | - | 단건·목록 | ID·나이·연봉은 JSON 숫자, 이름·성별은 문자열 (과제의 User 자료형) |
| `returns404WhenUserNotFound` | API-5 | `GET /api/users/1016` | 파싱 실패 행 ID는 404 `USER_NOT_FOUND`, 한글 detail |
| `searchesByName` | API-7 | `name=파이` | 파이리, 파이추 2건 |
| `searchesWithTrimmedKeywordAndPaging` | - | `name=␣김␣&size=2` | 앞뒤 공백 무시, 검색 결과도 페이징 (5건, 3페이지) |
| `returnsEmptyWhenNoMatch` | API-9 | `name=없는이름` | 200, 빈 content |
| `searchExcludesFailedRows` | - | `name=바사삭` | 파싱 실패 행(1016)의 이름은 검색되지 않음 |
| `listsParseFailures` | API-10 | `GET /parse-failures` | 3건, 행 번호 16·31·39, 원본 행, 에러 코드, `loadedAt` 존재 |
| `parseFailuresHaveCodeAndMessageForEachRow` | - | `GET /parse-failures` | 3건 모두 에러 코드와 비어 있지 않은 사유 메시지 |

### 3.9 `UserReloadApiTest` — 재적재 API (4건)

`@BeforeEach`에서 데이터 파일을 샘플로 되돌리고 재적재합니다.

| 메서드 | 계획 | 검증 내용 |
|---|---|---|
| `reloadsChangedFile` | API-11 | 파일을 3행(정상 2, 성별 오류 1)으로 바꿔 재적재 → 응답 `successCount=2`, `failureCount=1`. 새 ID 조회 가능, 사라진 ID(1018)는 404, 목록·실패 목록 모두 새 파일 기준 |
| `keepsPreviousDataWhenReloadFails` | API-12 | 파일 삭제 후 재적재 → 500 `DATA_LOAD_FAILED`, 고정 문구. 이후 조회는 기존 37건·실패 3건 유지 |
| `replacesWithEmptyDataWhenFileHasNoRows` (2) | R-6 | 빈 파일, 헤더·구분선만 있는 파일을 재적재 → **200**, `successCount=0`·`failureCount=0`. 목록 0건, 1001은 404, `loadedAt`은 갱신됨. 0건도 유효한 결과로 교체한다는 정책(03 6.1 (1), 07 3.5)을 고정하는 테스트로, 0건 거부 가드를 넣으면 함께 바꿉니다 |

### 3.10 `UserDataStartupWithoutFileTest` — 파일 없이 기동 (1건)

| 메서드 | 계획 | 검증 내용 |
|---|---|---|
| `startsWithEmptyData` | R-2 | 요구사항 A-8. 없는 경로로 기동해도 애플리케이션이 뜨고, 목록은 0건, `/parse-failures`의 `loadedAt`이 없음(적재된 적 없음 표시), 재적재는 500 `DATA_LOAD_FAILED` |

### 3.11 `UserApiIntegrationTest` — 실제 서버 통합 (5건)

`RANDOM_PORT`로 내장 서버를 띄우고 `RestClient`로 호출합니다. `exchange`를 써서 4xx/5xx도 예외 없이 상태와 본문을 검증합니다.

| 메서드 | 계획 | 검증 내용 |
|---|---|---|
| `listsUsersOverHttp` | I-1 | 응답 `Content-Type`이 `application/json`, 37건, 4페이지, 첫 행 1001 김다나, 연봉이 정수 30000000 |
| `searchesByKoreanName` | I-2 | 한글 검색어 `파이`가 URL 인코딩되어 전달되고 2건 모두 이름에 `파이` 포함 |
| `returnsProblemDetailOverHttp` | I-3 | 404(`USER_NOT_FOUND`, `instance` 경로 포함)가 `application/problem+json`. 400 `INVALID_PARAMETER`, 없는 경로 404 `NOT_FOUND`, 405 `METHOD_NOT_ALLOWED` |
| `reloadLifecycle` | I-4 | 한 시나리오로 전체 흐름: ① 기동 시 실패 3건(16·31·39) ② 행 추가 후 재적재 38건 반영 ③ 파일 삭제 시 500, 응답에 서버 경로 미노출, 기존 38건 유지 ④ 파일 복구 후 재적재 37건 |
| `servesFrontendAtRoot` | I-5 | 실제 서버에서 `/`가 200 `text/html`로 프론트엔드 화면(`index.html`)을 제공. MockMvc는 welcome page의 forward를 실행하지 않아 실제 서버로 확인 |

### 3.12 `UserApiApplicationTests` — 기동 (1건)

| 메서드 | 검증 내용 |
|---|---|
| `contextLoads` | 기본 설정으로 애플리케이션 컨텍스트가 뜹니다 (빈 구성 오류 검출) |

### 3.13 `StaticFrontendTest` — 프론트엔드 정적 리소스 (5건)

`@WebMvcTest(UserController.class)` + `@MockitoBean UserService`. 화면 설계는 [`11-frontend-design.md`](11-frontend-design.md)이고, 화면 동작은 그 문서 8절의 수동 시나리오(F-1~F-17)로 확인하며, 실행 절차와 스크린샷은 [`12-ui-test-guide.md`](12-ui-test-guide.md)에 있습니다.

| 메서드 | 계획 | 검증 내용 |
|---|---|---|
| `rootForwardsToIndex` | FE-1 | `/`가 `index.html`로 forward (스프링 부트 welcome page) |
| `servesIndexWithCsp` | FE-2 | `index.html`이 `text/html`이고 `meta charset`, 제목, CSP `default-src 'self'`, `app.js` 포함. 정적 리소스 응답에 charset이 없어 본문을 UTF-8로 읽음 |
| `servesAssets` | FE-3 | `app.js`는 `text/javascript`, `app.css`는 `text/css` |
| `scriptDoesNotUseInnerHtml` | FE-4 | `app.js`에 `innerHTML`·`insertAdjacentHTML`이 없고 `textContent`를 씀. 데이터 파일 원문(`rawLine`, 이름)의 HTML이 해석되지 않도록 하는 규칙을 고정 |
| `missingAssetKeepsProblemDetail` | FE-5 | 정적 리소스를 추가해도 없는 파일은 기존과 같은 404 `NOT_FOUND` ProblemDetail |

## 4. 요구사항 추적

요구사항·가정 번호는 [`01-requirements.md`](01-requirements.md) 기준.

| 요구사항 | 증명하는 테스트 |
|---|---|
| FR-1 Spring Boot REST API | `contextLoads`, 3.8·3.11 전체 |
| FR-2 기동 시 적재, User 변환 | L-1, P-1, API-1 (기동 시 적재된 데이터로 조회), I-1 |
| FR-3 파싱 실패 로그 | R-5 `logsParseFailures` |
| FR-4 목록 페이지 조회 | API-1, API-2, `listsLastPage`, `handlesHugePageWithoutOverflow`, `pagesCoverAllUsersWithoutGapOrDuplicate`, R-3, I-1 |
| FR-5 ID 조회 | API-4, API-5, `userJsonFieldTypesMatchSpec` |
| FR-6 이름 부분 일치 검색 | API-7, API-9, `searchesWithTrimmedKeywordAndPaging`, `searchExcludesFailedRows`, I-2 |
| FR-7 파싱 실패 조회 | API-10, `parseFailuresHaveCodeAndMessageForEachRow`, `failureContainsLineInfo`, I-4 ① |
| FR-8 재적재 | API-11, API-12, R-6, `UserServiceReloadTest`, I-4 |
| NFR-1 일관된 에러 응답 | 3.7 전체 (17건), API-5, I-3 |
| NFR-2 I/O 오류에도 생존·데이터 유지 | L-4, `throwsWhenNotUtf8`, API-12, `reloadFailureKeepsPreviousSnapshot`, R-2, I-4 ③ |
| NFR-3 부분 갱신 비노출 | R-1, R-4 `isImmutable` |
| A-1 UTF-8, `,` 구분, trim | P-1, P-2, `throwsWhenNotUtf8` |
| A-2 연봉 `_` 자릿수 구분자 | P-1, P-11, P-12 |
| A-3 필드 수 5개, 빈 필드 보정 안 함 | P-5, P-6 |
| A-4 성별 M/F만 | P-9 (소문자 `m` 포함) |
| A-5 중복 ID는 먼저 나온 행 유지 | L-3 |
| A-6 빈 줄·구분선·헤더 skip | L-2, `treatsHeaderLikeLineAfterDataAsFailure`, L-5 (BOM) |
| A-7 재적재 실패 시 기존 데이터 유지 | API-12, `reloadFailureKeepsPreviousSnapshot`, I-4 ③ |
| A-8 파일 없어도 기동 | R-2 |
| A-9 page 0부터, 기본 10, 최대 100 | API-1, API-3 |
| A-10 ID 오름차순 | API-1, `pagesCoverAllUsersWithoutGapOrDuplicate`, R-4 `sortsByIdAndIndexes` |
| 프론트엔드 (11-frontend-design) | FE-1~FE-5, I-5. 화면 동작은 수동 시나리오 F-1~F-17 |

## 5. 테스트하지 않은 것

설계 문서에 정해 둔 동작 중 테스트로 증명하지 않은 것과 그 이유입니다. 추가 여부를 정할 때의 참고용입니다.

| 동작 | 근거 문서 | 테스트 없는 이유 | 추가 우선순위 |
|---|---|---|---|
| 기동 시 `DataLoadException` 외 예외는 기동 실패 (fail-fast) | 03 6.1 (2), 07 3.4 | 파서 버그를 인위로 주입해야 해 테스트 비용이 큼 | 낮음 |
| 동시 재적재 직렬화 (`synchronized`) | 03 4.3 | R-1은 재적재 스레드가 하나. 동시 재적재 결과는 어느 쪽이든 완전한 스냅샷이라 관측 가능한 차이가 작음 | 낮음 |
| 파일 읽기 권한 없음 | 03 4.2 | 파일 없음·비 UTF-8과 같은 `IOException` 경로. OS·실행 사용자(root)에 따라 결과가 달라 불안정 | 낮음 |
| `GET /api/users/reload`가 405가 아닌 400 | 03 6.1 (5) | 알려진 부수 동작이며 바꿀 계획이 없음 | 낮음 |
| 415 등 그 밖의 스프링 표준 예외 | 03 6 | 406 한 건으로 공통 보정(`handleExceptionInternal`) 경로를 대표 | 낮음 |
| 화면 동작 (검색·페이징·상세·탭·재적재 후 페이지 보정·URL 상태·에러 표시) | 11 4·5절 | JS 테스트 도구는 Node 툴체인을 끌어옴. 수동 시나리오 F-1~F-17로 확인하고 결과를 11 8.1에 기록 | 화면 로직이 커지면 Playwright 등 E2E 도입 |
| 재적재 중 파일이 쓰이는 경우의 부분 적재 (빈 파일·헤더만 있는 파일은 R-6에서 검증) | 03 6.1 (1) | 타이밍 재현이 어렵고, 해결책은 쓰기 측 규약(rename)이라 이 코드의 테스트 대상이 아님 | 해당 없음 |

## 6. 테스트를 추가할 때

- 어느 계층에서 증명할지 먼저 정합니다. 데이터 경우의 수는 파서·로더 단위 테스트, 응답 형식은 `ApiErrorResponseTest`, 실제 데이터 흐름은 `UserControllerTest`, 컨테이너가 관여하는 동작만 통합 테스트
- 같은 설정의 `@SpringBootTest`를 재사용합니다. 프로퍼티가 다르면 스프링 컨텍스트가 새로 떠서 테스트 시간이 늘어납니다 (현재 전체 컨텍스트 5종 + 웹 슬라이스 1종)
- 파일을 바꾸는 테스트는 `@BeforeEach`에서 샘플로 되돌립니다
- 계획에 있는 케이스면 `05-test-plan.md`의 번호를 `@DisplayName` 앞에 붙이고, 이 문서의 해당 표에 행을 추가합니다
