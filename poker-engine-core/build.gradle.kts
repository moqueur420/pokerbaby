import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    `java-library`
}

description = "Deterministic heads-up poker engine with no runtime dependencies"

val benchmark = sourceSets.create("benchmark") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

tasks.register<JavaExec>("benchmarkEvaluator") {
    group = "verification"
    description = "Runs an initial single-thread evaluator throughput measurement outside tests and coverage"
    dependsOn(benchmark.classesTaskName)
    classpath = benchmark.runtimeClasspath
    mainClass.set("io.pokerbaby.benchmark.HandEvaluatorBenchmark")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    jvmArgs("-Xms512m", "-Xmx512m", "-XX:ActiveProcessorCount=1", "-XX:+UseSerialGC")
}

tasks.register<JavaExec>("benchmarkHands") {
    group = "verification"
    description = "Measures complete hands with history and player views outside tests and coverage"
    dependsOn(benchmark.classesTaskName)
    classpath = benchmark.runtimeClasspath
    mainClass.set("io.pokerbaby.benchmark.HandEngineBenchmark")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    jvmArgs("-Xms512m", "-Xmx512m", "-XX:ActiveProcessorCount=1", "-XX:+UseSerialGC")
}

val productionClasses = sourceSets.main.get().output.classesDirs.asFileTree.matching {
    include("**/*.class")
    exclude("**/package-info.class")
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    onlyIf("The core contains production classes") { !productionClasses.isEmpty }
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
