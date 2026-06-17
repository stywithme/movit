package com.movit.core.data.repository

import com.movit.core.network.dto.VerifyAppStoreRequest
import com.movit.shared.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BillingSyncRepositoryTest {

    @Test
    fun verifyAppStore_success_returnsProStatusFromFixture() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val requestBody = readFixture("verify-app-store-request.json")
            val responseBody = readFixture("verify-app-store-response.json")
            val engine = MockEngine { request ->
                assertTrue(request.url.encodedPath.endsWith("/subscriptions/app-store/verify"))
                assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
                respond(content = responseBody, headers = jsonHeaders())
            }
            val repo = BillingSyncRepository(testBillingApi(engine, platform)) { platform }

            val parsedRequest = com.movit.core.network.MovitJson.decodeFromString(
                VerifyAppStoreRequest.serializer(),
                requestBody,
            )
            val result = repo.verifyAppStore(parsedRequest)

            assertTrue(result is AppResult.Success)
            assertEquals(true, result.value.status?.isPro)
            assertEquals("sub-001", result.value.subscription?.id)
            assertEquals("active", result.value.subscription?.status)
        }
    }

    @Test
    fun verifyAppStore_apiFailure_returnsFailure() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val engine = MockEngine {
                respond("", HttpStatusCode.InternalServerError)
            }
            val repo = BillingSyncRepository(testBillingApi(engine, platform)) { platform }
            val request = VerifyAppStoreRequest(
                planId = "550e8400-e29b-41d4-a716-446655440000",
                billingPeriod = "monthly",
                productId = "movit.pro.monthly",
                transactionId = "2000000123456789",
                originalTransactionId = "2000000123456789",
                signedTransactionInfo = "signed.jwt.payload",
            )

            val result = repo.verifyAppStore(request)

            assertTrue(result is AppResult.Failure)
            assertEquals("App Store verify failed (500)", result.message)
        }
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun readFixture(name: String): String {
        javaClass.classLoader?.getResource("fixtures/$name")?.readText()?.let { return it }
        val candidates = listOf(
            "src/commonTest/resources/fixtures/$name",
            "core/data/src/commonTest/resources/fixtures/$name",
            "../core/network/src/commonTest/resources/fixtures/$name",
        )
        for (relative in candidates) {
            val file = java.io.File(relative)
            if (file.isFile) return file.readText()
        }
        error("Missing fixture: fixtures/$name")
    }
}
