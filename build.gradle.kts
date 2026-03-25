plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "com.gameperf"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("com.gameperf.MainKt")
}

kotlin {
    jvmToolchain(17)
}
