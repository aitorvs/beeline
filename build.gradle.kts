import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
}

group = "com.aitorvs.beeline"
version = "0.2.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test-junit"))
    implementation("com.jakewharton.picnic:picnic:0.5.0")
    implementation("com.github.ajalt.clikt:clikt:3.1.0")
    implementation("com.github.ajalt.mordant:mordant:2.0.0-alpha2")

}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "BeelineKt"
        attributes["Implementation-Version"] = archiveVersion
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
}
