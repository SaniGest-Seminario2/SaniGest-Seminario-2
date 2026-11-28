package com.sanigest.company.application.dto.team;

import com.sanigest.company.domain.model.team.Membership;
import java.util.List;

public record MemberPage(
    List<Item> items, int page, int size, long totalElements, long totalPages) {
  public record Item(Membership membership, String name, String email) {}
}
