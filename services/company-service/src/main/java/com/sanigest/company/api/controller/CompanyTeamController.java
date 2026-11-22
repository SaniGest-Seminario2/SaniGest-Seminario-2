package com.sanigest.company.api.controller;

import static com.sanigest.company.api.controller.CompanyController.authenticatedUserId;

import com.sanigest.company.api.dto.InvitationRequest;
import com.sanigest.company.api.dto.InvitationResponse;
import com.sanigest.company.api.dto.MemberResponse;
import com.sanigest.company.api.routing.CompanyRoutes;
import com.sanigest.company.application.port.out.integration.CurrentAccount;
import com.sanigest.company.application.usecase.team.AcceptCompanyInvitationUseCase;
import com.sanigest.company.application.usecase.team.InviteCompanyUserUseCase;
import com.sanigest.company.application.usecase.team.ListMyCompanyUsersUseCase;
import com.sanigest.company.domain.enums.CompanyRole;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CompanyTeamController {

  private final CurrentAccount accounts;
  private final InviteCompanyUserUseCase inviteUser;
  private final AcceptCompanyInvitationUseCase acceptInvitation;
  private final ListMyCompanyUsersUseCase listUsers;

  CompanyTeamController(
      CurrentAccount accounts,
      InviteCompanyUserUseCase inviteUser,
      AcceptCompanyInvitationUseCase acceptInvitation,
      ListMyCompanyUsersUseCase listUsers) {
    this.accounts = accounts;
    this.inviteUser = inviteUser;
    this.acceptInvitation = acceptInvitation;
    this.listUsers = listUsers;
  }

  @PostMapping("/api/v1/companies/me/invitations")
  ResponseEntity<InvitationResponse> invite(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InvitationRequest request) {
    var created =
        inviteUser.invite(
            authenticatedUserId(jwt, accounts),
            request.email(),
            CompanyRole.valueOf(request.role()),
            request.replace());
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(
            new InvitationResponse(
                created.invitationId(),
                created.role().name(),
                created.status().name(),
                created.expiresAt()));
  }

  @PostMapping(CompanyRoutes.ACCEPT_INVITATION)
  ResponseEntity<MemberResponse> accept(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
    var member =
        acceptInvitation.accept(authenticatedUserId(jwt, accounts), verifiedEmail(jwt), token);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(MemberResponse.from(member));
  }

  @GetMapping("/api/v1/companies/me/users")
  ResponseEntity<List<MemberResponse>> users(@AuthenticationPrincipal Jwt jwt) {
    var members = listUsers.list(authenticatedUserId(jwt, accounts));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(members.stream().map(MemberResponse::from).toList());
  }

  private static String verifiedEmail(Jwt jwt) {
    return Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))
            && jwt.getClaims().get("email") instanceof String email
        ? email
        : null;
  }
}
