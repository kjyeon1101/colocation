#!/bin/bash
# Stop: 작업을 끝내기 전에 전체 테스트가 통과하는지 확인한다.
#  - src/ 아래 파일이 마지막 통과 이후 바뀌지 않았다면 건너뛴다 (질문·조회만 한 턴을 방해하지 않기 위해).
#  - 실패하면 종료를 막고(decision: block) 실패 내용을 모델에게 돌려줘 스스로 고치게 한다.
#  - 이미 한 번 막은 뒤(stop_hook_active)에도 실패하면 무한 반복을 피하려고 종료를 허용하되 사용자에게 경고한다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

input="$(cat)"
cd "$PROJECT_DIR" || exit 0

marker="build/.verify-passed"
mkdir -p build

if [ -f "$marker" ]; then
  changed="$(find src -type f -newer "$marker" 2>/dev/null | head -1)"
  [ -z "$changed" ] && exit 0
else
  # 한 번도 통과한 적이 없으면, src에 변경이 있을 때만 검증한다.
  [ -z "$(git status --porcelain -- src 2>/dev/null)" ] && exit 0
fi

out="$(./gradlew test --console=plain 2>&1)"
status=$?

if [ $status -eq 0 ]; then
  touch "$marker"
  printf '{"systemMessage":"자동 검증 통과: ./gradlew test 성공"}\n'
  exit 0
fi

summary="$(printf '%s\n' "$out" | grep -E 'FAILED|tests completed|What went wrong|error:|\.java:[0-9]+' | head -30 | json_escape)"
[ -z "$summary" ] && summary="$(printf '%s\n' "$out" | tail -20 | json_escape)"

if printf '%s' "$input" | grep -q '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  printf '{"systemMessage":"자동 검증 실패가 해결되지 않은 채 종료합니다. ./gradlew test를 직접 확인하세요."}\n'
  exit 0
fi

printf '{"decision":"block","reason":"작업을 끝내기 전 ./gradlew test가 실패했습니다. 실패 원인을 고친 뒤 다시 완료하세요. 테스트 기대값을 바꿔 통과시키지 마세요(CLAUDE.md 10.4).\\n%s"}\n' "$summary"
exit 0
