package com.sanigest.sales.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

class SalesArchitectureTest {
  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.sanigest.sales");

  @Test
  void keepsDomainAndApplicationIndependent() {
    classes()
        .that()
        .resideInAPackage("..sales.domain..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "..sales.domain..")
        .check(PRODUCTION);
    classes()
        .that()
        .resideInAPackage("..sales.application..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "..sales.application..", "..sales.domain..")
        .check(PRODUCTION);
    slices().matching("com.sanigest.sales.(*)..").should().beFreeOfCycles().check(PRODUCTION);
  }

  @Test
  void isolatesCatalogTransportBehindTheOutgoingPort() {
    classes()
        .that()
        .areAssignableTo(CatalogClient.class)
        .should()
        .resideInAnyPackage(
            "..sales.application.port.out.integration..",
            "..sales.infrastructure.integration.catalog..")
        .check(PRODUCTION);
    classes()
        .that()
        .haveSimpleName("CatalogHttpClient")
        .should()
        .implement(CatalogClient.class)
        .check(PRODUCTION);
    noClasses()
        .that()
        .resideOutsideOfPackage("..sales.infrastructure.integration.catalog..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..sales.infrastructure.integration.catalog.dto..")
        .check(PRODUCTION);
    noClasses()
        .that()
        .haveSimpleName("CatalogIdentityInterceptor")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..sales.domain..", "..sales.application.port..")
        .check(PRODUCTION);
  }

  @Test
  void isolatesPersistenceAndOtherServices() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.sanigest.catalog..",
            "com.sanigest.company..",
            "com.sanigest.identity..",
            "com.sanigest.gateway..",
            "com.sanigest.expense..",
            "com.sanigest.finance..")
        .check(PRODUCTION);
    classes()
        .that()
        .areAnnotatedWith(Entity.class)
        .should()
        .resideInAPackage("..sales.infrastructure.persistence.jpa..")
        .check(PRODUCTION);
    classes()
        .that()
        .haveSimpleName("JpaSaleRepository")
        .should()
        .implement(SaleRepository.class)
        .check(PRODUCTION);
    noClasses()
        .that()
        .areAnnotatedWith(RestController.class)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..sales.infrastructure..", "..sales.application.port.out.persistence..",
            "..sales.application.port.out.integration..", "org.springframework.web.client..",
            "jakarta.persistence..", "org.springframework.data..")
        .allowEmptyShould(true)
        .check(PRODUCTION);
  }
}
