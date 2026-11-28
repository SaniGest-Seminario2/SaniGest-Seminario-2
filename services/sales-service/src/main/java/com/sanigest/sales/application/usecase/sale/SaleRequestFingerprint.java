package com.sanigest.sales.application.usecase.sale;

import com.sanigest.sales.application.dto.sale.CreateSaleCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class SaleRequestFingerprint {
  private static final int LEGACY_VERSION = 1;
  private static final int DUE_DATE_VERSION = 2;
  static final String OPERATION = "POST /api/v1/sales";

  private SaleRequestFingerprint() {}

  private static String decimal(java.math.BigDecimal value) {
    return value == null ? "-" : value.stripTrailingZeros().toString();
  }

  static int version(CreateSaleCommand command) {
    return command.dueDate() == null ? LEGACY_VERSION : DUE_DATE_VERSION;
  }

  static String hash(CreateSaleCommand command) {
    return hash(command, false);
  }

  static String hash(CreateSaleCommand command, boolean invoice) {
    var canonical =
        new StringBuilder(OPERATION)
            .append('\n')
            .append(version(command))
            .append('\n')
            .append(command.clientId())
            .append('\n')
            .append(command.mode())
            .append('\n')
            .append(command.saleDate() == null ? "-" : command.saleDate());
    if (command.dueDate() != null) {
      canonical.append('\n').append(command.dueDate());
    }
    for (var line : command.lines()) {
      canonical
          .append('\n')
          .append(line.catalogItemId())
          .append('\n')
          .append(line.quantity().stripTrailingZeros().toPlainString());
      if (invoice) {
        canonical
            .append('\n')
            .append(
                line.discount() == null ? "0" : line.discount().stripTrailingZeros().toString());
      }
    }
    if (invoice && command.hasInvoiceFields()) {
      appendInvoiceForm(canonical, command);
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is required by Java", failure);
    }
  }

  private static void appendInvoiceForm(StringBuilder canonical, CreateSaleCommand command) {
    // Append only for extended requests to preserve replay of existing keys.
    canonical
        .append('\n')
        .append("invoice-form-v1")
        .append('\n')
        .append(command.requestedReceiptType())
        .append('\n')
        .append(command.notes() == null ? -1 : command.notes().length())
        .append(':')
        .append(command.notes());
    for (var line : command.lines()) {
      canonical
          .append('\n')
          .append(decimal(line.unitPrice()))
          .append('\n')
          .append(decimal(line.taxRate()));
    }
  }
}
