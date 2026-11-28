package com.sanigest.finance.api.controller.cash;

import com.sanigest.finance.application.port.in.cash.PaymentAdmission;
import com.sanigest.finance.application.port.in.cash.PaymentAdmission.ActiveShift;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class PaymentAdmissionController {
  private final PaymentAdmission admission;
  private final byte[] expectedAuthorization;

  PaymentAdmissionController(
      PaymentAdmission admission, @Value("${sanigest.finance.admission-token}") String token) {
    this.admission = admission;
    expectedAuthorization =
        token.isBlank() ? null : ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
  }

  @GetMapping("/internal/companies/{companyId}/operators/{operatorId}/active-shift")
  ResponseEntity<ActiveShift> activeShift(
      @PathVariable UUID companyId,
      @PathVariable UUID operatorId,
      @RequestHeader(name = HttpHeaders.AUTHORIZATION, defaultValue = "") String authorization) {
    authorize(authorization);
    return ResponseEntity.of(admission.find(companyId, operatorId));
  }

  @GetMapping("/internal/companies/{companyId}/payment-accounts/{accountId}")
  ResponseEntity<Void> paymentAccount(
      @PathVariable UUID companyId,
      @PathVariable UUID accountId,
      @RequestParam String method,
      @RequestHeader(name = HttpHeaders.AUTHORIZATION, defaultValue = "") String authorization) {
    authorize(authorization);
    return admission.isEligibleAccount(companyId, accountId, method)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }

  private void authorize(String authorization) {
    if (expectedAuthorization == null
        || !MessageDigest.isEqual(
            expectedAuthorization, authorization.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
  }
}
