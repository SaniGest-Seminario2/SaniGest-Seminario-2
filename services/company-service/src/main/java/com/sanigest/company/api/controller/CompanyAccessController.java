package com.sanigest.company.api.controller;

import com.sanigest.company.application.exception.CompanyAccessDeniedException;
import com.sanigest.company.application.usecase.access.RequireRegularAccount;
import com.sanigest.company.application.usecase.access.ResolveCompanyAccess;
import com.sanigest.company.domain.model.AdvisorPermission;
import com.sanigest.companyaccess.CompanyAccess.MembershipRole;
import com.sanigest.companyaccess.CompanyAccessProtocol;
import com.sanigest.companyaccess.CompanyAccessProtocol.Query;
import com.sanigest.companyaccess.CompanyAccessProtocol.Response;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CompanyAccessController {
  private final ResolveCompanyAccess access;
  private final JwtDecoder userDecoder;
  private final RequireRegularAccount regularAccount;

  CompanyAccessController(
      ResolveCompanyAccess access, JwtDecoder userDecoder, RequireRegularAccount regularAccount) {
    this.access = access;
    this.userDecoder = userDecoder;
    this.regularAccount = regularAccount;
  }

  @GetMapping(CompanyAccessProtocol.PATH)
  ResponseEntity<Response> resolve(
      @AuthenticationPrincipal Jwt assertion,
      @RequestHeader(name = CompanyAccessProtocol.USER_ASSERTION, required = false)
          String session) {
    var query = Query.from(assertion);
    if (query.target() == null) {
      UUID userId = regularUser(session, query.userId());
      var membership =
          access.membership(userId, query.module().name() + "_" + query.scope().name());
      return response(
          new Response(
              userId,
              membership.companyId(),
              query.module(),
              query.scope(),
              "MEMBERSHIP",
              MembershipRole.valueOf(membership.role().name()),
              membership.explicitGrant()));
    }
    access.requireDelegation(
        query.userId(),
        query.target(),
        new AdvisorPermission(
            AdvisorPermission.Module.valueOf(query.module().name()),
            AdvisorPermission.Scope.valueOf(query.scope().name())));
    return response(
        new Response(
            query.userId(),
            query.target(),
            query.module(),
            query.scope(),
            "DELEGATED",
            null,
            null));
  }

  private UUID regularUser(String session, UUID assertedUser) {
    if (session == null || session.isBlank()) {
      throw new CompanyAccessDeniedException();
    }
    Jwt verified;
    try {
      verified = userDecoder.decode(session);
    } catch (JwtException invalid) {
      throw new CompanyAccessDeniedException();
    }
    if (!assertedUser.equals(UUID.fromString(verified.getSubject()))) {
      throw new CompanyAccessDeniedException();
    }
    return CompanyActor.authenticatedUserId(verified, regularAccount);
  }

  private static ResponseEntity<Response> response(Response response) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
  }
}
