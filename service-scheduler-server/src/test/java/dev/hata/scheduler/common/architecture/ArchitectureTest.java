package dev.hata.scheduler.common.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Encodes the architecture rules declared in AGENTS.md §3 as executable assertions.
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

    // AGENTS.md §3: feature-based packages must not import each other.
    private static final String[] FEATURE_PACKAGES = {
        "auth", "customer", "dealership", "appointment", "resource", "vehicle", "notification", "scheduling"
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
                        .resideInAPackage("..%s..".formatted(a))
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..%s..".formatted(b))
                        .allowEmptyShould(true);
                rule.check(classes);
            }
        }
    }

    @Test
    void commonDoesNotDependOnFeatures() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..common..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..auth..",
                        "..customer..",
                        "..dealership..",
                        "..appointment..",
                        "..resource..",
                        "..vehicle..",
                        "..notification..",
                        "..scheduling..")
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
}
