package com.sanigest.company.api.controller;

import static com.sanigest.company.api.controller.CompanyActor.authenticatedUserId;

import com.sanigest.company.api.dto.CompanyPatchRequest;
import com.sanigest.company.api.dto.CompanyProfileResponse;
import com.sanigest.company.api.dto.CompanyRequest;
import com.sanigest.company.api.dto.CompanyResponse;
import com.sanigest.company.api.dto.CompanySummaryResponse;
import com.sanigest.company.api.dto.UpdatedCompanyResponse;
import com.sanigest.company.application.dto.CreateCompanyCommand;
import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import com.sanigest.company.application.usecase.profile.CreateCompanyUseCase;
import com.sanigest.company.application.usecase.profile.GetMyCompanyUseCase;
import com.sanigest.company.application.usecase.profile.ListMyCompaniesUseCase;
import com.sanigest.company.application.usecase.profile.UpdateMyCompanyUseCase;
import jakarta.validation.Valid;
import java.util.List;
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

  private final ListMyCompaniesUseCase listMyCompanies;

  private final RequireRegularAccount regularAccount;

  public CompanyController(
      CreateCompanyUseCase useCase,
      GetMyCompanyUseCase getMyCompany,
      UpdateMyCompanyUseCase updateMyCompany,
      ListMyCompaniesUseCase listMyCompanies,
      RequireRegularAccount regularAccount) {
    this.useCase = useCase;
    this.getMyCompany = getMyCompany;
    this.updateMyCompany = updateMyCompany;
    this.listMyCompanies = listMyCompanies;
    this.regularAccount = regularAccount;
  }

  @GetMapping
  List<CompanySummaryResponse> list(@AuthenticationPrincipal Jwt jwt) {
    return listMyCompanies.list(authenticatedUserId(jwt, regularAccount)).stream()
        .map(CompanySummaryResponse::from)
        .toList();
  }

  @GetMapping("/me")
  CompanyProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
    var profile = getMyCompany.get(authenticatedUserId(jwt, regularAccount));
    return new CompanyProfileResponse(
        profile.companyId(),
        profile.companyName(),
        profile.businessName(),
        profile.rnc(),
        profile.location(),
        profile.currency(),
        profile.status());
  }

  @PatchMapping("/me")
  UpdatedCompanyResponse update(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompanyPatchRequest request) {
    var updated =
        updateMyCompany.update(authenticatedUserId(jwt, regularAccount), request.toPatch());
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
    var userId = authenticatedUserId(jwt, regularAccount);
    var command =
        new CreateCompanyCommand(
            request.companyName(),
            request.businessName(),
            request.rnc(),
            request.email(),
            request.phone(),
            request.location(),
            request.ownerId(),
            request.userId());
    var company = useCase.create(userId, key, command);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            new CompanyResponse(
                company.id(),
                company.name(),
                company.businessName(),
                company.rnc(),
                company.email(),
                company.phone(),
                company.location(),
                company.currency(),
                company.status()));
  }
}
