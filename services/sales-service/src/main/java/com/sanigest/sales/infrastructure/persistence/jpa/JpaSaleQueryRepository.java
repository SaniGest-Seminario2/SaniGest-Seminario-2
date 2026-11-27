package com.sanigest.sales.infrastructure.persistence.jpa;

import com.sanigest.sales.application.dto.SaleDetail;
import com.sanigest.sales.application.dto.SaleFinancials;
import com.sanigest.sales.application.dto.SalePage;
import com.sanigest.sales.application.dto.SaleSearch;
import com.sanigest.sales.application.port.out.persistence.SaleQueryRepository;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.domain.enums.ReceivableStatus;
import com.sanigest.sales.domain.enums.SaleMode;
import com.sanigest.sales.domain.enums.SaleStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaSaleQueryRepository implements SaleQueryRepository {
  private final SaleRows rows;

  private final SaleRepository sales;

  JpaSaleQueryRepository(SaleRows rows, SaleRepository sales) {
    this.rows = rows;
    this.sales = sales;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Optional<SaleDetail> detail(UUID companyId, UUID saleId) {
    return sales
        .findByCompanyIdAndId(companyId, saleId)
        .flatMap(sale -> financials(companyId, saleId).map(data -> new SaleDetail(sale, data)));
  }

  private Optional<SaleFinancials> financials(UUID companyId, UUID saleId) {
    return rows.financials(companyId, saleId)
        .map(row ->
            new SaleFinancials(
                row.getInvoiceId(),
                row.getInvoiceNumber(),
                row.getDueDate(),
                paymentStatus(row.getPaymentStatus()),
                money(row.getOutstandingBalance()),
                money(row.getCollected())));
  }

  private static BigDecimal money(BigDecimal value) {
    return value == null ? null : value.setScale(2);
  }

  private static ReceivableStatus paymentStatus(String value) {
    return value == null ? null : ReceivableStatus.valueOf(value);
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
                        row.getTotal().setScale(2),
                        row.getInvoiceNumber(),
                        paymentStatus(row.getPaymentStatus()),
                        money(row.getOutstandingBalance()),
                        money(row.getCollected())))
                .toList();
    return new SalePage(content, criteria.page(), criteria.size(), total);
  }
}
