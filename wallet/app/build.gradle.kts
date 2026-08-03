plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "xyz.raiz.sobre.spike"
    compileSdk = 35 // android-35 platform is installed locally; 36.1 also present if ever needed

    defaultConfig {
        applicationId = "xyz.raiz.sobre.spike"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-spike"

        // ------------------------------------------------------------------
        // DEMO_URL — the ONLY knob of this harness. Edit it here and rebuild.
        //
        // Default: http://localhost:3000 + `adb reverse tcp:3000 tcp:3000`
        //   -> localhost IS a secure context, so with the demo's COOP/COEP
        //      headers crossOriginIsolated becomes true = multithreaded bb.js.
        //      (Cleanest option per docs/spike-findings.md.)
        //
        // Alternative (no USB): your PC's LAN IP, e.g. "http://192.168.0.104:3000"
        //   (IP of 2026-08-02 — re-check with ipconfig). WORKS, but plain-http
        //   LAN origins are NOT secure contexts -> crossOriginIsolated stays
        //   false -> bb.js silently drops to 1 thread. Measure it as the
        //   degraded configuration, don't read it as a hard failure.
        // ------------------------------------------------------------------
        // Spike measurement targets: :4173 = scripts/prover-bench (proving only,
        // no Freighter — what we actually time) · :3000 = full CT demo app.
        buildConfigField("String", "DEMO_URL", "\"http://localhost:4173\"")
    }

    buildFeatures {
        buildConfig = true // AGP 8 defaults this to false; we need BuildConfig.DEMO_URL
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Intentionally ZERO dependencies beyond the Kotlin stdlib: the spike harness is a
// plain Activity + WebView. AndroidX/coroutines arrive in Session 5 with the bridge.
