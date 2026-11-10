#!/bin/bash
# PostToolUse(Edit|Write): Java 파일을 고친 직후 컴파일만 확인한다. (전체 테스트는 Stop hook에서)
# 여러 파일에 걸친 수정 중에는 일시적으로 컴파일이 깨질 수 있으므로, 막지 않고 모델에게 알리기만 한다.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

input="$(cat)"
path="$(extract_file_path "$input")"

case "$path" in
  *.java) ;;
  *) exit 0 ;;
esac

cd "$PROJECT_DIR" || exit 0
out="$(./gradlew compileJava compileTestJava -q --console=plain 2>&1)"
status=$?

if [ $status -ne 0 ]; then
  summary="$(printf '%s\n' "$out" | grep -E 'error:|warning: \[|\.java:[0-9]+|What went wrong|^>' | head -20 | json_escape)"
  [ -z "$summary" ] && summary="$(printf '%s\n' "$out" | tail -15 | json_escape)"
  printf '{"hookSpecificOutput":{"hookEventName":"PostToolUse","additionalContext":"컴파일 실패 (%s 수정 후). 여러 파일을 고치는 중이면 나머지 수정 후 다시 확인하세요.\\n%s"},"systemMessage":"컴파일 실패: %s"}\n' \
    "$(basename "$path")" "$summary" "$(basename "$path")"
fi
exit 0
