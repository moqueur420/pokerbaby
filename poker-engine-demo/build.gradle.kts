plugins {
    application
}

description = "Command-line demonstration of the poker engine"

dependencies {
    implementation(project(":poker-engine-core"))
    implementation(project(":poker-engine-json"))
}

application {
    mainClass.set("io.pokerbaby.demo.PokerBabyDemo")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}
