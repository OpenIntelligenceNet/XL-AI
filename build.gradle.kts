plugins {
    // Upgraded from 8.2.0 to 8.9.0 to support Android 15/16
    id("com.android.application") version "8.9.0" apply false
    id("com.android.library") version "8.9.0" apply false
    
    // Upgraded Kotlin for compatibility with higher SDKs
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.0" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}