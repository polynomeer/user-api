# CLAUDE.md

JAVA 코딩테스트(제한 90분). `user.txt`를 기동 시 메모리에 적재하고 조회·검색·파싱실패 조회·재적재 REST API를 제공하는 Spring Boot 애플리케이션.

## 먼저 읽을 문서

- 요구사항·가정: `docs/01-requirements.md` (FR/NFR/A-x 번호로 참조)
- 작업 순서·시간 배분: `docs/02-work-plan.md` — 현재 단계만 작업하고 범위를 넓히지 말 것
- 설계(패키지, API 명세, 예외 매트릭스): `docs/03-design.md` — 구현은 이 문서를 따른다
- 데이터 특이사항: `docs/04-data-analysis.md` (CRLF, 마지막 행 개행 없음, 오류 행 16·31·39)
- 테스트 케이스: `docs/05-test-plan.md` (계획·이력), `docs/10-test-catalog.md` (현재 전체 목록·요구사항 추적. 테스트 추가 시 갱신)
- 기술 선택 근거: `docs/07-tech-decisions.md` (대안·판단 기준·재검토 조건)
- 진행 기록: `docs/08-process-log.md` (단계별 타임라인·판단·AI 활용·회고)

## 명령어

```bash
./gradlew build          # 빌드 + 테스트
./gradlew test           # 테스트
./gradlew bootRun        # 실행 (작업 디렉터리의 ./user.txt 사용)
```

## 규칙

- **커밋**: `docs/06-commit-convention.md`를 따른다. 제목·바디 모두 한글, 바디에 `## 변경 내용 / ## 변경 이유 / ## 검증`(+선택지가 있었다면 `## 설계 결정`) 필수. 빌드·테스트 통과 상태에서만 커밋
- **`user.txt`는 수정하지 않는다** (테스트는 `@TempDir` 복사본 사용). 오류 행은 의도된 것
- 설계와 다르게 구현해야 하면 먼저 이유를 말하고, 확정되면 `docs/03-design.md`도 갱신
- 단계 완료 시 `docs/AI_USAGE.md`의 해당 행을 채운다
- 과제 원본(루트의 `*.pdf`, `*.png`)은 커밋하지 않는다 (`.gitignore` 처리됨)
- 코드 스타일: Java 21 `record`/`sealed` 활용, Lombok 미사용, 생성자 주입, 로그·에러 메시지는 한글
- 파서는 스프링 비의존 순수 클래스로 유지. 예외를 흐름 제어에 쓰지 말고 `ParseResult`로 반환
