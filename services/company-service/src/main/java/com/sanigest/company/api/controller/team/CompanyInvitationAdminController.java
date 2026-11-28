package com.sanigest.company.api.controller.team;

import static com.sanigest.company.api.routing.CompanyRequestSelection.resolve;
import static com.sanigest.company.api.security.CompanyActor.authenticatedUserId;

import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import com.sanigest.company.application.usecase.team.ManageCompanyInvitationsUseCase;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.InvitationStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/companies/me/invitations", "/api/v1/companies/{companyId}/invitations"})
class CompanyInvitationAdminController {
  private final RequireRegularAccount regularAccount;
  private final ManageCompanyInvitationsUseCase invitations;

  CompanyInvitationAdminController(
      RequireRegularAccount regularAccount, ManageCompanyInvitationsUseCase invitations) {
    this.regularAccount = regularAccount;
    this.invitations = invitations;
  }

  @GetMapping
  ResponseEntity<List<PendingInvitationResponse>> pending(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable(required = false) String companyId,
      HttpServletRequest http,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result =
        invitations
            .pending(authenticatedUserId(jwt, regularAccount), resolve(http, companyId), page, size)
            .stream()
            .map(invitation ->
                new PendingInvitationResponse(
                    invitation.id(),
                    invitation.name(),
                    invitation.email(),
                    invitation.role(),
                    invitation.status(),
                    invitation.createdAt(),
                    invitation.expiresAt()))
            .toList();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
  }

  @DeleteMapping("/{invitationId}")
  ResponseEntity<Void> revoke(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable(required = false) String companyId,
      HttpServletRequest http,
      @PathVariable UUID invitationId) {
    invitations.revoke(
        authenticatedUserId(jwt, regularAccount), resolve(http, companyId), invitationId);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  record PendingInvitationResponse(
      UUID invitationId,
      String name,
      String email,
      CompanyRole role,
      InvitationStatus status,
      Instant createdAt,
      Instant expiresAt) {}
}
