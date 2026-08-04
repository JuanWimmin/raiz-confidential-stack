// Versions pinned to what is VERIFIED PRESENT in this machine's Gradle cache on
// 2026-08-02 (AGP 8.7.3 + Kotlin 2.1.21 under ~/.gradle/caches/modules-2, Gradle
// 8.10.2 dist under ~/.gradle/wrapper/dists) — the first build works offline-ish
// and Android Studio won't need to fetch a new toolchain at the venue.
// NOTE: Gradle 8.10.2 cannot RUN on a Java 25 JVM (this machine's PATH default).
// Android Studio uses its bundled JBR (Java 21) automatically; for CLI builds set
// JAVA_HOME to "C:\Program Files\Android\Android Studio\jbr" first.
// Session 6 addition: the Compose compiler is a Kotlin K2 built-in plugin since
// Kotlin 2.0, so its version MUST track the Kotlin version exactly (2.1.21).
// Verified present in this machine's cache on 2026-08-03:
//   modules-2/files-2.1/org.jetbrains.kotlin/compose-compiler-gradle-plugin/2.1.21
//   modules-2/metadata-2.106/descriptors/org.jetbrains.kotlin.plugin.compose/
//     org.jetbrains.kotlin.plugin.compose.gradle.plugin/2.1.21
// (metadata-2.106 is the descriptor store Gradle 8.10.2 uses here — same one
//  that already holds androidx.webkit 1.12.1 and eddsa 0.3.0 from our build.)
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
}
