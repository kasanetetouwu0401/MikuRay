plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.v2ray.ang.tools.GenerateThemeSnapshotKt")
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
}
