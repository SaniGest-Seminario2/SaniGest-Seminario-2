package com.sanigest.catalog.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.catalog.domain.enums.CatalogItemType;
import com.sanigest.catalog.domain.enums.SupplierPaymentTerms;
import com.sanigest.catalog.domain.enums.SupplierSupplyType;
import com.sanigest.catalog.domain.exception.InvalidSupplierDetails;
import com.sanigest.catalog.domain.exception.InvalidSupplierPatch;
import com.sanigest.catalog.domain.model.Supplier;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class SupplierCommercialDtoTest {
  private final JsonMapper json = JsonMapper.shared();

  @Test
  void distinguishesLegacyFromCommercialCreate() {
    var legacy =
        create(
            """
            {"name":"Office","contact":"Alex","taxId":"OLD-3"}
            """);
    assertThat(legacy.details().profile().paymentTerms()).isNull();
    assertThat(legacy.details().taxId()).isEqualTo("OLD-3");

    var expanded =
        create(
            """
            {"name":"Office","contact":"Alex","taxId":"001 0000001-2","supplyType":"PRODUCT"}
            """);
    assertThat(expanded.details().profile().paymentTerms()).isEqualTo(SupplierPaymentTerms.CASH);
    assertThat(expanded.details().taxId()).isEqualTo("00100000012");
    assertThat(expanded.details().profile().supplies(CatalogItemType.PRODUCT)).isTrue();
    assertThat(expanded.details().profile().supplies(CatalogItemType.SERVICE)).isFalse();
    assertThatThrownBy(() ->
            create(
                """
                {"name":"Office","contact":"Alex","taxId":"OLD-3","supplyType":"PRODUCT"}
                """))
        .isInstanceOf(InvalidSupplierDetails.class);
  }

  @Test
  void appliesTypedPatchTogetherAndClearsOnlyExplicitFields() {
    var supplier =
        create(
            """
            {"name":"Office","contact":"Alex","supplyType":"BOTH","paymentTerms":"CREDIT",
             "creditDays":30,"address":"Santo Domingo","observations":"Temporal"}
            """);
    assertThatThrownBy(() -> patch(supplier, "{\"paymentTerms\":\"CASH\"}"))
        .isInstanceOf(InvalidSupplierDetails.class);
    var category = UUID.randomUUID();
    var edited =
        patch(
            supplier,
            """
            {"supplyType":"SERVICE","paymentTerms":"BOTH","categoryId":"%s","creditDays":0,"observations":null}
            """
                .formatted(category));
    var profile = edited.details().profile();
    assertThat(profile.supplyType()).isEqualTo(SupplierSupplyType.SERVICE);
    assertThat(profile.categoryId()).isEqualTo(category);
    assertThat(profile.creditDays()).isZero();
    assertThat(profile.address()).isEqualTo("Santo Domingo");
    assertThat(profile.observations()).isNull();
    assertThat(edited.details().contact()).isEqualTo("Alex");
    assertThat(
            patch(
                    edited,
                    """
                    {"paymentTerms":"CASH","creditDays":null}
                    """)
                .details()
                .profile()
                .creditDays())
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"companyId\":null}", "{\"revision\":1}", "{\"creditDays\":\"30\"}",
        "{\"creditDays\":2.1}", "{\"creditDays\":2147483648}", "{\"categoryId\":\"1-1-1-1-1\"}",
        "{\"paymentTerms\":1}", "{\"supplyType\":\"UNKNOWN\"}", "{}"
      })
  void rejectsUnknownProtectedAndMistypedPatchFields(String input) {
    assertThatThrownBy(() -> SupplierPatchRequest.details(json.readTree(input)))
        .isInstanceOf(InvalidSupplierPatch.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"name\":null}", "{\"contact\":null}", "{\"creditDays\":-1}"})
  void rejectsInvalidResultingDetails(String input) {
    var supplier =
        create(
            """
            {"name":"Office","contact":"Alex","paymentTerms":"CREDIT"}
            """);
    assertThatThrownBy(() -> patch(supplier, input)).isInstanceOf(InvalidSupplierDetails.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "\"-1\"", "\"1.01234\"", "\"1000000000000000\"", "\"1e1\""})
  void rejectsCostsOutsideTheDecimalStringContract(String cost) {
    var node =
        json.readTree(
            """
            {"items":[{"catalogItemId":"%s","offeredCost":%s}]}
            """
                .formatted(UUID.randomUUID(), cost));
    assertThatThrownBy(() -> SupplierItemsRequest.parse(node))
        .isInstanceOf(InvalidSupplierPatch.class);
  }

  @Test
  void preservesUnknownAndZeroCosts() {
    var request =
        SupplierItemsRequest.parse(
            json.readTree(
                """
                {"items":[{"catalogItemId":"%s","offeredCost":null},
                          {"catalogItemId":"%s","offeredCost":"0","supplierReference":"  ABC-1  "}]}
                """
                    .formatted(UUID.randomUUID(), UUID.randomUUID())));
    assertThat(request.items().get(0).offeredCost()).isNull();
    assertThat(request.items().get(1).offeredCost()).isEqualByComparingTo("0.0000");
    assertThat(request.items().get(1).supplierReference()).isEqualTo("ABC-1");
  }

  private Supplier create(String input) {
    return Supplier.register(
        UUID.randomUUID(), CreateSupplierRequest.parse(json.readTree(input)).details());
  }

  private Supplier patch(Supplier supplier, String input) {
    return supplier.updateDetails(SupplierPatchRequest.details(json.readTree(input)));
  }
}
