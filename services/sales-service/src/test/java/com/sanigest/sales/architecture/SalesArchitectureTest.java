package com.sanigest.sales.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.sanigest.sales.application.port.out.integration.CatalogClient;
import com.sanigest.sales.application.port.out.integration.SalesAuthorization;
import com.sanigest.sales.application.port.out.persistence.ReceivableRepository;
import com.sanigest.sales.application.port.out.persistence.SaleIdempotencyRepository;
import com.sanigest.sales.application.port.out.persistence.SaleQueryRepository;
import com.sanigest.sales.application.port.out.persistence.SaleRepository;
import com.sanigest.sales.application.usecase.sale.CreateSale;
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
  void createsSalesThroughOutgoingPorts() {
    classes()
        .that()
        .haveFullyQualifiedName(CreateSale.class.getName())
        .should()
        .dependOnClassesThat()
        .areAssignableTo(CatalogClient.class)
        .andShould()
        .dependOnClassesThat()
        .areAssignableTo(SalesAuthorization.class)
        .andShould()
        .dependOnClassesThat()
        .areAssignableTo(SaleRepository.class)
        .check(PRODUCTION);
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
  void isolatesCompanyAuthorizationBehindTheSalesPort() {
    classes()
        .that()
        .areAssignableTo(SalesAuthorization.class)
        .should()
        .resideInAnyPackage(
            "..sales.application.port.out.integration..", "..sales.infrastructure.company..")
        .check(PRODUCTION);
    classes()
        .that()
        .haveSimpleName("CompanySalesAuthorization")
        .should()
        .implement(SalesAuthorization.class)
        .check(PRODUCTION);
    noClasses()
        .that()
        .resideInAPackage("..sales.infrastructure.company..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..sales.domain..",
            "..sales.api..",
            "..sales.infrastructure.persistence..",
            "..sales.application.port.out.persistence..",
            "java.sql..",
            "jakarta.persistence..",
            "org.springframework.jdbc..",
            "org.springframework.data..")
        .check(PRODUCTION);
    noClasses()
        .that()
        .haveSimpleName("SecurityConfiguration")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..sales.application..",
            "..sales.domain..",
            "..sales.infrastructure.company..",
            "com.sanigest.companyaccess..")
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
    noClasses()
        .that()
        .resideOutsideOfPackage("..sales.infrastructure.persistence.jpa..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..sales.infrastructure.persistence.jpa..")
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
    classes()
        .that()
        .haveSimpleName("JpaSaleQueryRepository")
        .should()
        .implement(SaleQueryRepository.class)
        .check(PRODUCTION);
    classes()
        .that()
        .areAssignableTo(SaleIdempotencyRepository.class)
        .should()
        .resideInAnyPackage(
            "..sales.application.port.out.persistence..",
            "..sales.infrastructure.persistence.jpa..")
        .check(PRODUCTION);
    classes()
        .that()
        .haveSimpleName("PostgresReceivableRepository")
        .should()
        .implement(ReceivableRepository.class)
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
        .check(PRODUCTION);
  }
}
