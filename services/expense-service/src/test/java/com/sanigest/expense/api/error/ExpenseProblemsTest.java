package com.sanigest.expense.api.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sanigest.expense.application.exception.ExpenseFailure;
import com.sanigest.security.api.request.company.CompanySelector;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpenseProblemsTest {
  @Test
  void mapsExpenseAndCompanySelectionFailures() {
    var problems = new ExpenseProblems();
    assertEquals(
        404,
        problems.expense(new ExpenseFailure(ExpenseFailure.Kind.NOT_FOUND, "missing")).getStatus());
    for (var kind : ExpenseFailure.Kind.values()) {
      var problem = problems.expense(new ExpenseFailure(kind, kind.name()));
      assertEquals(kind.name(), problem.getProperties().get("errorCode"));
    }
    var invalid =
        assertThrows(
            CompanySelector.InvalidContext.class,
            () -> CompanySelector.resolve(List.of(), null, true));
    assertEquals(400, problems.selection(invalid).getStatus());
  }
}
