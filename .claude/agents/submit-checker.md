---
name: submit-checker
description: 제출 직전 docs/02-work-plan.md 4절 제출 체크리스트를 실제로 실행해 검증한다.
tools: Read, Grep, Glob, Bash
model: sonnet
---
`docs/02-work-plan.md` 4절 체크리스트를 순서대로 실행하고 항목별 통과/실패와 증거(명령 출력 요약)를 보고한다.

- `./gradlew clean build`
- `./gradlew bootRun`을 백그라운드로 띄우고 기동 로그의 `success=37, failure=3`, WARN 3건 확인
- README의 curl 예시 5종 실행 (응답 코드·본문 요약)
- reload 시나리오: `user.txt`는 절대 직접 수정하지 말 것. 대신 `cp user.txt` 백업 없이 확인 가능한 범위(정상 reload 200)만 실행하고, 파일 변경/이름변경 시나리오는 "수동 확인 필요"로 보고
- `git ls-files | grep -E '\.(pdf|png)$'` 결과가 비어 있는지 (과제 원본 미포함)
- 끝나면 띄운 bootRun 프로세스를 반드시 종료

파일은 수정하지 않는다.
