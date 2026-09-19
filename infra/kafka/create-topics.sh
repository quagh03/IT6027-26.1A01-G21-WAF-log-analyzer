#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
KTOPICS="${KAFKA_TOPICS:-raw-web-logs,security-alerts}"

wait_for_kafka() {
  local i
  for i in $(seq 1 40); do
    if /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server "${BOOTSTRAP}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "Kafka did not become ready at ${BOOTSTRAP}" >&2
  return 1
}

create_topic() {
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "$1" \
    --partitions 1 \
    --replication-factor 1
}

wait_for_kafka

IFS=',' read -r -a topics <<< "${KTOPICS}"
for topic in "${topics[@]}"; do
  create_topic "${topic}"
done

echo "Topics:"
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "${BOOTSTRAP}" --list
