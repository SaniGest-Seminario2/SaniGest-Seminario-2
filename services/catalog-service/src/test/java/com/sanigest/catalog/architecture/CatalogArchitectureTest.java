package com.sanigest.catalog.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CatalogArchitectureTest {
  private static final JavaClasses CATALOG =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.sanigest.catalog");

  @Test
  void domainStaysIndependentOfOuterLayers() {
    noClasses()
        .that()
        .resideInAPackage("..catalog.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..catalog.api..",
            "..catalog.application..",
            "..catalog.infrastructure..",
            "com.sanigest.companyaccess..",
            "org.springframework..",
            "jakarta.persistence..")
        .check(CATALOG);
  }

  @Test
  void useCasesDoNotKnowTransportOrPersistenceAdapters() {
    noClasses()
        .that()
        .resideInAPackage("..catalog.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..catalog.api..",
            "..catalog.infrastructure..",
            "org.springframework.web..",
            "org.springframework.data..",
            "org.springframework.jdbc..",
            "jakarta.persistence..")
        .check(CATALOG);
  }

  @Test
  void controllersReachPersistenceOnlyThroughUseCases() {
    noClasses()
        .that()
        .resideInAPackage("..catalog.api.controller..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..catalog.application.port.out.persistence..", "..catalog.infrastructure..")
        .check(CATALOG);
  }

  @Test
  void persistenceDoesNotUseHttpDtos() {
    noClasses()
        .that()
        .resideInAPackage("..catalog.infrastructure.persistence..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..catalog.api..")
        .check(CATALOG);
  }

  @Test
  void catalogFeaturesUseTheirOwnOperations() {
    slices()
        .matching("com.sanigest.catalog.application.usecase.(*)..")
        .should()
        .notDependOnEachOther()
        .check(CATALOG);
  }

  @Test
  void catalogItemDoesNotReuseClientOrSupplierTypes() {
    noClasses()
        .that()
        .haveSimpleNameContaining("CatalogItem")
        .should()
        .dependOnClassesThat()
        .haveNameMatching("com\\.sanigest\\.catalog\\..*(Client|Supplier).*")
        .check(CATALOG);
  }

  @Test
  void moneyRulesStayOutOfTheCatalogItemController() {
    noClasses()
        .that()
        .haveSimpleName("CatalogItemController")
        .should()
        .callMethodWhere(
            DescribedPredicate.describe(
                "call BigDecimal methods",
                call -> call.getTargetOwner().isEquivalentTo(BigDecimal.class)))
        .check(CATALOG);
  }

  @Test
  void httpDtosDoNotDependOnJpa() {
    noClasses()
        .that()
        .resideInAPackage("..catalog.api.dto..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("jakarta.persistence..", "..catalog.infrastructure..")
        .check(CATALOG);
  }
}
