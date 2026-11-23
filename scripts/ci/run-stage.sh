#!/bin/sh
set -u

scope=$1
stage=$2
shift 2
started=$(date +%s)
printf '::group::%s / %s\n' "$scope" "$stage"
result=0
"$@" || result=$?
printf '::endgroup::\n'
duration=$(( $(date +%s) - started ))
if [ "$result" -eq 0 ]; then
    status=Passed
    level=notice
else
    status=Failed
    level=error
fi
printf '::%s::%s / %s — %s (%ss, exit %s). See the step log for details.\n' \
    "$level" "$scope" "$stage" "$status" "$duration" "$result"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    printf '### %s / %s\n\n**%s** in %s s (exit %s). Details in the step log.\n\n' \
        "$scope" "$stage" "$status" "$duration" "$result" >>"$GITHUB_STEP_SUMMARY" ||
        printf '::warning::Could not write the stage summary.\n'
fi
exit "$result"
