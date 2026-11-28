package com.sanigest.events.application.idempotency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/** Stable fingerprint for a normalized financial write, including its operation and target. */
public final class FinancialWriteFingerprint {
  private static final JsonMapper CANONICAL_JSON =
      JsonMapper.builder()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
          .enable(
              JsonNodeFeature.WRITE_PROPERTIES_SORTED, JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .build();

  private FinancialWriteFingerprint() {}

  public static String v1(String operation, UUID target, Object normalizedRequest) {
    JsonNode canonical =
        CANONICAL_JSON.readTree(
            CANONICAL_JSON.writeValueAsBytes(List.of(operation, target, normalizedRequest)));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(CANONICAL_JSON.writeValueAsBytes(canonical));
      return "v1:" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
