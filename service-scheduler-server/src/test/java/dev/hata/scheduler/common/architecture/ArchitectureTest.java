package dev.hata.scheduler.common.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Encodes the architecture rules declared in AGENTS.md §4 as executable assertions.
 *
 * <p>Violations fail the regular JUnit run, which means the Gradle {@code test} task fails
 * and so does {@code check}.
 */
@SuppressWarnings("PMD.LooseCoupling")
class ArchitectureTest {

    private static final String BASE = "dev.hata.scheduler";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    // Feature packages that must remain completely independent
    private static final String[] FEATURE_PACKAGES = {
        "auth", "customer", "dealership", "appointment", "resource", "vehicle", "scheduling"
    };

    @Test
    void featurePackagesAreIndependent() {
        for (String a : FEATURE_PACKAGES) {
            for (String b : FEATURE_PACKAGES) {
                if (a.equals(b)) {
                    continue;
                }
                ArchRule rule = noClasses()
                        .that()
                        .resideInAPackage("dev.hata.scheduler.%s..".formatted(a))
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("dev.hata.scheduler.%s..".formatted(b))
                        .allowEmptyShould(true);
                rule.check(classes);
            }
        }
    }

    @Test
    void featuresDoNotDependOnInfra() {
        for (String feature : FEATURE_PACKAGES) {
            ArchRule rule = noClasses()
                    .that()
                    .resideInAPackage("dev.hata.scheduler.%s..".formatted(feature))
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("dev.hata.scheduler.infra..")
                    .allowEmptyShould(true);
            rule.check(classes);
        }
    }

    @Test
    void commonDoesNotDependOnFeaturesOrInfra() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("dev.hata.scheduler.common..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "dev.hata.scheduler.auth..",
                        "dev.hata.scheduler.customer..",
                        "dev.hata.scheduler.dealership..",
                        "dev.hata.scheduler.appointment..",
                        "dev.hata.scheduler.resource..",
                        "dev.hata.scheduler.vehicle..",
                        "dev.hata.scheduler.scheduling..",
                        "dev.hata.scheduler.infra..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void infraDoesNotDependOnFeatures() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("dev.hata.scheduler.infra..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "dev.hata.scheduler.auth..",
                        "dev.hata.scheduler.customer..",
                        "dev.hata.scheduler.dealership..",
                        "dev.hata.scheduler.appointment..",
                        "dev.hata.scheduler.resource..",
                        "dev.hata.scheduler.vehicle..",
                        "dev.hata.scheduler.scheduling..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void controllersOnlyReachServiceLayer() {
        ArchRule rule = classes()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .onlyAccessClassesThat()
                .resideInAnyPackage(
                        "..controller..",
                        "..service..",
                        "..dto..",
                        "..event..",
                        "..model..",
                        "..exception..",
                        "..common..",
                        "java..",
                        "jakarta..",
                        "org.springframework..",
                        "io.micrometer..",
                        "io.swagger..",
                        "org.slf4j..",
                        "org.springdoc..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void servicesDoNotDependOnServletApi() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..service..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.servlet..", "javax.servlet..")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    void noMethodsUseSynchronizedModifierToPreventVirtualThreadPinning() {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods()
                .should()
                .haveModifier(com.tngtech.archunit.core.domain.JavaModifier.SYNCHRONIZED);
        rule.check(classes);
    }
}
