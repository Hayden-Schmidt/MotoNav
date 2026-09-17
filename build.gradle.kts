// Root build file. Plugin versions declared here, applied per-module in app/build.gradle.kts.
// AGP bumped to 8.13.2 (from 8.7.0) — required by Navigation SDK 7.7.0+, see
// docs/RESEARCH_NOTES.md "Technical requirements" for the full Navigation SDK adoption record.
// Kotlin bumped 2.0.20 -> 2.2.20 (stage 1) — Navigation SDK 7.9.0 transitively pulls
// kotlin-stdlib/kotlin-reflect 2.3.0, whose .kotlin_module metadata 2.0.20's compiler can't read.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
