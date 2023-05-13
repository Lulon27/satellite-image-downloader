plugins {
    kotlin("jvm") version "1.8.20"
    kotlin("plugin.serialization") version "1.8.10"

    id("org.openjfx.javafxplugin") version "0.0.13"
	application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-RC")
}

application {
    mainClass.set("Main")
}

java {
    sourceCompatibility = JavaVersion.VERSION_19
    targetCompatibility = JavaVersion.VERSION_19
}


javafx {
    version = "20"
    modules("javafx.controls", "javafx.fxml", "javafx.swing")
}

configure<SourceSetContainer>
{
    named("main")
    {
        java.srcDir("src")
        resources.srcDir("res")
    }
}