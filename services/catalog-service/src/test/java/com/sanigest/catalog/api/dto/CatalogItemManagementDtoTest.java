package com.sanigest.catalog.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

class CatalogItemManagementDtoTest {
  private final JsonMapper mapper = JsonMapper.shared();

  @Test
  void preservesFieldPresenceAndExactPrices() {
    var patch =
        mapper
            .readValue("{\"description\":null,\"price\":3200.50}", CatalogItemPatchRequest.class)
            .patch();
    assertThat(patch.name()).isEmpty();
    assertThat(patch.description().orElseThrow()).isEmpty();
    assertThat(patch.price()).contains(new BigDecimal("3200.50"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "[]",
        "{\"name\":null}",
        "{\"description\":1}",
        "{\"price\":null}",
        "{\"price\":\"3.00\"}",
        "{\"name\":\"A\",\"name\":\"B\"}",
        "{\"type\":\"SERVICE\"}",
        "{\"companyId\":\"company\"}",
        "{\"id\":\"item\"}",
        "{\"currency\":\"USD\"}",
        "{\"revision\":1}",
        "{\"active\":false}"
      })
  void rejectsInvalidOrReservedPatchFields(String json) {
    assertThatThrownBy(() -> mapper.readValue(json, CatalogItemPatchRequest.class))
        .isInstanceOf(MismatchedInputException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"active\":null}",
        "{\"active\":\"true\"}",
        "{\"active\":0}",
        "{\"active\":true,\"name\":\"Silla\"}",
        "{\"active\":true,\"active\":false}"
      })
  void rejectsInvalidStatusPayloads(String json) {
    assertThatThrownBy(() -> mapper.readValue(json, CatalogItemStatusRequest.class))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void acceptsOnlyJsonBooleans() {
    assertThat(mapper.readValue("{\"active\":true}", CatalogItemStatusRequest.class).active())
        .isTrue();
    assertThat(mapper.readValue("{\"active\":false}", CatalogItemStatusRequest.class).active())
        .isFalse();
  }
}
