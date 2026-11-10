#!/bin/bash
# PreToolUse(Edit|Write): 수정 직전 점검.
#  - SPEC.md 수정은 차단한다 (명세가 판단 기준이므로 에이전트가 바꾸면 안 된다).
#  - 기존 테스트 파일 수정은 막지 않고, 규칙을 상기시키는 경고를 모델에게 전달한다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

input="$(cat)"
path="$(extract_file_path "$input")"
[ -z "$path" ] && exit 0

case "$path" in
  */SPEC.md|SPEC.md)
    msg="SPEC.md는 서비스 명세(판단 기준)라 수정할 수 없습니다. 명세가 모호하거나 코드와 다르면 사용자에게 물어보세요."
    printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$msg"
    exit 0
    ;;
esac

case "$path" in
  */src/test/*)
    # 새 테스트 파일을 만드는 경우는 경고하지 않는다.
    if [ -f "$path" ]; then
      msg="기존 테스트 파일을 수정합니다. CLAUDE.md 10.4 규칙: 테스트를 통과시키려고 기대값을 바꾸지 마세요. 기대값이 정말 틀렸다면 바꾸되, 그 이유와 근거(SPEC 절)를 evidence에 적어야 합니다."
      printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","additionalContext":"%s"},"systemMessage":"기존 테스트 파일 수정: %s"}\n' "$msg" "$(basename "$path")"
    fi
    ;;
esac
exit 0
