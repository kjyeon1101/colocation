#!/bin/bash
# hook 스크립트 공용 함수. jq 없이 동작하도록 순수 bash/sed만 사용한다.

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

# stdin(JSON)에서 tool_input.file_path를 뽑아 슬래시(/) 경로로 돌려준다.
extract_file_path() {
  printf '%s' "$1" \
    | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' \
    | head -1 \
    | sed -e 's/^"file_path"[[:space:]]*:[[:space:]]*"//' -e 's/"$//' -e 's/\\\{1,\}/\//g'
}

# 여러 줄 문자열을 JSON 문자열 값으로 쓸 수 있게 이스케이프한다.
json_escape() {
  sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\r//g' -e 's/\t/ /g' \
    | awk 'BEGIN{ORS=""} NR>1{print "\\n"} {print}'
}
