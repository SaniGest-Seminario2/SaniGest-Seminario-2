package com.sanigest.sales.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sanigest.sales.domain.enums.PaymentMethod;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class InvoicePaymentTest {
  private static final InvoicePayment.Line CASH =
      new InvoicePayment.Line(PaymentMethod.CASH, BigDecimal.ONE, null);

  @Test
  void normalizesAndTotalsMixedPaymentLines() {
    var transfer =
        new InvoicePayment.Line(PaymentMethod.TRANSFER, new BigDecimal("2.350"), " REF ");
    var lines = new ArrayList<>(List.of(CASH, transfer));
    var request = new InvoicePayment.Request(" Cliente ", BigDecimal.TEN, lines, " Nota ");
    lines.clear();

    assertThat(request.clientName()).isEqualTo("Cliente");
    assertThat(request.balance()).isEqualTo(new BigDecimal("10.00"));
    assertThat(request.notes()).isEqualTo("Nota");
    assertThat(request.lines()).containsExactly(CASH, transfer);
    assertThat(transfer.reference()).isEqualTo("REF");
    assertThat(request.total()).isEqualTo(new BigDecimal("3.35"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"0", "-1", "1.001", "100000000000000000"})
  void rejectsInvalidAmounts(String raw) {
    var amount = raw == null ? null : new BigDecimal(raw);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InvoicePayment.Line(PaymentMethod.CASH, amount, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InvoicePayment.Request("Cliente", amount, List.of(CASH), null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "Cliente\u0000"})
  void requiresAValidClientName(String name) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InvoicePayment.Request(name, BigDecimal.TEN, List.of(CASH), null));
  }

  @Test
  void rejectsInvalidLineCollections() {
    assertThatIllegalArgumentException().isThrownBy(() -> requestWith(null));
    assertThatIllegalArgumentException().isThrownBy(() -> requestWith(List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> requestWith(Collections.singletonList(null)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> requestWith(Collections.nCopies(101, CASH)));
  }

  @Test
  void rejectsMissingMethodCashReferencesAndOverlongText() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InvoicePayment.Line(null, BigDecimal.ONE, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InvoicePayment.Line(PaymentMethod.CASH, BigDecimal.ONE, "REF"));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new InvoicePayment.Line(PaymentMethod.TRANSFER, BigDecimal.ONE, "x".repeat(251)));
  }

  private static InvoicePayment.Request requestWith(List<InvoicePayment.Line> lines) {
    return new InvoicePayment.Request("Cliente", BigDecimal.TEN, lines, null);
  }
}
