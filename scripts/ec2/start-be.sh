#!/usr/bin/env bash
# EC2에서 prod BE를 재시작한다. 비밀값은 /home/ubuntu/apps/be.env 에만 둔다.
set -euo pipefail

APP_DIR="/home/ubuntu/apps"
JAR_PATH="${APP_DIR}/career-dungeon.jar"
ENV_FILE="${APP_DIR}/be.env"
LOG_FILE="/home/ubuntu/be.log"

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "JAR not found: ${JAR_PATH}" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Env file not found: ${ENV_FILE}" >&2
  echo "Copy scripts/ec2/be.env.example to ${ENV_FILE} and fill values." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

pkill -f 'career-dungeon.jar|career-dungeon-0.0.1-SNAPSHOT.jar' 2>/dev/null || true
sleep 2

: > "${LOG_FILE}"
nohup java -jar "${JAR_PATH}" \
  --server.port=8000 \
  --server.forward-headers-strategy=framework \
  >> "${LOG_FILE}" 2>&1 &

echo "STARTED_PID=$!"

# EC2에서 기동이 12초를 넘는 경우가 있어, 최대 60초까지 폴링한다.
for _ in $(seq 1 30); do
  if grep -q "Started CareerDungeonApplication" "${LOG_FILE}"; then
    echo "BE start OK"
    tail -n 20 "${LOG_FILE}"
    exit 0
  fi
  if grep -q "Application run failed" "${LOG_FILE}"; then
    break
  fi
  sleep 2
done

echo "BE start failed — last log lines:" >&2
tail -n 80 "${LOG_FILE}" >&2
exit 1
