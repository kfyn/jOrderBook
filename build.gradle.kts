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
}
