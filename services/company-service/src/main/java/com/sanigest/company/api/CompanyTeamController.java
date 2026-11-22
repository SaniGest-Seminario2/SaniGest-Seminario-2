package com.sanigest.company.api;

import static com.sanigest.company.api.CompanyController.authenticatedUserId;

import com.sanigest.company.application.AcceptCompanyInvitationUseCase;
import com.sanigest.company.application.CurrentAccount;
import com.sanigest.company.application.InviteCompanyUserUseCase;
import com.sanigest.company.application.InviteCompanyUserUseCase.CreatedInvitation;
import com.sanigest.company.application.ListMyCompanyUsersUseCase;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
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
  static final String ACCEPT_PATH = "/api/v1/invitations/{token}/accept";

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
  ResponseEntity<CreatedInvitation> invite(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InvitationRequest request) {
    var created =
        inviteUser.invite(
            authenticatedUserId(jwt, accounts),
            request.email(),
            CompanyRole.valueOf(request.role()),
            request.replace());
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(created);
  }

  @PostMapping(ACCEPT_PATH)
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

  record InvitationRequest(
      @NotBlank @Email @Size(max = 320) String email,
      @NotBlank @Pattern(regexp = "OPERATOR|VIEWER") String role,
      boolean replace) {}

  record MemberResponse(UUID membershipId, UUID userId, CompanyRole role, MembershipStatus status) {
    static MemberResponse from(Membership membership) {
      return new MemberResponse(
          membership.id(), membership.userId(), membership.role(), membership.status());
    }
  }
}
