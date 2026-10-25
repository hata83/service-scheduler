import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    id("java")
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.6"
    id("jacoco")
    id("com.diffplug.spotless") version "7.0.2"
    checkstyle
    pmd
}

group = "dev.hata"
version = "1.0.0-SNAPSHOT"

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
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database & Migrations
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Cache & Metrics
    implementation("com.github.ben-manes.caffeine:caffeine")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // OpenAPI 3.1 & Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    // Testing & Quality Gates
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}