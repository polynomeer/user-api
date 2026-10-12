---
name: step
description: 작업 계획(docs/02-work-plan.md)의 N단계를 구현→테스트→명세검증→커밋→AI_USAGE 기록까지 수행한다. "/step 4"처럼 사용.
---
인자: 단계 번호 `$ARGUMENTS`

1. `docs/02-work-plan.md`에서 해당 단계의 작업·완료 기준·커밋 제목을 확인하고, `docs/03-design.md`의 관련 절을 읽는다. 범위를 넓히지 않는다.
2. 테스트 먼저 작성(TDD) → 구현 → `./gradlew test` 통과.
3. `spec-reviewer` 서브에이전트에 "N단계 범위" 검증을 맡기고, 누락/불일치를 수정한다. 설계와 달라지면 이유를 사용자에게 먼저 말하고 `docs/03-design.md`를 갱신한다.
4. 완료 기준(예: curl 응답)을 실제로 실행해 확인한다.
5. `docs/AI_USAGE.md` 해당 단계 행을 채운다.
6. 변경 요약과 커밋 메시지 초안(docs/06-commit-convention.md 형식)을 보여주고, 사용자 확인 후 커밋한다.
7. 마지막에 계획 대비 경과 시간 판단을 한 줄로 알린다 (T+70 초과 시 Could 포기 권고).
