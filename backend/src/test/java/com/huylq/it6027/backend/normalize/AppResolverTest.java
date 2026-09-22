package com.huylq.it6027.backend.normalize;

import com.huylq.it6027.backend.entity.Application;
import com.huylq.it6027.backend.ingest.UnprocessableLogException;
import com.huylq.it6027.backend.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppResolverTest {

  @Mock
  ApplicationRepository applicationRepository;

  AppResolver resolver;

  @BeforeEach
  void setUp() {
    Application juice = Application.builder()
        .name("Juice Shop")
        .hostPattern("^(juice\\.lab\\.local|localhost|127\\.0\\.0\\.1)$")
        .riskThreshold(60)
        .enabled(true)
        .build();
    Application shop = Application.builder()
        .name("Juice Shop (shop)")
        .hostPattern("^shop\\.lab\\.local$")
        .riskThreshold(60)
        .enabled(true)
        .build();
    // ids via reflection not needed — identity by name is enough for assert
    when(applicationRepository.findByEnabledTrue()).thenReturn(List.of(juice, shop));
    resolver = new AppResolver(applicationRepository);
    resolver.reload();
  }

  @Test
  void resolvesJuiceAndShopSeparately() {
    assertEquals("Juice Shop", resolver.resolve("juice.lab.local").getName());
    assertEquals("Juice Shop", resolver.resolve("localhost").getName());
    assertEquals("Juice Shop (shop)", resolver.resolve("shop.lab.local").getName());
  }

  @Test
  void stripsPortFromHostHeader() {
    assertEquals("Juice Shop", resolver.resolve("juice.lab.local:80").getName());
  }

  @Test
  void unknownHostFails() {
    assertThrows(UnprocessableLogException.class, () -> resolver.resolve("evil.example"));
  }
}
