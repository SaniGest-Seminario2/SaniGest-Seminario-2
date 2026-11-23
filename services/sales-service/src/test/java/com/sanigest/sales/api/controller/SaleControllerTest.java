package com.sanigest.sales.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.api.dto.CreateSaleLineRequest;
import com.sanigest.sales.api.dto.CreateSaleRequest;
import com.sanigest.sales.application.dto.CreatedSaleResponse;
import com.sanigest.sales.application.usecase.sale.CreateSaleIdempotently;
import com.sanigest.sales.domain.enums.SaleMode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

class SaleControllerTest {
  private final CreateSaleIdempotently creation = mock(CreateSaleIdempotently.class);
  private final SaleController controller = new SaleController(creation, new ObjectMapper());

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "a b", "a,b", "á", "a\tb", "a\nb"})
  void rejectsInvalidKey(String key) {
    var headers = new HttpHeaders();
    if (key != null) {
      headers.add("Idempotency-Key", key);
    }
    assertInvalid(headers);
  }

  @Test
  void rejectsOversizedAndRepeatedKeys() {
    var headers = new HttpHeaders();
    headers.add("Idempotency-Key", "a".repeat(129));
    assertInvalid(headers);
    headers.set("Idempotency-Key", "a");
    headers.add("Idempotency-Key", "b");
    assertInvalid(headers);
  }

  @Test
  void returnsTheStoredStatusAndBody() {
    var command =
        new CreateSaleRequest(
            UUID.randomUUID(),
            List.of(new CreateSaleLineRequest(UUID.randomUUID(), BigDecimal.ONE)),
            null,
            SaleMode.CASH);
    String key = "a".repeat(124) + "._~-";
    String body = "{\"client\":{\"name\":\"José\"}}";
    when(creation.execute(eq(key), eq(command.toCommand()), any()))
        .thenReturn(new CreatedSaleResponse(UUID.randomUUID(), 201, body));
    var headers = new HttpHeaders();
    headers.add("idempotency-key", key);
    var response = controller.create(headers, command);
    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody()).isEqualTo(body);
  }

  private void assertInvalid(HttpHeaders headers) {
    assertThatThrownBy(() -> controller.create(headers, null))
        .isInstanceOf(InvalidIdempotencyKey.class);
    verifyNoInteractions(creation);
  }
}
