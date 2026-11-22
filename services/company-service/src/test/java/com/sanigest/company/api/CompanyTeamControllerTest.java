package com.sanigest.company.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.company.api.CompanyTeamController.InvitationRequest;
import com.sanigest.company.api.CompanyTeamController.MemberResponse;
import com.sanigest.company.application.AcceptCompanyInvitationUseCase;
import com.sanigest.company.application.CurrentAccount;
import com.sanigest.company.application.CurrentAccount.AccountType;
import com.sanigest.company.application.GetMyCompanyUseCase;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyAccessDeniedException;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyMembership;
import com.sanigest.company.application.GetMyCompanyUseCase.CompanyProfile;
import com.sanigest.company.application.GetMyCompanyUseCase.MembershipRepository;
import com.sanigest.company.application.InviteCompanyUserUseCase;
import com.sanigest.company.application.InviteCompanyUserUseCase.CreatedInvitation;
import com.sanigest.company.application.ListMyCompanyUsersUseCase;
import com.sanigest.company.domain.model.CompanyRole;
import com.sanigest.company.domain.model.Invitation.Status;
import com.sanigest.company.domain.model.Membership;
import com.sanigest.company.domain.model.MembershipStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerMapping;

class CompanyTeamControllerTest {
  private static final String EMAIL = "guest@example.com";
  private final UUID user = UUID.randomUUID();
  private final UUID company = UUID.randomUUID();
  private final CurrentAccount accounts = mock(CurrentAccount.class);
  private final InviteCompanyUserUseCase invite = mock(InviteCompanyUserUseCase.class);
  private final AcceptCompanyInvitationUseCase accept = mock(AcceptCompanyInvitationUseCase.class);
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final CompanyTeamController controller =
      new CompanyTeamController(
          accounts,
          invite,
          accept,
          new ListMyCompanyUsersUseCase(new GetMyCompanyUseCase(memberships), memberships));

  @Test
  void createsInvitationForAuthenticatedUserAndReturnsUncacheableMetadata() {
    when(accounts.resolve("access-token")).thenReturn(AccountType.REGULAR);
    var created =
        new CreatedInvitation(
            UUID.randomUUID(),
            CompanyRole.OPERATOR,
            Status.PENDING,
            Instant.parse("2026-11-22T00:00:00Z"));
    when(invite.invite(user, EMAIL, CompanyRole.OPERATOR, true)).thenReturn(created);

    var response = controller.invite(jwt(true), new InvitationRequest(EMAIL, "OPERATOR", true));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isEqualTo(created);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
  }

  @Test
  void acceptsOnlyTheEmailAssertedVerifiedByTheJwtIssuer() {
    when(accounts.resolve("access-token")).thenReturn(AccountType.REGULAR);
    when(accept.accept(user, null, "invite-token")).thenThrow(new CompanyAccessDeniedException());
    var member = member();
    when(accept.accept(user, EMAIL, "invite-token")).thenReturn(member);

    assertThatThrownBy(() -> controller.accept(jwt("true"), "invite-token"))
        .isInstanceOf(CompanyAccessDeniedException.class);
    var response = controller.accept(jwt(true), "invite-token");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .isEqualTo(
            new MemberResponse(member.id(), user, CompanyRole.VIEWER, MembershipStatus.ACTIVE));
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
  }

  @Test
  void listsMembersOnlyAfterAuthorizingTheCallersCompany() {
    when(accounts.resolve("access-token")).thenReturn(AccountType.REGULAR);
    var profile = new CompanyProfile(company, "Clinic", "Clinic", null, null, "DOP", "ACTIVE");
    when(memberships.findCompanyByUserId(user))
        .thenReturn(Optional.of(new CompanyMembership(MembershipStatus.SUSPENDED, profile)))
        .thenReturn(Optional.of(new CompanyMembership(MembershipStatus.ACTIVE, profile)));
    var member = member();
    when(memberships.findAllByCompanyId(company)).thenReturn(List.of(member));

    assertThatThrownBy(() -> controller.users(jwt(true)))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verify(memberships, never()).findAllByCompanyId(any());
    var response = controller.users(jwt(true));
    assertThat(response.getBody())
        .containsExactly(
            new MemberResponse(member.id(), user, CompanyRole.VIEWER, MembershipStatus.ACTIVE));
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
  }

  @Test
  void deniesAdvisorBeforeUsingAnInvitationOrReadingMemberships() {
    when(accounts.resolve("access-token")).thenReturn(AccountType.ADVISOR);
    assertThatThrownBy(() -> controller.accept(jwt(true), "invite-token"))
        .isInstanceOf(CompanyAccessDeniedException.class);
    assertThatThrownBy(() -> controller.users(jwt(true)))
        .isInstanceOf(CompanyAccessDeniedException.class);
    verifyNoInteractions(accept, memberships);
  }

  @Test
  void redactsTheTokenFromInvitationProblemDetails() {
    var request = new MockHttpServletRequest("POST", "/api/v1/invitations/secret/accept");
    request.setAttribute(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, CompanyTeamController.ACCEPT_PATH);

    var problem = new ApiExceptionHandler().invitationNotFound(request);

    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getInstance()).hasToString("/api/v1/invitations");
  }

  private Jwt jwt(Object verified) {
    return Jwt.withTokenValue("access-token")
        .header("alg", "RS256")
        .subject(user.toString())
        .claim("email", EMAIL)
        .claim("email_verified", verified)
        .build();
  }

  private Membership member() {
    return new Membership(
        UUID.randomUUID(), company, user, CompanyRole.VIEWER, MembershipStatus.ACTIVE);
  }
}
