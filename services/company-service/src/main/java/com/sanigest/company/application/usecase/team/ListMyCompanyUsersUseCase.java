package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.dto.team.MemberPage;
import com.sanigest.company.application.dto.team.MemberSearchCriteria;
import com.sanigest.company.application.exception.access.AuthorizationUnavailableException;
import com.sanigest.company.application.exception.team.MemberListLimitException;
import com.sanigest.company.application.port.out.integration.team.MemberIdentityDirectory;
import com.sanigest.company.application.port.out.persistence.team.MembershipRepository;
import com.sanigest.company.application.usecase.profile.GetMyCompanyUseCase;
import com.sanigest.company.domain.model.team.Membership;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListMyCompanyUsersUseCase {
  public static final int ALL_LIMIT = 500;

  private final GetMyCompanyUseCase getMyCompany;
  private final MembershipRepository memberships;
  private final CompanyMembershipPolicy policy;
  private final MemberIdentityDirectory directory;

  public ListMyCompanyUsersUseCase(
      GetMyCompanyUseCase getMyCompany,
      MembershipRepository memberships,
      CompanyMembershipPolicy policy,
      MemberIdentityDirectory directory) {
    this.getMyCompany = getMyCompany;
    this.memberships = memberships;
    this.policy = policy;
    this.directory = directory;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<Membership> list(UUID userId) {
    return list(userId, null);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<Membership> list(UUID userId, UUID requestedCompany) {
    return memberships.findAllByCompanyId(
        (requestedCompany == null
                ? getMyCompany.get(userId)
                : getMyCompany.get(userId, requestedCompany))
            .companyId());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public MemberPage page(UUID userId, UUID requestedCompany, MemberSearchCriteria criteria) {
    UUID company = policy.requireAdministrator(userId, requestedCompany, false);
    var statuses = criteria.statuses();
    if (criteria.search().isEmpty()) {
      long total = memberships.count(company, criteria.role(), statuses);
      var batch =
          criteria.offset() >= total
              ? List.<Membership>of()
              : memberships.findBatch(
                  company,
                  criteria.role(),
                  statuses,
                  criteria.descending(),
                  null,
                  criteria.size(),
                  criteria.offset());
      return result(criteria, enrich(batch), total);
    }

    return scan(company, criteria, criteria.size(), false);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<MemberPage.Item> all(
      UUID userId, UUID requestedCompany, MemberSearchCriteria criteria) {
    UUID company = policy.requireAdministrator(userId, requestedCompany, false);
    if (criteria.search().isEmpty()
        && memberships.count(company, criteria.role(), criteria.statuses()) > ALL_LIMIT) {
      throw new MemberListLimitException(ALL_LIMIT);
    }
    return scan(company, criteria, ALL_LIMIT, true).items();
  }

  private MemberPage scan(UUID company, MemberSearchCriteria criteria, int limit, boolean bounded) {
    var statuses = criteria.statuses();
    var items = new ArrayList<MemberPage.Item>();
    long offset = bounded ? 0 : criteria.offset();
    long total = 0;
    UUID after = null;
    int batchSize = MemberIdentityDirectory.MAX_BATCH;
    // Read a stable membership snapshot in UUID order. Identity data is live per batch.
    while (true) {
      var batch =
          memberships.findBatch(
              company, criteria.role(), statuses, criteria.descending(), after, batchSize, 0);
      for (var item : enrich(batch)) {
        if (matches(item, criteria.search())) {
          if (bounded && total == limit) {
            throw new MemberListLimitException(ALL_LIMIT);
          }
          if (total >= offset && items.size() < limit) {
            items.add(item);
          }
          total++;
        }
      }
      if (batch.size() < batchSize) {
        return result(criteria, items, total);
      }
      after = batch.getLast().id();
    }
  }

  private List<MemberPage.Item> enrich(List<Membership> batch) {
    if (batch.isEmpty()) {
      return List.of();
    }
    var identities = directory.lookup(batch.stream().map(Membership::userId).toList());
    return batch.stream()
        .map(member -> {
          var identity = identities.get(member.userId());
          if (identity == null) {
            throw new AuthorizationUnavailableException();
          }
          return new MemberPage.Item(member, identity.name(), identity.email());
        })
        .toList();
  }

  private static boolean matches(MemberPage.Item item, String search) {
    return item.name().toLowerCase(Locale.ROOT).contains(search)
        || item.email().toLowerCase(Locale.ROOT).contains(search);
  }

  private static MemberPage result(
      MemberSearchCriteria criteria, List<MemberPage.Item> items, long total) {
    return new MemberPage(
        List.copyOf(items),
        criteria.page(),
        criteria.size(),
        total,
        Math.ceilDiv(total, criteria.size()));
  }
}
