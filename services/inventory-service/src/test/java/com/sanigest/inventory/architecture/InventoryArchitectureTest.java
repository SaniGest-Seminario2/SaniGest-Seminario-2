package com.sanigest.inventory.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class InventoryArchitectureTest {
  @Test
  void upholdsBoundaries() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.sanigest.inventory");
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..application..",
            "..infrastructure..",
            "..api..",
            "org.springframework..",
            "jakarta.persistence..",
            "java.sql..",
            "java.net..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..infrastructure..", "..api..", "org.springframework.jdbc..", "java.sql..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..", "..port.out.persistence..")
        .check(classes);
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.sanigest.catalog..", "com.sanigest.sales..", "com.sanigest.company..")
        .check(classes);
    slices().matching("com.sanigest.inventory.(*)..").should().beFreeOfCycles().check(classes);
  }
}
