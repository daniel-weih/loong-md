import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "com.loongmd"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.loongmd"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

compose.desktop {
    application {
        mainClass = "com.loongmd.MainKt"
        jvmArgs += listOf(
            "-Dapple.awt.application.name=LoongMD",
            "-Xdock:name=LoongMD",
            "-Xdock:icon=${project.file("src/desktopMain/resources/app_icon.png").absolutePath}"
        )
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "LoongMD"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "com.loongmd.desktop"
                iconFile.set(project.file("src/desktopMain/resources/LoongMD.icns"))
            }
        }
    }
}

tasks.configureEach {
    if (name == "packageDmg") {
        doFirst {
            val applicationsLink = layout.buildDirectory
                .dir("compose/binaries/main/app")
                .get()
                .asFile
                .resolve("Applications")
            if (applicationsLink.exists()) {
                applicationsLink.deleteRecursively()
            }
        }

        doLast {
            val iconSource = layout.projectDirectory.file("src/desktopMain/resources/app_icon.png").asFile
            if (!iconSource.exists()) return@doLast

            val dmgDir = layout.buildDirectory.dir("compose/binaries/main/dmg").get().asFile
            val dmgFile = dmgDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("dmg", ignoreCase = true) }
                ?.maxByOrNull { it.lastModified() }
                ?: return@doLast

            val tempIcon = File.createTempFile("dmg_icon_", ".png")
            val tempRsrc = File.createTempFile("dmg_icon_", ".rsrc")
            fun run(vararg cmd: String) {
                val process = ProcessBuilder(*cmd)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    error("Command failed (${cmd.joinToString(" ")}): $output")
                }
            }

            try {
                iconSource.copyTo(tempIcon, overwrite = true)
                run("sips", "-i", tempIcon.absolutePath)
                run("sh", "-c", "DeRez -only icns \"${tempIcon.absolutePath}\" > \"${tempRsrc.absolutePath}\"")
                run("Rez", "-append", tempRsrc.absolutePath, "-o", dmgFile.absolutePath)
                run("SetFile", "-a", "C", dmgFile.absolutePath)
            } finally {
                tempIcon.delete()
                tempRsrc.delete()
            }
        }
    }
}
