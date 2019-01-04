package dependencies

object Versions {
    // Android
    val android_gradle_plugin = "3.2.1"
    val support = "1.0.0"
    val lifecycle = "2.1.0-alpha01"
    val room = "2.1.0-alpha03"
    val navigation = "1.0.0-alpha05"
    val work = "1.0.0-alpha12"
    val app_compat = "1.1.0-alpha01"
    val material = "1.1.0-alpha02"
    val recycler_view = "1.1.0-alpha01"
    val transision = "1.1.0-alpha01"
    val media = "1.1.0-alpha01"
    val constraint_layout = "2.0.0-alpha3"
    val ktx = "1.0.1"

    // Crashlytics
    val fabric_gradle_plugin = "1.26.1"
    val crashlytics = "2.9.7"

    // Google
    val firebase = "16.0.1"
    val firebase_messageing = "17.0.0"
    val firebase_ml = "16.0.0"
    val google_service_plugin = "4.2.0"

    // Kotlin
    val kotlin = "1.2.71"
    val kotlin_coroutine = "0.30.2"

    // Test
    val junit = "4.12"
    val test_runner = "1.1.1"
    val espresso = "3.1.1"
    val mockito = "2.12.0"

    // 3rd party
    val glide = "4.7.1"

    // Mozilla
    val android_components = "0.27.0"

    // Adjust
    val adjust = "4.15.1"
    val android_install_referrer = "1.0"
}

object Deps {
    object support {
        val app_compat = "androidx.appcompat:appcompat:${Versions.app_compat}"
        val material = "com.google.android.material:material:${Versions.material}"
        val card_view = "androidx.cardview:cardview:${Versions.support}"
        val recycler_view = "androidx.recyclerview:recyclerview:${Versions.recycler_view}"
        val transition = "androidx.transition:transition:${Versions.transision}"
        val preference = "androidx.legacy:legacy-preference-v14:${Versions.support}"
        val media = "androidx.media:media:${Versions.media}"
        val v4 = "androidx.legacy:legacy-support-v4:${Versions.support}"
        val constraint_layout = "androidx.constraintlayout:constraintlayout:${Versions.constraint_layout}"
    }

    object navigation {
        val ui = "android.arch.navigation:navigation-ui:${Versions.navigation}"
        val fragment = "android.arch.navigation:navigation-fragment:${Versions.navigation}"
    }

    object work {
        val runtime = "android.arch.work:work-runtime:${Versions.work}"
    }

    object firebase {
        val core = "com.google.firebase:firebase-core:${Versions.firebase}"
        val messaging = "com.google.firebase:firebase-messaging:${Versions.firebase_messageing}"
        val ml_vision = "com.google.firebase:firebase-ml-vision:${Versions.firebase_ml}"
        val analytics = "com.google.android.gms:play-services-analytics:${Versions.firebase}" // Required by Adjust
    }

    object room {
        val runtime = "androidx.room:room-runtime:${Versions.room}"
        val compiler = "androidx.room:room-compiler:${Versions.room}"
    }

    object lifecycle {
        val common = "androidx.lifecycle:lifecycle-common-java8:${Versions.lifecycle}"
        val extension = "androidx.lifecycle:lifecycle-extensions:${Versions.lifecycle}"
    }

    object fabric {
        val crashlytics = "com.crashlytics.sdk.android:crashlytics:${Versions.crashlytics}"
        val plugin = "io.fabric.tools:gradle:${Versions.fabric_gradle_plugin}"
    }

    object mozilla {
        val search = "org.mozilla.components:browser-search:${Versions.android_components}"
        val telemetry = "org.mozilla.components:service-telemetry:${Versions.android_components}"
    }

    object ktx {
        val core = "androidx.core:core-ktx:${Versions.ktx}"
    }

    object kotlin {
        val stdlib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}"
        val coroutine = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.kotlin_coroutine}"
        val plugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"
    }

    object espresso {
        val core = "androidx.test.espresso:espresso-core:${Versions.espresso}"
    }

    object support_test {
        val runner = "androidx.test:runner:${Versions.test_runner}"
    }

    object mockito {
        val core = "org.mockito:mockito-core:${Versions.mockito}"
    }

    object gms {
        val plugin = "com.google.gms:google-services:${Versions.google_service_plugin}" // google-services plugin
    }

    val junit = "junit:junit:${Versions.junit}"
    val glide = "com.github.bumptech.glide:glide:${Versions.glide}"
    val adjust = "com.adjust.sdk:adjust-android:${Versions.adjust}"
    val install_referrer = "com.android.installreferrer:installreferrer:${Versions.android_install_referrer}"
}
