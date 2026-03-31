plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "com.gameperf"
version = "6.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))


    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

application {
    mainClass.set("com.gameperf.MainKt")
}

kotlin {
    jvmToolchain(8)
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.gameperf.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
