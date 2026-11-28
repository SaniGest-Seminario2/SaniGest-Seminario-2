package com.sanigest.company.api.dto.team;

import com.sanigest.company.application.dto.team.MemberPage;
import com.sanigest.company.domain.enums.team.CompanyRole;
import com.sanigest.company.domain.enums.team.MembershipStatus;
import com.sanigest.web.api.dto.PageResponse;
import java.util.UUID;

public final class MemberPageResponse {
  private MemberPageResponse() {}

  public record Item(
      UUID membershipId,
      UUID userId,
      String name,
      String email,
      CompanyRole role,
      MembershipStatus status,
      DisplayStatus displayStatus) {
    public static Item from(MemberPage.Item item) {
      var member = item.membership();
      return new Item(
          member.id(),
          member.userId(),
          item.name(),
          item.email(),
          member.role(),
          member.status(),
          member.status() == MembershipStatus.ACTIVE
              ? DisplayStatus.ACTIVE
              : DisplayStatus.INACTIVE);
    }
  }

  public enum DisplayStatus {
    ACTIVE,
    INACTIVE
  }

  public static PageResponse<MemberPageResponse.Item> from(MemberPage page) {
    return new PageResponse<>(
        page.items().stream().map(Item::from).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }
}
