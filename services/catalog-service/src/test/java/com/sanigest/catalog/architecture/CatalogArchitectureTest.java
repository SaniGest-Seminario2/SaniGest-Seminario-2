package com.sanigest.catalog.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
}
