package com.sanigest.finance.api.controller.movement;

import com.sanigest.companyaccess.application.exception.CompanyAccessUnavailable;
import com.sanigest.companyaccess.application.port.out.CompanyAccess;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.application.port.out.CompanyAccess.Scope;
import com.sanigest.companyaccess.security.authentication.AuthenticatedActor;
import com.sanigest.finance.application.dto.movement.ManualMovement.Document;
import com.sanigest.finance.application.dto.movement.ManualMovement.Options;
import com.sanigest.finance.application.dto.movement.ManualMovement.Request;
import com.sanigest.finance.application.dto.movement.ManualMovement.Result;
import com.sanigest.finance.application.exception.movement.InvalidManualMovement;
import com.sanigest.finance.application.exception.movement.ManualMovementConflict;
import com.sanigest.finance.application.usecase.movement.ManualCashMovements;
import com.sanigest.security.api.request.company.CompanySelector;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ManualCashMovementController {
  private static final String MOVEMENTS = "/api/v1/cash-movements";
  private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
  private final ManualCashMovements movements;
  private final ObjectProvider<CompanyAccess> companies;

  ManualCashMovementController(
      ManualCashMovements movements, ObjectProvider<CompanyAccess> companies) {
    this.movements = movements;
    this.companies = companies;
  }

  @GetMapping("/api/v1/finance/options")
  Options options(HttpServletRequest http) {
    return movements.options(company(http, Scope.READ));
  }

  @PostMapping(path = MOVEMENTS, consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  Response create(
      HttpServletRequest http,
      @RequestHeader(IDEMPOTENCY_KEY) String key,
      @RequestBody Request request) {
    return register(http, key, request, null);
  }

  @PostMapping(path = MOVEMENTS, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  Response createWithDocument(
      HttpServletRequest http,
      @RequestHeader(IDEMPOTENCY_KEY) String key,
      @RequestPart("movement") Request request,
      @RequestPart(value = "document", required = false) MultipartFile file) {
    return register(http, key, request, file);
  }

  @GetMapping(MOVEMENTS + "/{id}/attachment")
  ResponseEntity<byte[]> document(HttpServletRequest http, @PathVariable UUID id) {
    var document =
        movements
            .document(company(http, Scope.READ), id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(document.contentType()))
        .cacheControl(CacheControl.noStore())
        .header("Content-Disposition", "attachment")
        .header("X-Content-Type-Options", "nosniff")
        .body(document.content());
  }

  private Response register(
      HttpServletRequest http, String key, Request request, MultipartFile file) {
    UUID company = company(http, Scope.WRITE);
    UUID actor = AuthenticatedActor.userId().orElseThrow(ManualCashMovementController::denied);
    if (Collections.list(http.getHeaders(IDEMPOTENCY_KEY)).size() != 1
        || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
      throw new InvalidManualMovement("InvalidManualMovement Idempotency-Key");
    }
    Result result =
        movements.register(company, actor, key, request, file == null ? null : read(file));
    return new Response(
        result.id(),
        result.origin(),
        result.actorId(),
        result.movement(),
        result.hasDocument() ? MOVEMENTS + "/" + result.id() + "/attachment" : null);
  }

  private UUID company(HttpServletRequest http, Scope scope) {
    try {
      UUID id =
          CompanySelector.resolve(
              Collections.list(http.getHeaders(CompanySelector.HEADER)), null, true);
      companies
          .getIfAvailable(() -> {
            throw new CompanyAccessUnavailable();
          })
          .membership(id, scope)
          .filter(member -> id.equals(member.companyId()))
          .filter(member ->
              member.role() == MembershipRole.OWNER
                  || member.role() == MembershipRole.ADMIN
                  || (scope == Scope.READ && member.explicitGrant()))
          .orElseThrow(ManualCashMovementController::denied);
      return id;
    } catch (CompanySelector.InvalidContext invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
    } catch (CompanyAccessUnavailable unavailable) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Company authorizer unavailable");
    }
  }

  private static ResponseStatusException denied() {
    return new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  private static Document read(MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 1_048_576) {
      throw new InvalidManualMovement("document must contain at most 1 MiB");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException failure) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read document", failure);
    }
    String signature = new String(bytes, 0, Math.min(8, bytes.length), StandardCharsets.ISO_8859_1);
    String contentType = file.getContentType();
    if ((MediaType.APPLICATION_PDF_VALUE.equals(contentType) && signature.startsWith("%PDF-"))
        || (MediaType.IMAGE_PNG_VALUE.equals(contentType)
            && signature.equals("\u0089PNG\r\n\u001a\n"))
        || (MediaType.IMAGE_JPEG_VALUE.equals(contentType)
            && signature.startsWith("\u00ff\u00d8\u00ff"))) {
      return new Document(contentType, bytes);
    }
    throw new InvalidManualMovement("document must be a PDF, PNG or JPEG");
  }

  record Response(UUID id, String origin, UUID actorId, Request movement, String documentUrl) {}

  @ExceptionHandler(InvalidManualMovement.class)
  ProblemDetail invalid(InvalidManualMovement failure) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
  }

  @ExceptionHandler(ManualMovementConflict.class)
  ProblemDetail conflict() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "This Idempotency-Key was used with a different movement");
  }
}
