import java.lang.String
import java.util.Locale

plugins {
    java
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

jacoco {
    toolVersion = "0.8.15"   // Java 25 (class file 69) support
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Minimum line coverage enforced by `gradle test` (CI gate).

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

// Runs the demo (`./gradlew run`).
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the demo: uncross the spec book and print the results."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "net.kfyn.ob.Main"
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required = true
        html.required = true
    }
    // resolved at configuration time; the action captures only the File (configuration-cache safe)
    val xmlFile = reports.xml.outputLocation.get().asFile
    doLast {
        // Print a machine-parseable percentage for GitLab's `coverage:` regex,
        // then enforce the gate. The report-level LINE counter is the last
        // `type="LINE"` counter in the XML document.
        val coverageGate = 90.0
        if (!xmlFile.exists()) return@doLast
        val reportText = xmlFile.readText()
        val covered = Regex("""<counter type="LINE" missed="\d+" covered="(\d+)"/>""")
            .findAll(reportText).map { it.groupValues[1].toLong() }.lastOrNull() ?: 0L
        val missed = Regex("""<counter type="LINE" missed="(\d+)" covered="\d+"/>""")
            .findAll(reportText).map { it.groupValues[1].toLong() }.lastOrNull() ?: 0L
        val pct = if (covered + missed == 0L) 0.0 else 100.0 * covered / (covered + missed)
        val pctStr = String.format(Locale.ROOT, "%.2f", pct)
        println("Coverage: $pctStr%")
        if (pct < coverageGate) {
            throw GradleException(String.format(Locale.ROOT, "line coverage %.2f%% below gate %.2f%%", pct, coverageGate))
        }
    }
}

tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Run JMH benchmarks. Extra JMH flags via -PjmhArgs=\"<flags>\"."
    classpath = jmhClasspath
    mainClass = "org.openjdk.jmh.Main"
    jvmArgs("-Xmx1g")
    // resolve the output dir at configuration time; the action captures only the File
    // (configuration-cache safe) and mkdirs is a no-op when the dir already exists
    val outDir = layout.buildDirectory.dir("jmh").get().asFile
    doFirst { if (!outDir.exists()) outDir.mkdirs() }
    val extra = providers.gradleProperty("jmhArgs").orNull?.split(' ') ?: emptyList()
    args(extra)
}

tasks.register<JavaExec>("jmhSmoke") {
    group = "benchmark"
    description = "Quick JMH smoke run (1 fork, 1s rounds, fail on error) — CI gate."
    classpath = jmhClasspath
    mainClass = "org.openjdk.jmh.Main"
    jvmArgs("-Xmx1g")
    val outDir = layout.buildDirectory.dir("jmh").get().asFile
    doFirst { if (!outDir.exists()) outDir.mkdirs() }
    args(
        "-i", "3", "-wi", "2", "-f", "2", "-t", "1",
        "-r", "1s", "-w", "1s",
        "-foe", "true",
        "-prof", "gc",
        "-rf", "json", "-rff", outDir.resolve("results.json").absolutePath
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    // jmh sources legitimately carry non-JMH annotations (e.g. JUnit @TestFactory in the
    // benchmark test); silence the "no processor claimed" lint without disabling processing
    if (name == "compileJmhJava") options.compilerArgs.add("-Xlint:-processing")
}