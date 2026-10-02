import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.jumpy.fidelite"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.jumpy.fidelite"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    // La cle de publication vit hors du depot (android/key.properties, ignore par git). Toutes les
    // versions doivent etre signees par elle : un telephone refuse de mettre a jour une application
    // signee par une autre cle, et le client devrait la desinstaller — ses points restent sur le
    // serveur, mais il le vivrait comme une perte. Sans ce fichier, on signe avec la cle de debug,
    // pour que `flutter run --release` marche sur n'importe quel poste.
    val proprietesDeLaCle = Properties().apply {
        val fichier = rootProject.file("key.properties")
        if (fichier.exists()) fichier.inputStream().use { load(it) }
    }

    signingConfigs {
        if (proprietesDeLaCle.getProperty("storeFile") != null) {
            create("publication") {
                storeFile = file(proprietesDeLaCle.getProperty("storeFile"))
                storePassword = proprietesDeLaCle.getProperty("storePassword")
                keyAlias = proprietesDeLaCle.getProperty("keyAlias")
                keyPassword = proprietesDeLaCle.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("publication") ?: signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
