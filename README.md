# user-api

와탭랩스 JAVA 코딩테스트 — `user.txt` 기반 사용자 조회 REST API

## 실행

```bash
./gradlew bootRun
```

- 요구 환경: JDK 21
- 데이터 파일: 실행 디렉터리의 `./user.txt` (변경: `USER_API_DATA_FILE_PATH` 환경변수)

## API

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/users?page=0&size=10` | 전체 목록 (페이징, ID 오름차순) |
| GET | `/api/users/{id}` | ID로 단건 조회 |
| GET | `/api/users/search?name={문자열}&page=0&size=10` | 이름 부분 일치 검색 |
| GET | `/api/users/parse-failures` | 파싱 실패 데이터 조회 |
| POST | `/api/users/reload` | `user.txt` 재적재 |

상세 명세와 응답 예시: [docs/03-design.md](docs/03-design.md)

## 설계 결정 요약

<!-- 구현 완료 후 작성: 파싱 정책, 원자적 스냅샷 교체, 재적재 실패 시 기존 데이터 유지 등 -->

## 문서

| 문서 | 내용 |
|---|---|
| [01-requirements](docs/01-requirements.md) | 요구사항 분석, 가정 |
| [02-work-plan](docs/02-work-plan.md) | 작업 계획, 제출 체크리스트 |
| [03-design](docs/03-design.md) | 패키지 구조, API 명세, 예외 처리 |
| [04-data-analysis](docs/04-data-analysis.md) | 샘플 데이터 분석 |
| [05-test-plan](docs/05-test-plan.md) | 테스트 계획 |
| [06-commit-convention](docs/06-commit-convention.md) | 커밋 규칙 |

## AI 활용

Claude Code (Claude Opus 5.5) 사용. 상세: [docs/AI_USAGE.md](docs/AI_USAGE.md)
