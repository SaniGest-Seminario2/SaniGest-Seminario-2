package com.sanigest.company.application.usecase.team;

import com.sanigest.company.application.dto.MemberPage;
import com.sanigest.company.application.dto.MemberSearchCriteria;
import com.sanigest.company.application.exception.AuthorizationUnavailableException;
import com.sanigest.company.application.port.out.integration.MemberIdentityDirectory;
import com.sanigest.company.application.port.out.persistence.MembershipRepository;
import com.sanigest.company.application.usecase.profile.GetMyCompanyUseCase;
import com.sanigest.company.domain.model.Membership;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListMyCompanyUsersUseCase {
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
    if (criteria.search().isEmpty()) {
      long total = memberships.count(company, criteria.role(), criteria.status());
      var batch =
          criteria.offset() >= total
              ? List.<Membership>of()
              : memberships.findBatch(
                  company,
                  criteria.role(),
                  criteria.status(),
                  criteria.descending(),
                  null,
                  criteria.size(),
                  criteria.offset());
      return result(criteria, enrich(batch), total);
    }

    var items = new ArrayList<MemberPage.Item>(criteria.size());
    long total = 0;
    UUID after = null;
    int batchSize = MemberIdentityDirectory.MAX_BATCH;
    // Read a stable membership snapshot in UUID order. Identity data is live per batch.
    while (true) {
      var batch =
          memberships.findBatch(
              company,
              criteria.role(),
              criteria.status(),
              criteria.descending(),
              after,
              batchSize,
              0);
      for (var item : enrich(batch)) {
        if (matches(item, criteria.search())) {
          if (total >= criteria.offset() && items.size() < criteria.size()) {
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
