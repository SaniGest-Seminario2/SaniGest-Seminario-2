package com.sanigest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanySelectorTest {
  private static final UUID COMPANY = UUID.fromString("e3fc1fd0-42ea-4a0d-9c14-6cbd44592b42");
  private static final String OTHER = "0098a4d2-cf3e-4599-a9da-af9972e50251";

  @Test
  void parsesAndNormalizesAnExplicitSelection() {
    var upperCase = COMPANY.toString().toUpperCase(Locale.ROOT);
    assertThat(CompanySelector.resolve(List.of(upperCase), null, true)).isEqualTo(COMPANY);
    assertThat(CompanySelector.resolve(List.of(upperCase), COMPANY.toString(), false))
        .isEqualTo(COMPANY);
    assertThat(CompanySelector.resolve(List.of(), COMPANY.toString(), false)).isEqualTo(COMPANY);
    assertThat(CompanySelector.resolve(List.of(), null, false)).isNull();
  }

  @Test
  void rejectsMissingDuplicatedMalformedOrInconsistentSelections() {
    assertThatThrownBy(() -> CompanySelector.resolve(List.of(), null, true))
        .isInstanceOf(CompanySelector.InvalidContext.class)
        .hasMessage("COMPANY_CONTEXT_REQUIRED");
    for (var values :
        List.of(
            List.of(""),
            List.of("1-1-1-1-1"),
            List.of(COMPANY + "," + COMPANY),
            List.of(COMPANY.toString(), COMPANY.toString()))) {
      assertThatThrownBy(() -> CompanySelector.resolve(values, null, true))
          .isInstanceOf(CompanySelector.InvalidContext.class)
          .hasMessage("INVALID_COMPANY_CONTEXT");
    }
    assertThatThrownBy(() -> CompanySelector.resolve(List.of(OTHER), COMPANY.toString(), false))
        .isInstanceOf(CompanySelector.InvalidContext.class)
        .hasMessage("COMPANY_CONTEXT_MISMATCH");
  }
}
