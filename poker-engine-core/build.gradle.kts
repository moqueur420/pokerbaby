import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    `java-library`
}

description = "Deterministic heads-up poker engine with no runtime dependencies"

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
