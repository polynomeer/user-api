#!/usr/bin/env bash
# Stop 훅: 소스에 미커밋 변경이 있으면 테스트를 돌리고, 실패 시 종료를 막고 로그를 모델에 돌려준다.
input=$(cat)
# 이미 한 번 막힌 뒤의 재시도면 통과 (무한 루프 방지)
echo "$input" | grep -q '"stop_hook_active": *true' && exit 0

cd "$CLAUDE_PROJECT_DIR" || exit 0
# 프로덕션 코드 변경이 없으면 생략: 시간 절약 + TDD Red 단계(테스트만 추가)는 허용
[ -z "$(git status --porcelain -- src/main build.gradle settings.gradle)" ] && exit 0

if ! out=$(./gradlew test -q 2>&1); then
  echo "테스트 실패: 통과시킨 뒤 종료하라." >&2
  echo "$out" | tail -40 >&2
  exit 2
fi
exit 0
