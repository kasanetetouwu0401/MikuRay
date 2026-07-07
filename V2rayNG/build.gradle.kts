import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

buildscript {
    dependencies {
        classpath(libs.gradle.license.plugin)
    }
}

val buildDate = ZonedDateTime.now(ZoneId.of("Asia/Jakarta"))
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

extra["BUILD_DATE"] = buildDate
