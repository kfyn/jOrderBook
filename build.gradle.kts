plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)  // pin even if you run Gradle on 25
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0")) // JUnit 6 is current major
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

repositories {
    mavenCentral()
}
