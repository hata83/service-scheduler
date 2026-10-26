import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("jacoco")
    id("com.diffplug.spotless")
    checkstyle
    pmd
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

extensions.configure<SpotlessExtension> {
    java {
        palantirJavaFormat("2.71.0")
        formatAnnotations()
        removeUnusedImports()
        importOrder(
            "java|javax",
            "dev.hata.scheduler",
            ""
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = "10.21.0"
    configDirectory = layout.projectDirectory.dir("config/checkstyle").asFile
    isShowViolations = true
}

pmd {
    toolVersion = "7.16.0"
    ruleSetFiles = files(layout.projectDirectory.file("config/pmd/pmd-ruleset.xml"))
    isConsoleOutput = true
}

dependencies {
    // Spring Boot Core & Web MVC
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database & Migrations — PostgreSQL only, no in-memory SQL engine (HIL-1 round 4)
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Auth: JWT session tokens (ADR-005)
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // Observability: JSON logging, tracing, Prometheus metrics (design D12)
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // OpenAPI 3.1 & Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    // Testing & Quality Gates
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")

    // Integration tests: Testcontainers core spins up PostgreSQL 16 + Redis 7
    // containers directly (design D3); versions come from the Boot BOM property
    // testcontainers.version (2.0.5)
    testImplementation("org.testcontainers:testcontainers")
}

tasks.withType<Test> {
    useJUnitPlatform()
    environment("SPRING_PROFILES_ACTIVE", "test")
    finalizedBy(tasks.jacocoTestReport)
}

// The reset uses JDBC and the Redis client, so it works against the Docker
// Compose services and against the CI service containers.
tasks.register<JavaExec>("resetAuthFixtures") {
    group = "verification"
    description = "Restores the seeded fixtures so the Bruno and Playwright suites can run again."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.hata.scheduler.platform.AuthFixtureReset"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            // Matches the service classes only. The inner classes carry a $ suffix.
            includes = listOf("*Service")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
