plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.habit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.habit"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 发布版签名。keystore 放在项目根目录，密码就写在这里（个人项目够用；
        // 以后要上传代码到 GitHub，记得把它和密码都排除掉）
        create("release") {
            storeFile = rootProject.file("habit-release.jks")
            storePassword = "habit2026"
            keyAlias = "habit"
            keyPassword = "habit2026"
        }
    }

    buildTypes {
        debug {
            // 调试包也用发布密钥签名。
            // 好处：debug 包能直接覆盖安装正式版，手机上已有的数据不会丢。
            // 代价：debug 包和正式包签名相同，不能装在同一台机器上共存。
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            // 之前开了包级优化（optimization.enable = true），
            // 打出来的包装上就崩：IllegalAccessError: kotlin.collections.d extended by n6
            // ——R8 把这些包改名后，跨包的可见性被破坏了。
            // 这个 App 本来就只有 2~3 MB，关掉压缩换稳定，完全值得。
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
