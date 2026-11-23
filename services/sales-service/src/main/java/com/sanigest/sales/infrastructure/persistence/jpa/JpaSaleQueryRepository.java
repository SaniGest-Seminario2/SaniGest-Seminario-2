package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.sales.application.dto.SalePage;
import com.sanigest.sales.application.dto.SaleSearch;
import com.sanigest.sales.application.port.out.persistence.SaleQueryRepository;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaSaleQueryRepository implements SaleQueryRepository {
  private final SaleRows rows;

  JpaSaleQueryRepository(SaleRows rows) {
    this.rows = rows;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SalePage search(UUID companyId, SaleSearch criteria) {
    var pattern =
        criteria.search().isEmpty()
            ? ""
            : "%"
                + criteria.search().replace("!", "!!").replace("%", "!%").replace("_", "!_")
                + "%";
    var status = criteria.status() == null ? "" : criteria.status().name();
    long total = rows.countMatching(companyId, pattern, status, criteria.from(), criteria.to());
    List<SalePage.Item> content =
        criteria.offset() >= total
            ? List.of()
            : rows
                .search(
                    companyId,
                    pattern,
                    status,
                    criteria.from(),
                    criteria.to(),
                    criteria.size(),
                    criteria.offset())
                .stream()
                .map(row ->
                    new SalePage.Item(
                        row.getId(),
                        row.getCode(),
                        row.getClientId(),
                        row.getClientName(),
                        row.getSaleDate(),
                        SaleMode.valueOf(row.getMode()),
                        SaleStatus.valueOf(row.getStatus()),
                        row.getTotal().setScale(2)))
                .toList();
    return new SalePage(content, criteria.page(), criteria.size(), total);
  }
}
