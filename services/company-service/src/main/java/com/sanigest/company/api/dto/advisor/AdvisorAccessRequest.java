package com.sanigest.company.api.dto.advisor;

import com.sanigest.company.domain.model.advisor.AdvisorPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AdvisorAccessRequest(
    @NotNull UUID advisorId,
    @PositiveOrZero long revision,
    @NotNull @Size(min = 1, max = 8) List<@NotNull @Valid Permission> permissions) {
  public record Permission(@NotNull String module, String scope) {
    public AdvisorPermission toDomain() {
      return new AdvisorPermission(
          AdvisorPermission.Module.valueOf(module),
          scope == null ? AdvisorPermission.Scope.READ : AdvisorPermission.Scope.valueOf(scope));
    }
  }
}
