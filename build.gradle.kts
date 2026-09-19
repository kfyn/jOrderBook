plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

sourceSets {
    create("jmh") {
        java.srcDir("src/jmh/java")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

dependencies {
    add("jmhImplementation", "org.openjdk.jmh:jmh-core:1.37")
    add("jmhAnnotationProcessor", "org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

val jmhClasspath = sourceSets["jmh"].runtimeClasspath

tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Run JMH benchmarks. Extra JMH flags via -PjmhArgs=\"<flags>\"."
    classpath = jmhClasspath
    mainClass = "org.openjdk.jmh.Main"
    jvmArgs("-Xmx1g")
    doFirst { mkdir(layout.buildDirectory.dir("jmh").get().asFile) }
    if (project.hasProperty("jmhArgs")) {
        args(project.property("jmhArgs").toString().split(' '))
    }
}

tasks.register<JavaExec>("jmhSmoke") {
    group = "benchmark"
    description = "Quick JMH smoke run (1 fork, 1s rounds, fail on error) — CI gate."
    classpath = jmhClasspath
    mainClass = "org.openjdk.jmh.Main"
    jvmArgs("-Xmx1g")
    doFirst { mkdir(layout.buildDirectory.dir("jmh").get().asFile) }
    args(
        "-i", "1", "-wi", "1", "-f", "1", "-t", "1",
        "-r", "1s", "-w", "1s",
        "-foe", "true",
        "-rf", "json", "-rff", "build/jmh/results.json"
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
