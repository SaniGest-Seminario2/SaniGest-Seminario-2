package com.sanigest.company.api.controller;

import static com.sanigest.company.api.controller.CompanyActor.authenticatedUserId;

import com.sanigest.company.api.dto.AdvisorAccessRequest;
import com.sanigest.company.api.dto.AdvisorAccessResponse;
import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import com.sanigest.company.application.usecase.advisor.GetAdvisorAccessUseCase;
import com.sanigest.company.application.usecase.advisor.PutAdvisorAccessUseCase;
import com.sanigest.company.application.usecase.advisor.RevokeAdvisorAccessUseCase;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AdvisorAccessController.PATH)
class AdvisorAccessController {
  static final String PATH = "/api/v1/companies/me/advisor-access";
  private final RequireRegularAccount regularAccount;
  private final GetAdvisorAccessUseCase getAccess;
  private final PutAdvisorAccessUseCase putAccess;
  private final RevokeAdvisorAccessUseCase revokeAccess;

  AdvisorAccessController(
      RequireRegularAccount regularAccount,
      GetAdvisorAccessUseCase getAccess,
      PutAdvisorAccessUseCase putAccess,
      RevokeAdvisorAccessUseCase revokeAccess) {
    this.regularAccount = regularAccount;
    this.getAccess = getAccess;
    this.putAccess = putAccess;
    this.revokeAccess = revokeAccess;
  }

  @GetMapping
  ResponseEntity<List<AdvisorAccessResponse>> list(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            getAccess.get(authenticatedUserId(jwt, regularAccount)).stream()
                .map(AdvisorAccessResponse::from)
                .toList());
  }

  @PutMapping
  ResponseEntity<AdvisorAccessResponse> put(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AdvisorAccessRequest request) {
    UUID actor = authenticatedUserId(jwt, regularAccount);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            AdvisorAccessResponse.from(
                putAccess.put(
                    actor,
                    request.advisorId(),
                    request.revision(),
                    request.permissions().stream()
                        .map(AdvisorAccessRequest.Permission::toDomain)
                        .toList())));
  }

  @DeleteMapping
  ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID advisorId) {
    revokeAccess.revoke(authenticatedUserId(jwt, regularAccount), advisorId);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
