import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort

plugins {
    `java-library`
    checkstyle
    jacoco
    id("com.github.spotbugs") version "6.0.26"
    id("me.champeau.jmh") version "0.7.2"
}

group = "dev.concurrentcollections"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
    // Concurrent/stress-style tests can run long; keep unit-scope tests fast
    // and let dedicated soak/stress suites (added in later milestones) opt
    // into longer timeouts explicitly rather than slowing down every `test` run.
    maxHeapSize = "1g"
    jvmArgs("-XX:+UseParallelGC")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

checkstyle {
    toolVersion = "10.20.2"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

spotbugs {
    toolVersion.set("4.8.6")
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.MEDIUM)
}

tasks.spotbugsMain {
    reports.create("html") {
        required.set(true)
    }
}

tasks.spotbugsTest {
    reports.create("html") {
        required.set(true)
    }
}

jmh {
    // Fast "smoke" profile by default (used by the PR pipeline); the
    // nightly pipeline passes -Pjmh.warmupIterations / -Pjmh.iterations /
    // -Pjmh.fork to run the full matrix from docs/benchmarks/methodology.md
    // without needing a second Gradle config block to stay in sync.
    warmupIterations.set(intProjectProperty("jmh.warmupIterations", 1))
    iterations.set(intProjectProperty("jmh.iterations", 1))
    fork.set(intProjectProperty("jmh.fork", 1))
    threads.set(1)
    resultFormat.set("JSON")
    resultsFile.set(project.layout.buildDirectory.file("results/jmh/results.json").get().asFile)
}

fun intProjectProperty(name: String, default: Int): Int =
    (project.findProperty(name) as String?)?.toInt() ?: default

tasks.named("check") {
    dependsOn(tasks.jacocoTestReport)
}
