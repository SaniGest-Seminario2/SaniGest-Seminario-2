package com.sanigest.company.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.net.URLConnection;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.ControllerAdvice;

class CompanyArchitectureTest {
  private static final String COMPANY = "com.sanigest.company";
  private static final String API = COMPANY + ".api..";
  private static final String DOMAIN = COMPANY + ".domain..";
  private static final String APPLICATION = COMPANY + ".application..";
  private static final String INFRASTRUCTURE = COMPANY + ".infrastructure..";
  private static final String OUTGOING_PORTS = COMPANY + ".application.port.out..";
  private static final String PERSISTENCE_PORTS = COMPANY + ".application.port.out.persistence..";
  private static final String JDBC_ADAPTERS = COMPANY + ".infrastructure.persistence.jdbc..";
  private static final String ACTIVE_ACCESS =
      COMPANY + ".application.port.out.persistence.MembershipRepository$ActiveAccess";

  private static final DescribedPredicate<JavaClass> PERSISTENCE =
      resideInAnyPackage(
          "java.sql..",
          "javax.sql..",
          "org.springframework.jdbc..",
          "org.springframework.dao..",
          "org.springframework.data..",
          "jakarta.persistence..",
          "javax.persistence..",
          "org.hibernate..",
          "org.postgresql..",
          "com.zaxxer.hikari..");

  private static final DescribedPredicate<JavaClass> HTTP_CLIENTS =
      resideInAnyPackage(
              "java.net.http..",
              "org.springframework.web.client..",
              "org.springframework.web.reactive.function.client..",
              "org.springframework.web.service.invoker..",
              "org.apache.hc.client5..",
              "org.apache.http.client..",
              "okhttp3..",
              "feign..")
          .or(JavaClass.Predicates.assignableTo(URLConnection.class));

  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
          .importPackages(COMPANY);

  @Test
  void domainOnlyUsesItsOwnModelAndNonTechnicalJavaTypes() {
    classes()
        .that()
        .resideInAPackage(DOMAIN)
        .should()
        .onlyDependOnClassesThat(
            resideInAnyPackage(DOMAIN, "java..").and(PERSISTENCE.or(HTTP_CLIENTS).negate()))
        .because("domain rules must stay independent of application, transport and storage")
        .check(PRODUCTION);
  }

  @Test
  void applicationAndItsPortsDoNotDependOnTransportOrPersistenceTechnology() {
    noClasses()
        .that()
        .resideInAPackage(APPLICATION)
        .should()
        .dependOnClassesThat(
            resideInAnyPackage(
                    API,
                    INFRASTRUCTURE,
                    "org.springframework.web..",
                    "org.springframework.http..",
                    "org.springframework.boot.webmvc..",
                    "org.springframework.security.web..",
                    "jakarta.servlet..",
                    "javax.servlet..")
                .or(HTTP_CLIENTS)
                .or(PERSISTENCE))
        .because("use cases and outgoing ports must use application and domain contracts")
        .check(PRODUCTION);
  }

  @Test
  void httpApiDelegatesPersistenceAndRemoteCallsToUseCases() {
    noClasses()
        .that()
        .resideInAPackage(API)
        .should()
        .dependOnClassesThat(
            resideInAnyPackage(INFRASTRUCTURE)
                // ResolveCompanyAccess returns this read model. The controller maps its
                // values to the HTTP response without accessing MembershipRepository.
                .or(resideInAnyPackage(OUTGOING_PORTS).and(name(ACTIVE_ACCESS).negate()))
                .or(PERSISTENCE)
                .or(HTTP_CLIENTS))
        .because("HTTP controllers and DTOs must stay on the application side of outgoing ports")
        .check(PRODUCTION);
  }

  @Test
  void controllersAndTheirAdviceBelongInTheApi() {
    classes()
        .that()
        .areMetaAnnotatedWith(Controller.class)
        .or()
        .areMetaAnnotatedWith(ControllerAdvice.class)
        .should()
        .resideInAPackage(API)
        .check(PRODUCTION);
  }

  @Test
  void infrastructureDoesNotUseControllersOrHttpDtos() {
    noClasses()
        .that()
        .resideInAPackage(INFRASTRUCTURE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(API)
        .check(PRODUCTION);
  }

  @Test
  void outgoingPortsDeclareInterfacesAndTheirImplementationsBelongInInfrastructure() {
    classes()
        .that()
        .resideInAPackage(OUTGOING_PORTS)
        .and()
        .areTopLevelClasses()
        .should()
        .beInterfaces()
        .check(PRODUCTION);
    classes()
        .that()
        .implement(resideInAnyPackage(OUTGOING_PORTS))
        .should()
        .resideInAPackage(INFRASTRUCTURE)
        .check(PRODUCTION);
  }

  @Test
  void jdbcRepositoriesStayInTheirAdapterPackageAndImplementPersistencePorts() {
    classes()
        .that()
        .areMetaAnnotatedWith(Repository.class)
        .should()
        .resideInAPackage(JDBC_ADAPTERS)
        .andShould()
        .implement(resideInAnyPackage(PERSISTENCE_PORTS))
        .check(PRODUCTION);
    classes()
        .that()
        .implement(resideInAnyPackage(PERSISTENCE_PORTS))
        .should()
        .resideInAPackage(JDBC_ADAPTERS)
        .check(PRODUCTION);
  }

  @Test
  void jdbcOperationsAreExecutedInsidePersistenceAdapters() {
    noClasses()
        .that()
        .resideOutsideOfPackage(JDBC_ADAPTERS)
        .should()
        .callMethodWhere(
            DescribedPredicate.describe(
                "execute JDBC operations",
                call ->
                    resideInAnyPackage("org.springframework.jdbc.core..", "java.sql..")
                            .test(call.getTargetOwner())
                        || (resideInAnyPackage("javax.sql..").test(call.getTargetOwner())
                            && call.getName().equals("getConnection"))))
        .because("composition can wire JDBC beans while adapters execute database operations")
        .check(PRODUCTION);
  }

  @Test
  void companyDoesNotUseOtherExecutableServicesClasses() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.sanigest.catalog..",
            "com.sanigest.sales..",
            "com.sanigest.identity..",
            "com.sanigest.expense..",
            "com.sanigest.finance..",
            "com.sanigest.gateway..")
        .because("services communicate through published contracts")
        .check(PRODUCTION);
  }

  @Test
  void companyLayersDoNotFormCycles() {
    slices().matching(COMPANY + ".(*)..").should().beFreeOfCycles().check(PRODUCTION);
  }
}
