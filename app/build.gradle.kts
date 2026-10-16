// Copyright 2026 deepcodecreate
// SPDX-License-Identifier: Apache-2.0

import java.net.URI
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

val nativeWork = layout.buildDirectory.dir("terbian-native").get().asFile

val buildProot = tasks.register("buildProot") {
    group = "terbian"
    description = "Builds termux/proot with the Android NDK into src/main/jniLibs (skips if impossible)"
    doLast {
        if (project.hasProperty("skipNative")) return@doLast
        val done = File(nativeWork, "proot.done")
        if (done.exists() && !project.hasProperty("forceProot")) return@doLast
        val ndk = try { android.ndkDirectory.absolutePath } catch (e: Throwable) { System.getenv("ANDROID_NDK_HOME") ?: "" }
        val extraDirs = listOfNotNull(System.getenv("PREFIX")?.let { "$it/bin" },
            "/data/data/com.itsaky.androidide/files/usr/bin", "/system/bin")
        val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotEmpty() } + extraDirs
        fun find(t: String): String? = pathDirs.map { File(it, t) }.firstOrNull { it.canExecute() }?.absolutePath
        val found = listOf("bash", "git", "curl", "make").associateWith { find(it) }
        val llvm = File(ndk, "toolchains/llvm/prebuilt")
        logger.lifecycle("[terbian] ndk='$ndk' llvm-prebuilt=${llvm.list()?.toList()} tools=$found")
        val missing = found.filterValues { it == null }.keys
        if (ndk.isEmpty() || !llvm.isDirectory || llvm.list().isNullOrEmpty() || missing.isNotEmpty()) {
            logger.warn("[terbian] cannot build proot here: ndkPath='$ndk' ndkToolchainOk=${llvm.isDirectory && !llvm.list().isNullOrEmpty()} " +
                "missingTools=$missing -> keeping existing jniLibs. Use GitHub Actions (build-proot.yml) instead.")
            return@doLast
        }
        nativeWork.mkdirs()
        val r = ProcessBuilder(found["bash"]!!, "tools/build-proot.sh").directory(projectDir).inheritIO().apply {
            environment()["ANDROID_NDK_HOME"] = ndk
            environment()["PATH"] = pathDirs.joinToString(File.pathSeparator)
            environment()["WORK"] = nativeWork.absolutePath
            environment()["OUT_DIR"] = file("src/main/jniLibs").absolutePath
            project.findProperty("prootAbis")?.let { environment()["ABIS"] = it.toString() }
        }.start().waitFor()
        if (r != 0) logger.warn("[terbian] proot build failed (exit $r) -> keeping the existing jniLibs")
    }
}

val fetchRootfs = tasks.register("fetchRootfs") {
    group = "terbian"
    description = "Downloads the official Debian 12 rootfs into src/main/assets/debian/rootfs.tb"
    doLast {
        if (project.hasProperty("skipNative")) return@doLast
        val dest = file("src/main/assets/debian/rootfs.tb")
        if (dest.exists() && dest.length() > 1_000_000) return@doLast
        val arch = (project.findProperty("rootfsArch") ?: "arm64v8").toString()
        val url = "https://github.com/debuerreotype/docker-debian-artifacts/raw/refs/heads/dist-$arch/bookworm/slim/oci/blobs/rootfs.tar.gz"
        try {
            logger.lifecycle("[terbian] downloading Debian 12 rootfs ($arch) ...")
            dest.parentFile.mkdirs()
            val tmp = File(dest.parentFile, "rootfs.tb.part")
            URI(url).toURL().openStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (tmp.length() < 1_000_000) { tmp.delete(); throw GradleException("download too small") }
            tmp.renameTo(dest)
        } catch (e: Exception) {
            logger.warn("[terbian] rootfs download failed (${e.message}). Put your own tar.gz at " +
                "src/main/assets/debian/rootfs.tb (URL: $url)")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(buildProot, fetchRootfs)
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
