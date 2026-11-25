package com.sanigest.company.api.dto;

import com.sanigest.company.application.dto.MemberPage;
import com.sanigest.company.domain.enums.CompanyRole;
import com.sanigest.company.domain.enums.MembershipStatus;
import java.util.List;
import java.util.UUID;

public record MemberPageResponse(
    List<Item> content, int page, int size, long totalElements, long totalPages) {
  public record Item(
      UUID membershipId,
      UUID userId,
      String name,
      String email,
      CompanyRole role,
      MembershipStatus status) {}

  public static MemberPageResponse from(MemberPage page) {
    return new MemberPageResponse(
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
