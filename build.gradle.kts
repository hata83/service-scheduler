// Parent build: version surface only. Feature modules apply and configure the plugins.
// Core Gradle plugins (java, jacoco, checkstyle, pmd) are applied directly in the module.
plugins {
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.diffplug.spotless") version "7.0.2" apply false
}

allprojects {
    group = "dev.hata"
    version = "1.0.0-SNAPSHOT"
}
