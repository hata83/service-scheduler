package dev.hata.scheduler;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Proves the package independence rule of AGENTS constraint 1. A feature
 * package must not import another feature package. The shared code lives in
 * the {@code common} package.
 */
@AnalyzeClasses(packages = "dev.hata.scheduler", importOptions = ImportOption.DoNotIncludeTests.class)
@SuppressWarnings("PMD.TestClassWithoutTestCases") // The ArchUnit rules live in the fields, not in the methods.
class PackageIndependenceTest {

    @ArchTest
    static final ArchRule featurePackagesDoNotImportEachOther = noClasses()
            .that()
            .resideInAPackage("dev.hata.scheduler.auth..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.hata.scheduler.appointment..",
                    "dev.hata.scheduler.resource..",
                    "dev.hata.scheduler.customer..",
                    "dev.hata.scheduler.vehicle..",
                    "dev.hata.scheduler.notification..",
                    "dev.hata.scheduler.scheduling..")
            .because("AGENTS constraint 1 keeps the feature packages independent");

    @ArchTest
    static final ArchRule commonPackageDoesNotImportFeaturePackages = noClasses()
            .that()
            .resideInAPackage("dev.hata.scheduler.common..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.hata.scheduler.auth..",
                    "dev.hata.scheduler.appointment..",
                    "dev.hata.scheduler.resource..",
                    "dev.hata.scheduler.customer..",
                    "dev.hata.scheduler.vehicle..",
                    "dev.hata.scheduler.notification..",
                    "dev.hata.scheduler.scheduling..")
            .because("the common package holds the shared code, so it stays below the features");
}
