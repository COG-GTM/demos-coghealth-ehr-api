#!/usr/bin/env bash
# Runs the hr-pto-sync interface contract tests and prints the report.
#
#   scripts/interface-contract.sh [yyyyMMdd]
#
# Without an argument the latest file under src/main/resources/interfaces/hr/export is checked.
# Exit code is the surefire exit code (non-zero when the contract fails).
set -u
cd "$(dirname "$0")/.."

EXPORT_DATE="${1:-${HR_EXPORT_DATE:-}}"
REPORT_DIR="target/interface-contract"

mvn -B test -Dgroups=interface-contract -Dhr.export.date="${EXPORT_DATE}"
STATUS=$?

echo
if [ -f "${REPORT_DIR}/report.md" ]; then
  cat "${REPORT_DIR}/report.md"
  echo
  echo "JSON report: ${REPORT_DIR}/report.json"
else
  echo "No report written to ${REPORT_DIR}"
fi

exit ${STATUS}
