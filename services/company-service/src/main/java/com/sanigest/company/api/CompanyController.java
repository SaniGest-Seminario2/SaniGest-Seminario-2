package com.sanigest.company.api;

import com.sanigest.company.application.CreateCompanyUseCase;
import com.sanigest.company.application.GetMyCompanyUseCase;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyProfile;
import com.sanigest.company.application.UpdateMyCompanyUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {
  private final CreateCompanyUseCase useCase;

  private final GetMyCompanyUseCase getMyCompany;

  private final UpdateMyCompanyUseCase updateMyCompany;

  public CompanyController(
      CreateCompanyUseCase useCase,
      GetMyCompanyUseCase getMyCompany,
      UpdateMyCompanyUseCase updateMyCompany) {
    this.useCase = useCase;
    this.getMyCompany = getMyCompany;
    this.updateMyCompany = updateMyCompany;
  }

  @GetMapping("/me")
  CompanyProfile me(@AuthenticationPrincipal Jwt jwt) {
    return getMyCompany.get(authenticatedUserId(jwt));
  }

  @PatchMapping("/me")
  UpdatedCompanyResponse update(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompanyPatchRequest request) {
    var updated = updateMyCompany.update(authenticatedUserId(jwt), request.toPatch());
    var company = updated.company();
    return new UpdatedCompanyResponse(
        company.id(),
        company.name(),
        company.businessName(),
        company.rnc(),
        company.email(),
        company.phone(),
        company.location(),
        company.currency(),
        company.status(),
        updated.updatedAt());
  }

  @PostMapping
  ResponseEntity<CompanyResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CompanyRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(useCase.create(authenticatedUserId(jwt), key, request));
  }

  private UUID authenticatedUserId(Jwt jwt) {
    try {
      return UUID.fromString(jwt.getSubject());
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid authenticated identity");
    }
  }

  public record CompanyRequest(
      @NotBlank @Size(max = 160) String companyName,
      @NotBlank @Size(max = 160) String businessName,
      @Pattern(regexp = "[0-9]{9}|[0-9]{11}", message = "RNC must contain 9 or 11 digits") String rnc,
      @NotBlank @Email @Size(max = 320) String email,
      @NotBlank @Pattern(regexp = "[0-9+() .-]{7,32}") String phone,
      @NotBlank @Size(max = 255) String location,
      String ownerId,
      String userId) {}

  public record UpdatedCompanyResponse(
      UUID companyId,
      String companyName,
      String businessName,
      String rnc,
      String email,
      String phone,
      String location,
      String currency,
      String status,
      Instant updatedAt) {}

  public record CompanyResponse(
      UUID companyId,
      String companyName,
      String businessName,
      String rnc,
      String email,
      String phone,
      String location,
      String currency,
      String status) {}
}
