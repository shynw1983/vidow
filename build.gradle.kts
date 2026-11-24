plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Optional fast build dir outside iCloud-synced Desktop when passing -PfastBuildDir=/tmp/...
val fastBuildRoot = providers.gradleProperty("fastBuildDir").orNull
if (!fastBuildRoot.isNullOrBlank()) {
    allprojects {
        layout.buildDirectory.set(file("$fastBuildRoot/${project.name}"))
    }
}
