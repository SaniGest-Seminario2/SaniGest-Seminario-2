package com.sanigest.events.payment;

public enum PaymentEventTypeV1 {
  ReceivablePaymentRegisteredV1("sales.receivable-payment.v1", false),
  PayablePaymentRegisteredV1("expense.payable-payment.v1", false),
  ReceivablePaymentReversedV1("sales.receivable-payment.v1", true),
  PayablePaymentReversedV1("expense.payable-payment.v1", true);

  private final String topic;
  private final boolean reversal;

  PaymentEventTypeV1(String topic, boolean reversal) {
    this.topic = topic;
    this.reversal = reversal;
  }

  public String topic() {
    return topic;
  }

  public boolean isReversal() {
    return reversal;
  }
}
