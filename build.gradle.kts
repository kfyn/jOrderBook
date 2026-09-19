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

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
    finalizedBy(tasks.jacocoTestReport)
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
    add("jmhImplementation", platform("org.junit:junit-bom:6.0.0"))
    add("jmhImplementation", "org.junit.jupiter:junit-jupiter")
    add("jmhRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}

val jmhClasspath = sourceSets["jmh"].runtimeClasspath

tasks.register<Test>("jmhTest") {
    group = "verification"
    description = "JMH benchmarks as JUnit dynamic tests (surfaced in the GitLab MR tests widget)."
    testClassesDirs = sourceSets["jmh"].output.classesDirs
    classpath = jmhClasspath
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required = true
        html.required = true
    }
    // resolved at configuration time; the action captures only the File (configuration-cache safe)
    val xmlFile = reports.xml.outputLocation.get().asFile
    doLast {
        // Print a machine-parseable percentage for GitLab's `coverage:` regex.
        if (!xmlFile.exists()) return@doLast
        val reader = javax.xml.stream.XMLInputFactory.newInstance().createXMLStreamReader(xmlFile.inputStream())
        var covered = 0L
        var missed = 0L
        while (reader.hasNext()) {
            if (reader.next() == javax.xml.stream.XMLStreamConstants.START_ELEMENT
                && reader.localName == "counter"
                && reader.getAttributeValue(null, "type") == "LINE") {
                covered = reader.getAttributeValue(null, "covered").toLong()
                missed = reader.getAttributeValue(null, "missed").toLong()
            }
        }
        reader.close()
        val pct = if (covered + missed == 0L) 0.0 else 100.0 * covered / (covered + missed)
        val pctStr = String.format(Locale.ROOT, "%.2f", pct)
        println("Coverage: $pctStr%")
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
    finalizedBy(tasks.jacocoTestReport)
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
        "-i", "1", "-wi", "1", "-f", "1", "-t", "1",
        "-r", "1s", "-w", "1s",
        "-foe", "true",
        "-rf", "json", "-rff", outDir.resolve("results.json").absolutePath
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    // jmh sources legitimately carry non-JMH annotations (e.g. JUnit @TestFactory in the
    // benchmark test); silence the "no processor claimed" lint without disabling processing
    if (name == "compileJmhJava") options.compilerArgs.add("-Xlint:-processing")
}
