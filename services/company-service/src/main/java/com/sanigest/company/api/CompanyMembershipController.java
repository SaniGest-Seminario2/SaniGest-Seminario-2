package com.sanigest.company.api;

import static com.sanigest.company.api.CompanyController.authenticatedUserId;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.sanigest.company.api.CompanyTeamController.MemberResponse;
import com.sanigest.company.application.ChangeCompanyMemberRoleUseCase;
import com.sanigest.company.application.CurrentAccount;
import com.sanigest.company.application.RevokeCompanyMembershipUseCase;
import com.sanigest.company.domain.model.CompanyRole;
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
import tools.jackson.databind.JsonNode;

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
      @Valid @RequestBody RoleRequest request) {
    var membership = changeRole.change(authenticatedUserId(jwt, accounts), userId, request.role);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(MemberResponse.from(membership));
  }

  @DeleteMapping
  ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
    revokeMembership.revoke(authenticatedUserId(jwt, accounts), userId);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  static final class RoleRequest {
    @NotNull private CompanyRole role;

    @JsonSetter("role")
    void setRole(JsonNode value) {
      if (value == null || !value.isString()) {
        throw new IllegalArgumentException("A role name is required");
      }
      role = CompanyRole.valueOf(value.stringValue());
    }

    @JsonAnySetter
    void rejectUnknownField(String name, JsonNode value) {
      throw new IllegalArgumentException("Unsupported membership field");
    }
  }
}
