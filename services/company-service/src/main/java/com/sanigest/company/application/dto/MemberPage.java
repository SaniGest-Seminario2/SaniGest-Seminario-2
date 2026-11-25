package com.sanigest.company.application.dto;

import com.sanigest.company.domain.model.Membership;
import java.util.List;

public record MemberPage(
    List<Item> items, int page, int size, long totalElements, long totalPages) {
  public record Item(Membership membership, String name, String email) {}
}
