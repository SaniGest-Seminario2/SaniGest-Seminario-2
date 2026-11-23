package com.sanigest.sales.application.usecase.sale;

import com.sanigest.sales.application.dto.CreateSaleCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class SaleRequestFingerprint {
  static final int VERSION = 1;
  static final String OPERATION = "POST /api/v1/sales";

  private SaleRequestFingerprint() {}

  static String hash(CreateSaleCommand command) {
    var canonical =
        new StringBuilder(OPERATION)
            .append('\n')
            .append(VERSION)
            .append('\n')
            .append(command.clientId())
            .append('\n')
            .append(command.mode())
            .append('\n')
            .append(command.saleDate() == null ? "-" : command.saleDate());
    for (var line : command.lines()) {
      canonical
          .append('\n')
          .append(line.catalogItemId())
          .append('\n')
          .append(line.quantity().stripTrailingZeros().toPlainString());
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
}
