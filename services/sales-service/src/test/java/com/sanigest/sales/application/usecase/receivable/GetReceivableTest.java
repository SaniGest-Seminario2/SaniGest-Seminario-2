package com.sanigest.sales.application.usecase.receivable;

import static com.sanigest.sales.application.port.out.integration.SalesAuthorization.Permission.SALES_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sanigest.sales.application.exception.ReceivableNotFound;
import com.sanigest.sales.application.exception.SalesAccessForbidden;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.ReceivableRepository;
import com.sanigest.sales.domain.model.Receivable;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetReceivableTest {
  private final SalesAuthorization authorization = mock(SalesAuthorization.class);
  private final ReceivableRepository receivables = mock(ReceivableRepository.class);
  private final GetReceivable useCase = new GetReceivable(authorization, receivables);
  private final UUID id = UUID.randomUUID();
  private final UUID companyId = UUID.randomUUID();

  @Test
  void readsOnlyWithinTheAuthorizedCompany() {
    var account = mock(Receivable.class);
    when(authorization.requireMembership(SALES_READ)).thenReturn(companyId);
    when(receivables.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(account));
    assertThat(useCase.execute(id)).isSameAs(account);
  }

  @Test
  void returnsNotFoundWhenTheAccountIsOutsideTheAuthorizedScope() {
    when(authorization.requireMembership(SALES_READ)).thenReturn(companyId);
    when(receivables.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(ReceivableNotFound.class);
  }

  @Test
  void deniesAccessBeforeReadingTheDatabase() {
    when(authorization.requireMembership(SALES_READ)).thenThrow(new SalesAccessForbidden());
    assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(SalesAccessForbidden.class);
    verifyNoInteractions(receivables);
  }
}
