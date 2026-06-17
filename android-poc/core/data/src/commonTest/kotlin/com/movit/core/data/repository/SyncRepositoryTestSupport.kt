package com.movit.core.data.repository

import com.movit.core.data.local.FakeMovitLocalStore
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.OfflineWriteQueue
import com.movit.core.data.platform.PlatformMovitAuthTokenStore
import com.movit.core.network.MovitHttpClientConfig
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.MovitBillingApi
import com.movit.core.network.createMovitHttpClientWithEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

internal fun testMobileApi(
    engine: MockEngine,
    platform: com.movit.core.data.platform.MovitPlatformBindings? = null,
): MovitMobileApi {
    val client = if (platform != null) {
        val refreshClient = createMovitHttpClientWithEngine(engine)
        createMovitHttpClientWithEngine(
            engine = engine,
            auth = MovitHttpClientConfig(
                tokenStore = PlatformMovitAuthTokenStore { platform },
                baseUrlProvider = { "https://test.movit.local" },
                refreshHttpClient = refreshClient,
            ),
        )
    } else {
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(MovitJson)
            }
        }
    }
    return MovitMobileApi(client) { "https://test.movit.local/" }
}

internal fun testLocalStore(platform: FakeMovitPlatformBindings = FakeMovitPlatformBindings()): FakeMovitLocalStore =
    FakeMovitLocalStore(platform)

internal fun testOfflineWriteQueue(
    api: MovitMobileApi,
    platform: FakeMovitPlatformBindings,
    localStore: MovitLocalStore = testLocalStore(platform),
): OfflineWriteQueue = OfflineWriteQueue(localStore, api) { platform }

internal fun testMobileWriteRepository(
    engine: MockEngine,
    platform: FakeMovitPlatformBindings = FakeMovitPlatformBindings(),
    localStore: MovitLocalStore = testLocalStore(platform),
): MobileWriteSyncRepository {
    val api = testMobileApi(engine, platform)
    return MobileWriteSyncRepository(
        platform = { platform },
        localStore = { localStore },
        offlineWrites = testOfflineWriteQueue(api, platform, localStore),
    )
}

internal fun testWorkoutSessionRepository(
    engine: MockEngine,
    platform: FakeMovitPlatformBindings = FakeMovitPlatformBindings(),
    localStore: FakeMovitLocalStore = testLocalStore(platform),
): WorkoutSessionSyncRepository {
    val api = testMobileApi(engine, platform)
    return WorkoutSessionSyncRepository(
        api = api,
        platform = { platform },
        localStore = { localStore },
        mobileWrites = testMobileWriteRepository(engine, platform, localStore),
        trainingConfig = TrainingConfigRepository(localStore),
    )
}


internal fun testBillingApi(
    engine: MockEngine,
    platform: com.movit.core.data.platform.MovitPlatformBindings? = null,
): MovitBillingApi {
    val client = if (platform != null) {
        val refreshClient = createMovitHttpClientWithEngine(engine)
        createMovitHttpClientWithEngine(
            engine = engine,
            auth = MovitHttpClientConfig(
                tokenStore = PlatformMovitAuthTokenStore { platform },
                baseUrlProvider = { "https://test.movit.local" },
                refreshHttpClient = refreshClient,
            ),
        )
    } else {
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(MovitJson)
            }
        }
    }
    return MovitBillingApi(client) { "https://test.movit.local/" }
}
internal fun successJson(body: String): String = body
