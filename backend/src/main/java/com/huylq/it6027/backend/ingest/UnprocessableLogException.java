package com.huylq.it6027.backend.ingest;

/**
 * Poison / malformed access log — skip the record and let the Kafka offset advance.
 */
public class UnprocessableLogException extends RuntimeException {

  public UnprocessableLogException(String message) {
    super(message);
  }

  public UnprocessableLogException(String message, Throwable cause) {
    super(message, cause);
  }
}
