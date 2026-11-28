package com.sanigest.catalog.domain.model.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanigest.catalog.domain.exception.supplier.InvalidSupplierDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SupplierDetailsTest {
  @Test
  void stripsTextAndPreservesTaxIdSeparators() {
    var details =
        new SupplierDetails(
            " Office ", "  1-23\t", " Laura ", "(809) 555-0118", "sales@example.do");

    assertThat(details.name()).isEqualTo("Office");
    assertThat(details.contact()).isEqualTo("Laura");
    assertThat(details.taxId()).isEqualTo("1-23");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" \t "})
  void acceptsAbsentOrBlankTaxIdAsNull(String taxId) {
    assertThat(new SupplierDetails("Office", taxId, "Laura", null, null).taxId()).isNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" \t "})
  void rejectsMissingOrBlankNameAndContact(String blank) {
    assertThatThrownBy(() -> new SupplierDetails(blank, null, "Laura", null, null))
        .isInstanceOf(InvalidSupplierDetails.class);
    assertThatThrownBy(() -> new SupplierDetails("Office", null, blank, null, null))
        .isInstanceOf(InvalidSupplierDetails.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "123-45", "() -.- ", "809-555-0118+", "abcdefghij"})
  void rejectsInvalidPhone(String phone) {
    assertThatThrownBy(() -> new SupplierDetails("Office", null, "Laura", phone, null))
        .isInstanceOf(InvalidSupplierDetails.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "@example.do", "sales@.", "a b@example.do", "a@@example.do"})
  void rejectsInvalidEmail(String email) {
    assertThatThrownBy(() -> new SupplierDetails("Office", null, "Laura", null, email))
        .isInstanceOf(InvalidSupplierDetails.class);
  }

  @Test
  void rejectsOversizedDetails() {
    assertThatThrownBy(() -> new SupplierDetails("x".repeat(161), null, "Laura", null, null))
        .isInstanceOf(InvalidSupplierDetails.class);
    assertThatThrownBy(() -> new SupplierDetails("Office", "x".repeat(65), "Laura", null, null))
        .isInstanceOf(InvalidSupplierDetails.class);
    assertThatThrownBy(() -> new SupplierDetails("Office", null, "x".repeat(161), null, null))
        .isInstanceOf(InvalidSupplierDetails.class);
    assertThatThrownBy(
            () -> new SupplierDetails("Office", null, "Laura", "+" + "1".repeat(32), null))
        .isInstanceOf(InvalidSupplierDetails.class);
    assertThatThrownBy(() ->
            new SupplierDetails("Office", null, "Laura", null, "x".repeat(311) + "@example.do"))
        .isInstanceOf(InvalidSupplierDetails.class);
  }
}
