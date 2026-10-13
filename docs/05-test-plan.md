# 05. 테스트 계획

> 단계별 계획과 보강 이력을 담습니다. 현재 존재하는 테스트 전체 목록과 요구사항 추적은 [`10-test-catalog.md`](10-test-catalog.md)를 봅니다.

## 1. 단위 테스트 — `UserLineParserTest` (스프링 없이)

| # | 입력 | 기대 |
|---|---|---|
| P-1 | `1001 , 김다나 , 23 , M , 30_000_000` | Success, salary=30000000 |
| P-2 | 후행 공백/CR 포함 `1040 , 오뚜기 , 62 , M , 69_000_000   ` | Success |
| P-3 | `45_OOO_000` (영문 O) | `INVALID_SALARY` |
| P-4 | `68_AAA_000` | `INVALID_SALARY` |
| P-5 | `F ,, 60_000_000` (필드 6개) | `INVALID_FIELD_COUNT` |
| P-6 | 필드 4개 | `INVALID_FIELD_COUNT` |
| P-7 | ID `abc`, `0`, `-1` | `INVALID_ID` |
| P-8 | 나이 `-1`, `151`, `20살` | `INVALID_AGE` |
| P-9 | 성별 `X`, 빈 값 | `INVALID_GENDER` |
| P-10 | 이름 공백 | `INVALID_NAME` |
| P-11 | 연봉 `1__000`, `_1000`, `1000_`, `9_999_999_999` | `INVALID_SALARY` |
| P-12 | 연봉 `30000000` (구분자 없음) | Success |

`@ParameterizedTest` + `@CsvSource`/`@MethodSource`로 묶습니다.

## 2. 로더 테스트 — `UserFileLoaderTest` (`@TempDir`)

| # | 시나리오 | 기대 |
|---|---|---|
| L-1 | 실제 `user.txt` 복사본 | success 37, failure 3, 실패 행 번호 16·31·39 |
| L-2 | 빈 줄·헤더·구분선 포함 | skip, failure 미포함 |
| L-3 | 중복 ID | 두 번째 행 `DUPLICATE_ID` |
| L-4 | 파일 없음 | `DataLoadException` |
| L-5 | CRLF / LF 혼재, BOM | 정상 파싱 |

## 3. API 테스트 — `UserControllerTest` (`@SpringBootTest` + MockMvc, 테스트용 파일 경로 주입)

> 번호는 `API-n`. 요구사항 문서의 가정 번호(`A-n`)와 겹치지 않도록 구분합니다.

| # | 요청 | 기대 |
|---|---|---|
| API-1 | `GET /api/users` | 200, size 10, totalElements 37, 첫 id 1001 |
| API-2 | `GET /api/users?page=100` | 200, 빈 content |
| API-3 | `GET /api/users?size=0` / `size=101` / `page=-1` / `page=abc` | 400 `INVALID_PARAMETER` |
| API-4 | `GET /api/users/1001` | 200, name 김다나 |
| API-5 | `GET /api/users/1016` (파싱 실패 행) | 404 |
| API-6 | `GET /api/users/abc` | 400 |
| API-7 | `GET /api/users/search?name=파이` | 파이리, 파이추 |
| API-8 | `GET /api/users/search?name=` / 파라미터 누락 | 400 |
| API-9 | `GET /api/users/search?name=없는이름` | 200, 빈 content |
| API-10 | `GET /api/users/parse-failures` | 3건, 행 번호 16·31·39 |
| API-11 | 파일 수정 후 `POST /api/users/reload` | 200, 카운트 반영, 조회 결과 반영 |
| API-12 | 파일 삭제 후 `POST /api/users/reload` | 500 `DATA_LOAD_FAILED`, 이후 조회는 기존 데이터 |

## 4. 수동 확인 (curl)

```bash
curl -s 'localhost:8080/api/users?page=0&size=5' | jq
curl -s localhost:8080/api/users/1018 | jq
curl -s 'localhost:8080/api/users/search?name=%ED%8C%8C%EC%9D%B4' | jq   # name=파이
curl -s localhost:8080/api/users/parse-failures | jq
curl -s -X POST localhost:8080/api/users/reload | jq
```

## 5. 보강 테스트 (7단계)

커버리지(JaCoCo): 라인 191/193(99%), 분기 48/54. 미커버 분기는 도달 불가능한 방어 코드(나이 `< 0`은 숫자 패턴에서 이미 걸러짐 등)라 커버리지 수치보다 **아직 증명하지 않은 동작**을 기준으로 보강했습니다.

| # | 테스트 | 검증 내용 |
|---|---|---|
| R-1 | `UserServiceConcurrencyTest` | NFR-3: 3건/50건 파일로 200회 재적재하는 동안 4개 스레드가 조회 → 한 응답 안에서 total과 content 일치, 두 파일 데이터 혼재 없음, 두 상태 모두 관측(조회가 재적재 사이사이에 실행됐음을 확인) |
| R-2 | `UserDataStartupWithoutFileTest` | 요구사항 A-8: 파일 없이 기동 → 빈 목록, `loadedAt` 없음, 재적재 500 |
| R-3 | `PageResultTest` | 0건, 정확히 나누어떨어짐, 마지막 1건, size > 전체, 범위 밖 |
| R-4 | `UserSnapshotTest` | 파일 순서와 무관한 ID 정렬, 빈 스냅샷, 컬렉션 불변 |
| R-5 | `UserFileLoaderTest#logsParseFailures` | FR-3: 실패 행마다 WARN 로그(행 번호·코드·원본), INFO 요약 로그 |
| R-6 | `UserReloadApiTest#replacesWithEmptyDataWhenFileHasNoRows` | 03-design 6.1 (1): 빈 파일·헤더만 있는 파일 재적재 → 200, 0건으로 교체, `loadedAt` 갱신 (예외 처리 설계 리뷰에서 정한 정책을 고정) |

- R-1 뮤테이션 확인: `findAll`이 스냅샷을 두 번 읽도록 고의로 바꾸면 3회 모두 실패 → 테스트가 실제로 결함을 검출함
- R-1 안정성: 10회 반복 실행 모두 통과
- R-1 한계: 조회 **도중**에 교체가 일어났음을 직접 증명하지는 않습니다. 다만 리스트를 비우고 채우는 식의 부분 갱신이면 total이 3/50이 아닌 값으로 관측되어 실패합니다
- 커버리지 리포트: `./gradlew test` 후 `build/reports/jacoco/test/html/index.html`

## 6. 통합 테스트 — `UserApiIntegrationTest` (`RANDOM_PORT` 실제 서버 + `RestClient`)

MockMvc 테스트는 서블릿 컨테이너를 거치지 않으므로, 실제 내장 서버를 띄워 HTTP로 호출해 다음을 확인합니다: 컨테이너 라우팅, 한글 쿼리 파라미터 URL 인코딩, JSON 직렬화와 `Content-Type`, 그리고 전체 흐름(기동 → 조회 → 변경 → 재적재 → 실패 → 복구).

| # | 테스트 | 검증 내용 |
|---|---|---|
| I-1 | `listsUsersOverHttp` | 기동 시 적재된 샘플 37건이 `application/json`으로 직렬화되어 조회됨 (첫 행 1001, 연봉 정수 변환) |
| I-2 | `searchesByKoreanName` | 한글 검색어(`파이`)가 URL 인코딩되어 전달되고 부분 일치 2건 반환 |
| I-3 | `returnsProblemDetailOverHttp` | 실제 서버에서 404·400·없는 경로 404·405가 `application/problem+json`과 `code`를 가짐 |
| I-4 | `reloadLifecycle` | 기동 시 실패 3건(16·31·39) → 행 추가 후 재적재 38건 반영 → 파일 삭제 시 500 `DATA_LOAD_FAILED`, 응답에 경로 미노출, 기존 38건 유지 → 복구 후 재적재 37건 |

- 원본 `user.txt`는 임시 디렉터리에 복사해 사용하며 수정하지 않습니다
- 테스트 클라이언트는 `spring-web`의 `RestClient`를 사용해 추가 의존성 없이 구성. `exchange`로 4xx/5xx도 예외 없이 상태·본문을 검증

## 7. 과제 명세 "4. RestFul Api 구현 목록" 대비 커버리지

과제 PDF의 API 5종을 기준으로 정상·경계·오류 케이스가 어느 테스트에서 검증되는지 대조합니다. (`UC` = `UserControllerTest`, `AE` = `ApiErrorResponseTest`, `RA` = `UserReloadApiTest`, `IT` = `UserApiIntegrationTest`, `SW` = `UserDataStartupWithoutFileTest`)

| 명세 API | 정상 | 경계·데이터 | 오류 |
|---|---|---|---|
| 전체 목록 페이지 조회 (JSON) | UC 기본 페이지, IT-1 실제 HTTP JSON | UC 마지막 페이지·범위 밖·오버플로, **UC 전 페이지 순회(누락·중복 없음)**, SW 빈 데이터 | AE page/size 범위·타입 오류 400 |
| ID로 단건 조회 (JSON) | UC 5개 필드 값, **UC 필드 자료형(정수/문자열)** | UC 파싱 실패 ID(1016) 404 | UC·IT-3 없는 ID 404, AE·IT-3 숫자 아닌 ID 400 |
| 이름 포함 문자열 검색 (JSON) | UC 부분 일치, IT-2 한글 URL 인코딩 | UC 공백 trim·페이징·결과 없음, **UC 실패 행 이름 미검색** | AE 검색어 누락·공백 400 |
| 파싱 실패 데이터 조회 | UC 행 번호·원본·코드, **UC 3건 모두 코드·사유 메시지** | RA·IT-4 재적재 후 실패 목록 교체, SW 미적재 시 `loadedAt` null | - |
| 파일 재읽기·메모리 갱신 | RA·IT-4 추가·삭제 반영, 응답 건수 | IT-4 실패 후 복구 재적재, R-1 재적재 중 조회 일관성 | RA·IT-4·SW 파일 없음 500 + 기존 데이터 유지, 경로 미노출 |

굵게 표시한 항목은 이 대조 과정에서 빈틈으로 확인되어 추가한 테스트입니다.

## 8. 프론트엔드 (`StaticFrontendTest`, `UserApiIntegrationTest#servesFrontendAtRoot`)

설계: [`11-frontend-design.md`](11-frontend-design.md). 정적 리소스 제공과 보안 규칙은 자동 테스트로, 화면 동작은 수동 시나리오로 확인합니다.

| # | 테스트 | 검증 내용 |
|---|---|---|
| FE-1 | `rootForwardsToIndex` | `/` → `index.html` |
| FE-2 | `servesIndexWithCsp` | `text/html`, CSP `default-src 'self'`, 스크립트 포함 |
| FE-3 | `servesAssets` | JS·CSS `Content-Type` |
| FE-4 | `scriptDoesNotUseInnerHtml` | 데이터를 `innerHTML`로 렌더링하지 않음 |
| FE-5 | `missingAssetKeepsProblemDetail` | 없는 정적 파일은 기존 404 `NOT_FOUND` |
| I-5 | `servesFrontendAtRoot` | 실제 서버에서 `/`가 화면 제공 |
| F-1~F-12 | 수동 (11 8절) | 목록·검색·상세·탭·재적재·에러 표시·HTML 주입·모바일 폭·미적재 경고·페이지 보정 |
