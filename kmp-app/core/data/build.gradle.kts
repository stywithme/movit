plugins {
    id("movit.kmp.core")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

movitKmp {
    namespace = "com.movit.core.data"
}

sqldelight {
    databases {
        create("MovitDatabase") {
            packageName.set("com.movit.core.data.db")
            // sqlite-jdbc native extract fails on some Windows agents (AccessDenied under
            // C:\WINDOWS). Default off locally; CI passes -PverifySqlMigrations=true.
            // OutboxSchemaMigrationTest still covers migrate(1→2) without this flag.
            verifyMigrations.set(
                providers.gradleProperty("verifySqlMigrations")
                    .map { it.equals("true", ignoreCase = true) }
                    .orElse(false),
            )
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":core:model"))
            implementation(project(":core:network"))
            implementation(project(":core:training-engine"))
            implementation(project(":core:resources"))
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
            implementation(libs.sqldelight.sqlite.driver)
        }
        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}
