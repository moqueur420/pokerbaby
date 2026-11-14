import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
}

allprojects {
    group = "io.pokerbaby"
    version = "0.1.0-SNAPSHOT"
}

val junitBom = libs.junit.bom
val junitJupiter = libs.junit.jupiter
val junitLauncher = libs.junit.platform.launcher
val jacocoVersion = libs.versions.jacoco.get()

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    val documentedTypes = extensions.getByType<SourceSetContainer>()
        .named("main").get().allJava.matching {
            exclude("**/package-info.java", "**/module-info.java")
        }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
        withJavadocJar()
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = jacocoVersion
    }

    dependencies {
        "testImplementation"(platform(junitBom))
        "testImplementation"(junitJupiter)
        "testRuntimeOnly"(junitLauncher)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    tasks.withType<Javadoc>().configureEach {
        onlyIf("The module contains Java types to document") { !documentedTypes.isEmpty }
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).apply {
            charSet = "UTF-8"
            docEncoding = "UTF-8"
            addBooleanOption("Xdoclint:all", true)
            addBooleanOption("Werror", true)
        }
    }

    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.named("test"))
        reports {
            html.required.set(true)
            xml.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("slow")
        }
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    val testSourceSet = extensions.getByType<SourceSetContainer>().named("test")
    tasks.register<Test>("slowTest") {
        group = "verification"
        description = "Runs only the tests tagged slow, separately from the regular build"
        dependsOn(tasks.named("testClasses"))
        testClassesDirs = testSourceSet.get().output.classesDirs
        classpath = testSourceSet.get().runtimeClasspath
        useJUnitPlatform {
            includeTags("slow")
        }
        // Some modules have tests but no slow-tagged cases.
        failOnNoDiscoveredTests.set(false)
        shouldRunAfter(tasks.named("test"))
    }

    tasks.named("check") {
        dependsOn(tasks.named("javadoc"))
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.named("assemble") {
    dependsOn(subprojects.map { "${it.path}:assemble" })
}

tasks.register("slowTest") {
    group = "verification"
    description = "Runs slow-tagged tests in all modules without adding them to check or build"
    dependsOn(subprojects.map { "${it.path}:slowTest" })
}

tasks.wrapper {
    gradleVersion = "9.9.1"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228"
    networkTimeout.set(60_000)
    validateDistributionUrl.set(true)
}
