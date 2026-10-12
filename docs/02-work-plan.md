# 02. 작업 계획서

## 1. 일정 (총 90분, T = 문서 수령 시각)

| 단계 | 시간 | 작업 | 산출물 / 완료 기준 | 커밋 |
|---|---|---|---|---|
| 0. 분석·설계 | T+0 ~ T+15 | 요구사항·데이터 분석, 설계, 커밋 규칙, CLAUDE.md | `docs/*`, `CLAUDE.md` | `docs: 과제 분석 및 설계 문서 작성` |
| 1. 스캐폴딩 | ~T+20 | Spring Boot 프로젝트 생성, `.gitignore`, 설정 파일 | `./gradlew build` 성공 | `chore: Spring Boot 프로젝트 초기 구성` |
| 2. 도메인·파서 (TDD) | ~T+35 | `User`, `UserLineParser`, `ParseFailure`, 파서 단위 테스트 | 정상 행 + 오류 3종 + 경계값 테스트 통과 | `feat: user.txt 행 파서 구현` |
| 3. 로더·저장소 | ~T+45 | `UserFileLoader`, `UserRepository`, 기동 시 적재 | 기동 로그에 `success=37, failure=3` | `feat: 기동 시 사용자 데이터 적재` |
| 4. 조회 API | ~T+55 | 목록(페이징)·단건·검색·파싱실패 API | curl로 4개 API 정상 응답 | `feat: 사용자 조회 API 구현` |
| 5. 재적재 API | ~T+60 | `POST /reload`, 원자적 교체, 실패 시 기존 데이터 유지 | 파일 수정 → reload → 반영 확인 | `feat: 사용자 데이터 재적재 API 구현` |
| 6. 예외 처리 | ~T+68 | `GlobalExceptionHandler`, 파라미터 검증 | 400/404/500 응답 형식 통일 | `feat: 전역 예외 처리 및 요청 검증` |
| 7. 테스트 보강 | ~T+75 | 로더(임시 파일), 컨트롤러(MockMvc) 테스트 | `./gradlew test` 전체 통과 | `test: ...` |
| 8. 마무리 | ~T+82 | README(실행법·API·설계 결정), `AI_USAGE.md` 갱신 | 문서 최신화 | `docs: README 및 AI 활용 내역 정리` |
| 9. 제출 | ~T+85 | 최종 빌드·테스트, push 또는 zip, 메일 발송 | 제출 완료 | - |
| 버퍼 | T+85 ~ T+90 | 예비 시간 | | |

## 2. 우선순위 (시간 부족 시 컷라인)

1. **Must** — FR-1~8 전부 동작 + 파싱 실패 로그 + 404/400 처리 + 파서 단위 테스트
2. **Should** — 원자적 스냅샷 교체, 재적재 실패 시 기존 데이터 유지, ProblemDetail 통일, 컨트롤러 테스트
3. **Could** — 로더 통합 테스트 확장, README의 상세 설계 설명

> 단계 6까지 T+70을 넘기면 Could는 버리고 README·제출을 우선한다. **동작하는 제출물 > 완벽한 미제출물.**

## 3. 진행 원칙

- 단계마다 **빌드·테스트 통과 상태에서 커밋** (규칙: [`06-commit-convention.md`](06-commit-convention.md))
- 설계와 달라진 결정은 커밋 바디에 "왜"를 남기고, 필요 시 `03-design.md` 갱신
- AI(Claude Code) 생성 코드는 **읽고 이해한 뒤** 커밋. 인터뷰에서 모든 줄을 설명할 수 있어야 함
- AI 활용 내역은 단계마다 `AI_USAGE.md`에 한 줄씩 기록

## 4. 제출 체크리스트

- [ ] `./gradlew clean build` 성공 (테스트 포함)
- [ ] `./gradlew bootRun` 후 기동 로그에 파싱 실패 3건 WARN, `success=37, failure=3`
- [ ] 5개 API curl 확인 (README 예시 그대로 실행)
- [ ] `user.txt` 수정 → `POST /api/users/reload` → 반영 확인 → 원복
- [ ] `user.txt` 이름 변경 → reload → 500 응답 + 기존 데이터 유지 확인 → 원복
- [ ] README: 실행 방법, API 목록, 설계 결정·가정, **사용한 AI 도구**
- [ ] 과제 원본(pdf/png)이 공개 레포에 포함되지 않았는지 확인
- [ ] `git log`로 커밋 이력 확인, push 또는 zip(`build/`, `.gradle/`, `.idea/` 제외)
- [ ] 메일 본문에 레포 링크/zip + 사용 AI 도구 명시
