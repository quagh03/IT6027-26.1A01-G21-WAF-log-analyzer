package com.huylq.it6027.backend.ingest;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RawWebLogListener {

  private static final Logger log = LoggerFactory.getLogger(RawWebLogListener.class);

  private final IngestService ingestService;

  public RawWebLogListener(IngestService ingestService) {
    this.ingestService = ingestService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.raw-web-logs}",
      groupId = "${spring.kafka.consumer.group-id}"
  )
  public void onMessage(ConsumerRecord<String, String> record) {
    String rawRef = record.topic() + "-" + record.partition() + "-" + record.offset();
    try {
      ingestService.ingestRaw(record.value(), rawRef);
    } catch (UnprocessableLogException e) {
      log.warn(
          "Skip unprocessable log topic={} partition={} offset={}: {}",
          record.topic(),
          record.partition(),
          record.offset(),
          e.getMessage()
      );
    } catch (Exception e) {
      // Let the container error handler decide retry; do not swallow DB/infra failures.
      log.error(
          "Ingest failed topic={} partition={} offset={}",
          record.topic(),
          record.partition(),
          record.offset(),
          e
      );
      throw e;
    }
  }
}
