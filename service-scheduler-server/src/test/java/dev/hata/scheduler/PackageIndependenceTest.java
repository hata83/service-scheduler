package dev.hata.scheduler;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Proves the package rules of AGENTS constraint 1 and the layered structure.
 *
 * <p>The layout has three levels:
 *
 * <ul>
 *   <li>The feature packages hold the domain, the application, the data transfer
 *       objects and the adapter of one feature.
 *   <li>The {@code common} package holds the shared kernel and the ports that
 *       several features use.
 *   <li>The {@code infra} package holds the adapters to the middleware.
 * </ul>
 *
 * <p>The dependency direction runs inward. The features never depend on the
 * middleware, and the shared kernel never depends on a feature.
 */
@AnalyzeClasses(packages = "dev.hata.scheduler", importOptions = ImportOption.DoNotIncludeTests.class)
@SuppressWarnings("PMD.TestClassWithoutTestCases") // The ArchUnit rules live in the fields, not in the methods.
class PackageIndependenceTest {

    private static final String[] OTHER_FEATURES = {
        "dev.hata.scheduler.auth..",
        "dev.hata.scheduler.customer..",
        "dev.hata.scheduler.dealership..",
        "dev.hata.scheduler.appointment..",
        "dev.hata.scheduler.resource..",
        "dev.hata.scheduler.vehicle..",
        "dev.hata.scheduler.notification..",
        "dev.hata.scheduler.scheduling.."
    };

    @ArchTest
    static final ArchRule authPackageDoesNotImportOtherFeaturePackages = noClasses()
            .that()
            .resideInAPackage("dev.hata.scheduler.auth..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.hata.scheduler.customer..",
                    "dev.hata.scheduler.dealership..",
                    "dev.hata.scheduler.appointment..",
                    "dev.hata.scheduler.resource..",
                    "dev.hata.scheduler.vehicle..",
                    "dev.hata.scheduler.notification..",
                    "dev.hata.scheduler.scheduling..")
            .because("AGENTS constraint 1 keeps the feature packages independent");

    @ArchTest
    static final ArchRule customerPackageDoesNotImportOtherFeaturePackages = noClasses()
            .that()
            .resideInAPackage("dev.hata.scheduler.customer..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.hata.scheduler.auth..",
                    "dev.hata.scheduler.dealership..",
                    "dev.hata.scheduler.appointment..",
                    "dev.hata.scheduler.resource..",
                    "dev.hata.scheduler.vehicle..",
                    "dev.hata.scheduler.notification..",
                    "dev.hata.scheduler.scheduling..")
            .because("the customer package is independent and communicates through the common ports");

    @ArchTest
    static final ArchRule dealershipPackageDoesNotImportOtherFeaturePackages = noClasses()
            .that()
            .resideInAPackage("dev.hata.scheduler.dealership..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "dev.hata.scheduler.auth..",
                    "dev.hata.scheduler.customer..",
                    "dev.hata.scheduler.appointment..",
                    "dev.hata.scheduler.resource..",
                    "dev.hata.scheduler.vehicle..",
                    "dev.hata.scheduler.notification..",
                    "dev.hata.scheduler.scheduling..")
            .because("the dealership package is independent and communicates through the common ports");

    @ArchTest
    static final ArchRule commonPackageDoesNotImportFeaturePackages = noClasses()
            .that()
            .resideInAPackage("dev.hata.scheduler.common..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(OTHER_FEATURES)
            .because("the common package holds the shared kernel, so it stays below the features");

    @ArchTest
    static final ArchRule commonPackageDoesNotImportInfra = noClasses()
            .that()
            .resideInAPackage("dev.hata.scheduler.common..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.hata.scheduler.infra..")
            .because("the shared kernel must not know the middleware adapters");

    @ArchTest
    static final ArchRule featurePackagesDoNotDependOnInfra = noClasses()
            .that()
            .resideInAnyPackage(OTHER_FEATURES)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.hata.scheduler.infra..")
            .because("a feature uses the ports, so it never depends on the middleware adapters");

    @ArchTest
    static final ArchRule featurePackagesDoNotDependOnOtherFeaturePackages = noClasses()
            .that()
            .resideInAnyPackage("dev.hata.scheduler.auth..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("dev.hata.scheduler.customer..", "dev.hata.scheduler.dealership..")
            .because("the features share the common ports instead of each other");
}
