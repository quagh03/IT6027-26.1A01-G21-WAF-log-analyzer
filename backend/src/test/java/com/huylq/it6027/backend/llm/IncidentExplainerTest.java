package com.huylq.it6027.backend.llm;

import com.huylq.it6027.backend.config.LlmProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentExplainerTest {

  @Mock
  LlmClient llmClient;
  @Mock
  IncidentExplainStore store;

  @Test
  void disabledProfileDoesNotCallTheModel() {
    IncidentExplainer explainer = new IncidentExplainer(LlmProperties.of(false), llmClient, store);

    explainer.explain(4L);

    verify(llmClient, never()).complete(org.mockito.ArgumentMatchers.any());
    verify(store, never()).promptIfPending(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void modelErrorMarksFailedWithoutThrowing() {
    IncidentExplainer explainer = new IncidentExplainer(LlmProperties.of(true), llmClient, store);
    when(store.promptIfPending(4L)).thenReturn("prompt");
    when(llmClient.complete("prompt")).thenThrow(new IllegalStateException("connection refused"));

    explainer.explain(4L);

    verify(store).fail(4L, "connection refused");
    verify(store, never()).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void successStoresTheModelText() {
    IncidentExplainer explainer = new IncidentExplainer(LlmProperties.of(true), llmClient, store);
    when(store.promptIfPending(4L)).thenReturn("prompt");
    when(llmClient.complete("prompt")).thenReturn("Giải thích\nCONFIDENCE: LOW");

    explainer.explain(4L);

    verify(store).complete(4L, "Giải thích\nCONFIDENCE: LOW");
  }
}
