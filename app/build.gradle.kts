import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application") version "8.11.0"
}

val keystorePropsFile = rootProject.file("release.properties")
val keystoreProps = Properties()

if (keystorePropsFile.exists()) {
    FileInputStream(keystorePropsFile).use {
        keystoreProps.load(it)
    }
}

val hasValidSigningProps =
    keystorePropsFile.exists() &&
            listOf(
                "storeFile",
                "storePassword",
                "keyAlias",
                "keyPassword"
            ).all { key ->
                keystoreProps[key] != null
            }

android {

    namespace = "com.terbian"

    compileSdk = 36

    sourceSets {
        getByName("main") {

            assets.srcDirs(
                "src/main/assets"
            )

            jniLibs.srcDirs(
                "src/main/jniLibs"
            )
        }
    }

    androidResources {
        noCompress.addAll(
            listOf(
                "tb",
                "gz",
                "tar",
                "xz"
            )
        )
    }

    lint {
        checkReleaseBuilds = false
    }

    signingConfigs {

        if (hasValidSigningProps) {

            create("release") {

                storeFile =
                    rootProject.file(
                        keystoreProps["storeFile"] as String
                    )

                storePassword =
                    keystoreProps["storePassword"] as String

                keyAlias =
                    keystoreProps["keyAlias"] as String

                keyPassword =
                    keystoreProps["keyPassword"] as String
            }
        }
    }

    defaultConfig {

        applicationId = "com.terbian"

        minSdk = 26

        targetSdk = 36

        versionCode = 1

        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }

    buildTypes {

        debug {

            isMinifyEnabled = false
        }

        release {

            if (hasValidSigningProps) {

                signingConfig =
                    signingConfigs.getByName(
                        "release"
                    )
            }

            isMinifyEnabled = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {

        viewBinding = true
    }

    packaging {

        jniLibs {

            useLegacyPackaging = true
        }

        resources {

            excludes.add(
                "/META-INF/{AL2.0,LGPL2.1}"
            )

            excludes.add(
                "META-INF/kotlinx_coroutines_core.version"
            )

            pickFirsts.add(
                "nonJvmMain/default/linkdata/package_androidx/0_androidx.knm"
            )

            pickFirsts.add(
                "nonJvmMain/default/linkdata/root_package/0_.knm"
            )

            pickFirsts.add(
                "nonJvmMain/default/linkdata/module"
            )

            pickFirsts.add(
                "nativeMain/default/linkdata/root_package/0_.knm"
            )

            pickFirsts.add(
                "nativeMain/default/linkdata/module"
            )

            pickFirsts.add(
                "commonMain/default/linkdata/root_package/0_.knm"
            )

            pickFirsts.add(
                "commonMain/default/linkdata/module"
            )

            pickFirsts.add(
                "commonMain/default/linkdata/package_androidx/0_androidx.knm"
            )

            pickFirsts.add(
                "META-INF/kotlin-project-structure-metadata.json"
            )

            merges.add(
                "commonMain/default/manifest"
            )

            merges.add(
                "nonJvmMain/default/manifest"
            )

            merges.add(
                "nativeMain/default/manifest"
            )
        }
    }

    configurations.all {

        resolutionStrategy {

            force(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.22"
            )

            force(
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22"
            )

            force(
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22"
            )

            force(
                "androidx.collection:collection:1.4.2"
            )

            force(
                "androidx.annotation:annotation:1.8.1"
            )

            force(
                "androidx.core:core-ktx:1.8.0"
            )

            force(
                "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1"
            )

            force(
                "androidx.collection:collection-ktx:1.4.2"
            )
        }
    }
}

tasks.withType<JavaCompile>().configureEach {

    options.compilerArgs.add(
        "-Xlint:deprecation"
    )
}

dependencies {

    implementation(
        "androidx.appcompat:appcompat:1.6.1"
    )

    implementation(
        "androidx.constraintlayout:constraintlayout:2.1.4"
    )

    implementation(
        "com.google.android.material:material:1.9.0"
    )

    implementation(
        "androidx.startup:startup-runtime:1.1.1"
    )

    implementation(
        "androidx.interpolator:interpolator:1.0.0"
    )

    implementation(
        "androidx.lifecycle:lifecycle-viewmodel:2.6.2"
    )

    implementation(
        "androidx.lifecycle:lifecycle-livedata:2.6.2"
    )

    implementation(
        "androidx.fragment:fragment:1.6.2"
    )

    implementation(
        "androidx.viewpager2:viewpager2:1.0.0"
    )
}