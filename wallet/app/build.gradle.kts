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
        // DEMO_URL — Session 1 spike leftover, kept as the documented interim
        // fallback: if WebViewAssetLoader ever fights us, the bridge can be
        // pointed at scripts/prover-bench's server (node serve.mjs + adb
        // reverse tcp:4173 tcp:4173). Session 5 default is the asset loader
        // (see ProverWebViewBridge.ENTRY_URL); this URL is otherwise unused.
        // ------------------------------------------------------------------
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

dependencies {
    // Session 5 bridge:
    //  - webkit: WebViewAssetLoader — serves assets/prover/* on the secure
    //    https://appassets.androidplatform.net origin (no dev server).
    //    1.12.1 = last release aligned with compileSdk 35 (newer ones want 36).
    //  - coroutines: suspend generateProof/selftest with withTimeout(90s).
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2") // cached locally 2026-08-02

    // Session 5 step 5 (Kotlin custody/sign/submit — see wallet/ package):
    //  - eddsa: Ed25519 signing. Android Keystore has NO Ed25519 (API 33/35)
    //    and platform Conscrypt does not expose Signature("Ed25519"); this is
    //    the library java-stellar-sdk itself used for years. API verified
    //    against the resolved jar (javap, 2026-08-03); output additionally
    //    verified byte-exact vs @stellar/stellar-sdk in StellarAccountTest.
    //  - security-crypto: EncryptedSharedPreferences for the seed + CT scalar
    //    (project custody rule). 1.1.0 stable; API verified via javap.
    implementation("net.i2p.crypto:eddsa:0.3.0")
    implementation("androidx.security:security-crypto:1.1.0")

    // Fixture-replay tests for the byte-level signing path (JVM, no device).
    testImplementation("junit:junit:4.13.2")
}
