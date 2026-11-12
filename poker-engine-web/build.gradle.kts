plugins {
    application
}

description = "Local browser interface for playing against a simple bot"

dependencies {
    implementation(project(":poker-engine-core"))
}

application {
    mainClass.set("io.pokerbaby.web.PokerBabyWeb")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}
