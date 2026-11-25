package com.sanigest.identity.api.controller.account;

import com.sanigest.identity.application.usecase.account.GetMemberIdentitiesUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test"})
final class MemberDirectoryController {
  private final GetMemberIdentitiesUseCase identities;

  MemberDirectoryController(GetMemberIdentitiesUseCase identities) {
    this.identities = identities;
  }

  @PostMapping("/internal/v1/member-directory")
  List<MemberIdentityResponse> lookup(@Valid @RequestBody Lookup request) {
    return identities.get(request.userIds()).stream()
        .map(identity ->
            new MemberIdentityResponse(identity.userId(), identity.name(), identity.email(), false))
        .toList();
  }

  record Lookup(@NotNull @Size(min = 1, max = 100) List<@NotNull UUID> userIds) {}

  // Registration does not yet produce evidence of email verification.
  record MemberIdentityResponse(UUID userId, String name, String email, boolean emailVerified) {}
}
