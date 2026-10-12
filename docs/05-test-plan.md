# 05. 테스트 계획

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

`@ParameterizedTest` + `@CsvSource`/`@MethodSource`로 묶는다.

## 2. 로더 테스트 — `UserFileLoaderTest` (`@TempDir`)

| # | 시나리오 | 기대 |
|---|---|---|
| L-1 | 실제 `user.txt` 복사본 | success 37, failure 3, 실패 행 번호 16·31·39 |
| L-2 | 빈 줄·헤더·구분선 포함 | skip, failure 미포함 |
| L-3 | 중복 ID | 두 번째 행 `DUPLICATE_ID` |
| L-4 | 파일 없음 | `DataLoadException` |
| L-5 | CRLF / LF 혼재, BOM | 정상 파싱 |

## 3. API 테스트 — `UserControllerTest` (`@SpringBootTest` + MockMvc, 테스트용 파일 경로 주입)

| # | 요청 | 기대 |
|---|---|---|
| A-1 | `GET /api/users` | 200, size 10, totalElements 37, 첫 id 1001 |
| A-2 | `GET /api/users?page=100` | 200, 빈 content |
| A-3 | `GET /api/users?size=0` / `size=101` / `page=-1` / `page=abc` | 400 `INVALID_PARAMETER` |
| A-4 | `GET /api/users/1001` | 200, name 김다나 |
| A-5 | `GET /api/users/1016` (파싱 실패 행) | 404 |
| A-6 | `GET /api/users/abc` | 400 |
| A-7 | `GET /api/users/search?name=파이` | 파이리, 파이추 |
| A-8 | `GET /api/users/search?name=` / 파라미터 누락 | 400 |
| A-9 | `GET /api/users/search?name=없는이름` | 200, 빈 content |
| A-10 | `GET /api/users/parse-failures` | 3건, 행 번호 16·31·39 |
| A-11 | 파일 수정 후 `POST /api/users/reload` | 200, 카운트 반영, 조회 결과 반영 |
| A-12 | 파일 삭제 후 `POST /api/users/reload` | 500 `DATA_LOAD_FAILED`, 이후 조회는 기존 데이터 |

## 4. 수동 확인 (curl)

```bash
curl -s 'localhost:8080/api/users?page=0&size=5' | jq
curl -s localhost:8080/api/users/1018 | jq
curl -s 'localhost:8080/api/users/search?name=%ED%8C%8C%EC%9D%B4' | jq   # name=파이
curl -s localhost:8080/api/users/parse-failures | jq
curl -s -X POST localhost:8080/api/users/reload | jq
```
