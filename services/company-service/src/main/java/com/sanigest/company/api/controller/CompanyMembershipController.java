package com.sanigest.company.api.controller;

import static com.sanigest.company.api.controller.CompanyController.authenticatedUserId;

import com.sanigest.company.api.dto.ChangeMemberRoleRequest;
import com.sanigest.company.api.dto.MemberResponse;
import com.sanigest.company.application.port.out.integration.CurrentAccount;
import com.sanigest.company.application.usecase.team.ChangeCompanyMemberRoleUseCase;
import com.sanigest.company.application.usecase.team.RevokeCompanyMembershipUseCase;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/companies/me/members/{userId}")
class CompanyMembershipController {
  private final CurrentAccount accounts;
  private final ChangeCompanyMemberRoleUseCase changeRole;
  private final RevokeCompanyMembershipUseCase revokeMembership;

  CompanyMembershipController(
      CurrentAccount accounts,
      ChangeCompanyMemberRoleUseCase changeRole,
      RevokeCompanyMembershipUseCase revokeMembership) {
    this.accounts = accounts;
    this.changeRole = changeRole;
    this.revokeMembership = revokeMembership;
  }

  @PatchMapping("/role")
  ResponseEntity<MemberResponse> changeRole(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID userId,
      @Valid @RequestBody ChangeMemberRoleRequest request) {
    var membership = changeRole.change(authenticatedUserId(jwt, accounts), userId, request.role());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(MemberResponse.from(membership));
  }

  @DeleteMapping
  ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
    revokeMembership.revoke(authenticatedUserId(jwt, accounts), userId);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
