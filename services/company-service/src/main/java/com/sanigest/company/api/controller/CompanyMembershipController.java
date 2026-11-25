package com.sanigest.company.api.controller;

import static com.sanigest.company.api.controller.CompanyActor.authenticatedUserId;
import static com.sanigest.company.api.routing.CompanyRequestSelection.resolve;

import com.sanigest.company.api.dto.ChangeMemberRoleRequest;
import com.sanigest.company.api.dto.MemberResponse;
import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import com.sanigest.company.application.usecase.team.ChangeCompanyMemberRoleUseCase;
import com.sanigest.company.application.usecase.team.ChangeCompanyMemberStatusUseCase;
import com.sanigest.company.application.usecase.team.RevokeCompanyMembershipUseCase;
import com.sanigest.company.domain.enums.MembershipStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({
  "/api/v1/companies/me/members/{userId}",
  "/api/v1/companies/{companyId}/members/{userId}"
})
class CompanyMembershipController {
  private final RequireRegularAccount regularAccount;
  private final ChangeCompanyMemberRoleUseCase changeRole;
  private final RevokeCompanyMembershipUseCase revokeMembership;
  private final ChangeCompanyMemberStatusUseCase changeStatus;

  CompanyMembershipController(
      RequireRegularAccount regularAccount,
      ChangeCompanyMemberRoleUseCase changeRole,
      RevokeCompanyMembershipUseCase revokeMembership,
      ChangeCompanyMemberStatusUseCase changeStatus) {
    this.regularAccount = regularAccount;
    this.changeRole = changeRole;
    this.revokeMembership = revokeMembership;
    this.changeStatus = changeStatus;
  }

  @PatchMapping("/role")
  ResponseEntity<MemberResponse> changeRole(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable(required = false) String companyId,
      HttpServletRequest http,
      @PathVariable UUID userId,
      @Valid @RequestBody ChangeMemberRoleRequest request) {
    var membership =
        changeRole.change(
            authenticatedUserId(jwt, regularAccount),
            resolve(http, companyId),
            userId,
            request.role());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(MemberResponse.from(membership));
  }

  @PatchMapping("/status")
  ResponseEntity<MemberResponse> changeStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable(required = false) String companyId,
      HttpServletRequest http,
      @PathVariable UUID userId,
      @Valid @RequestBody ChangeMemberStatusRequest request) {
    var member =
        changeStatus.change(
            authenticatedUserId(jwt, regularAccount),
            resolve(http, companyId),
            userId,
            request.status());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(MemberResponse.from(member));
  }

  record ChangeMemberStatusRequest(@NotNull MembershipStatus status) {}

  @DeleteMapping
  ResponseEntity<Void> revoke(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable(required = false) String companyId,
      HttpServletRequest http,
      @PathVariable UUID userId) {
    revokeMembership.revoke(
        authenticatedUserId(jwt, regularAccount), resolve(http, companyId), userId);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
