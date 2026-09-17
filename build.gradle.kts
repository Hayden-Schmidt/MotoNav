// Root build file. Plugin versions declared here, applied per-module in app/build.gradle.kts.
// AGP bumped to 8.13.2 (from 8.7.0) — required by Navigation SDK 7.7.0+, see
// docs/RESEARCH_NOTES.md "Technical requirements" for the full Navigation SDK adoption record.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
