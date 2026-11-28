package com.sanigest.catalog.api.dto.catalogitem;

import static com.sanigest.catalog.domain.enums.catalogitem.CatalogItemType.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.catalog.domain.model.catalogitem.CatalogItem;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

class CatalogItemDtoTest {
  private final JsonMapper mapper = JsonMapper.shared();

  @Test
  void bindsAndSerializesMoneyAsAnExactJsonNumber() {
    var request =
        mapper.readValue(
            """
            {"name":"Chair","type":"PRODUCT","price":999999999999.99,"unitCode":"UNIT","taxClassificationCode":"EXEMPT"}
            """,
            CreateCatalogItemRequest.class);
    assertThat(request.name()).isEqualTo("Chair");
    assertThat(request.type()).isEqualTo(PRODUCT);
    assertThat(request.price()).isEqualTo(new BigDecimal("999999999999.99"));

    var company = UUID.randomUUID();
    var item = CatalogItem.register(company, request.name(), request.type(), request.price());
    var json = mapper.writeValueAsString(CatalogItemResponse.from(item));
    assertThat(json).contains("\"price\":999999999999.99");
    assertThat(mapper.readValue(json, CatalogItemResponse.class))
        .isEqualTo(
            new CatalogItemResponse(
                item.id(), company, "Chair", PRODUCT, request.price(), "DOP", true));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "{\"name\":\"Chair\",\"type\":\"PRODUCT\",\"price\":1}",
        "{\"name\":\"Chair\",\"type\":\"PRODUCT\",\"price\":1,\"unitCode\":null,\"taxClassificationCode\":\"EXEMPT\"}",
        "{\"price\":\"3200.00\"}",
        "{\"price\":\"invalid\"}",
        "{\"price\":null}",
        "{\"price\":{}}",
        "{\"type\":0}",
        "{\"type\":\"OTHER\"}",
        "{\"name\":7}",
        "{\"name\":\"x\",\"name\":\"y\"}",
        "{\"id\":null}",
        "{\"companyId\":null}",
        "{\"currency\":\"DOP\"}",
        "{\"active\":true}",
        "{\"stock\":0}"
      })
  void rejectsInvalidTokensAndUnknownOrDuplicateFields(String json) {
    assertThatThrownBy(() -> mapper.readValue(json, CreateCatalogItemRequest.class))
        .isInstanceOf(MismatchedInputException.class);
  }
}
