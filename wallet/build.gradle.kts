// Versions pinned to what is VERIFIED PRESENT in this machine's Gradle cache on
// 2026-08-02 (AGP 8.7.3 + Kotlin 2.1.21 under ~/.gradle/caches/modules-2, Gradle
// 8.10.2 dist under ~/.gradle/wrapper/dists) — the first build works offline-ish
// and Android Studio won't need to fetch a new toolchain at the venue.
// NOTE: Gradle 8.10.2 cannot RUN on a Java 25 JVM (this machine's PATH default).
// Android Studio uses its bundled JBR (Java 21) automatically; for CLI builds set
// JAVA_HOME to "C:\Program Files\Android\Android Studio\jbr" first.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
}
