package com.sanigest.company.api.dto;

import com.sanigest.company.application.dto.MemberPage;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import com.sanigest.web.PageResponse;
import java.util.UUID;

public final class MemberPageResponse {
  private MemberPageResponse() {}

  public record Item(
      UUID membershipId,
      UUID userId,
      String name,
      String email,
      CompanyRole role,
      MembershipStatus status) {}

  public static PageResponse<MemberPageResponse.Item> from(MemberPage page) {
    return new PageResponse<>(
        page.items().stream()
            .map(item ->
                new Item(
                    item.membership().id(),
                    item.membership().userId(),
                    item.name(),
                    item.email(),
                    item.membership().role(),
                    item.membership().status()))
            .toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }
}
