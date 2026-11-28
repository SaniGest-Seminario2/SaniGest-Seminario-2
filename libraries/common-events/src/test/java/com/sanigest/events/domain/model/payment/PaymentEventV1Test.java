package com.sanigest.events.domain.model.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sanigest.events.domain.enums.payment.PaymentEventTypeV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class PaymentEventV1Test {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PAYMENT =
      """
      {
        "eventId": "00000000-0000-4000-8000-000000000001",
        "eventType": "ReceivablePaymentRegisteredV1",
        "version": 1,
        "occurredAt": "2026-11-26T10:00:00-04:00",
        "companyId": "00000000-0000-4000-8000-000000000002",
        "sourceId": "00000000-0000-4000-8000-000000000003",
        "traceId": "payment-request-1",
        "paymentId": "00000000-0000-4000-8000-000000000004",
        "accountId": "00000000-0000-4000-8000-000000000006",
        "method": "CASH",
        "amount": "55.00",
        "currency": "DOP",
        "futureField": true
      }
      """;
  private static final String ORIGINAL =
      """
      "originalEventId": "00000000-0000-4000-8000-000000000007",
      "originalSourceId": "00000000-0000-4000-8000-000000000008",
      """;

  @ParameterizedTest
  @EnumSource(PaymentEventTypeV1.class)
  void preservesWireContract(PaymentEventTypeV1 type) {
    String payload = PAYMENT.replace("ReceivablePaymentRegisteredV1", type.name());
    if (type.isReversal()) {
      payload = payload.replace("{", "{" + ORIGINAL);
    }

    PaymentEventV1 event = JSON.readValue(payload, PaymentEventV1.class);
    String serialized = JSON.writeValueAsString(event);
    var tree = JSON.readTree(serialized);

    assertEquals(type.name(), tree.path("eventType").asString());
    assertEquals(1, tree.path("version").asInt());
    assertEquals("55.00", tree.path("amount").asString());
    assertEquals("2026-11-26T14:00:00Z", tree.path("occurredAt").asString());
    assertEquals(type.isReversal(), tree.has("originalEventId"));
    assertEquals(type.isReversal(), tree.has("originalSourceId"));
    assertFalse(tree.has("shiftId"));
    assertFalse(tree.has("futureField"));
    assertEquals(event, JSON.readValue(serialized, PaymentEventV1.class));
    assertEquals(
        type.name().startsWith("Receivable")
            ? "sales.receivable-payment.v1"
            : "expense.payable-payment.v1",
        type.topic());
  }

  @Test
  void rejectsInvalidFinancialFacts() {
    assertInvalid(PAYMENT.replace("\"version\": 1", "\"version\": 2"));
    assertInvalid(PAYMENT.replace("55.00", "0"));
    assertInvalid(PAYMENT.replace("55.00", "-1"));
    assertInvalid(PAYMENT.replace("DOP", "INVALID"));
    assertInvalid(PAYMENT.replace("payment-request-1", " "));
    assertInvalid(PAYMENT.replace("payment-request-1", ""));
    assertInvalid(PAYMENT.replace("\"payment-request-1\"", "null"));
    assertInvalid(PAYMENT.replace("\"55.00\"", "null"));
    assertInvalid(PAYMENT.replace("{", "{" + ORIGINAL));
  }

  @Test
  void rejectsReversalsWithoutDistinctOriginalIdentifiers() {
    String reversal =
        PAYMENT.replace("ReceivablePaymentRegisteredV1", "ReceivablePaymentReversedV1");
    assertInvalid(reversal);
    assertInvalid(reversal.replace("{", "{" + ORIGINAL.replace("000000000007", "000000000001")));
    assertInvalid(reversal.replace("{", "{" + ORIGINAL.replace("000000000008", "000000000003")));
  }

  private static void assertInvalid(String payload) {
    assertThrows(JacksonException.class, () -> JSON.readValue(payload, PaymentEventV1.class));
  }
}
