package com.movit.core.network.contract

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.EntityAudioManifestApiResponse
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ExploreWorkoutApiResponse
import com.movit.core.network.dto.ExploreWorkoutUploadRequestDto
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.PlannedWorkoutApiResponse
import com.movit.core.network.dto.SyncMessageTemplateDto
import com.movit.core.network.dto.TrainingConfigApiResponse
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import com.movit.core.network.dto.UserProgramsApiResponse
import com.movit.core.network.dto.SubscriptionApiEnvelope
import com.movit.core.network.dto.VerifyAppStoreRequest
import com.movit.core.network.dto.VerifyAppStoreResponse
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DtoPayloadContractTest {

  @Test
  fun homeFixture_roundTripsWithoutLoss() {
    val raw = readFixture("home-response.json")
    val parsed = MovitJson.decodeFromString(HomeApiResponse.serializer(), raw)

    assertTrue(parsed.success)
    assertNotNull(parsed.data)
    assertEquals("Test User", parsed.data?.user?.name)
    assertEquals("active", parsed.data?.trainMode?.status)
    assertEquals("pw-001", parsed.data?.trainMode?.todayWorkout?.plannedWorkoutId)
    assertEquals(42, parsed.data?.stats?.totalWorkoutExecutions)

    val reencoded = MovitJson.encodeToString(HomeApiResponse.serializer(), parsed)
    val reparsed = MovitJson.decodeFromString(HomeApiResponse.serializer(), reencoded)
    assertEquals(parsed, reparsed)
  }

  @Test
  fun homeFixture_parsesWs1HomeFields() {
    val raw = readFixture("home-response.json")
    val parsed = MovitJson.decodeFromString(HomeApiResponse.serializer(), raw)

    assertEquals("wt-001", parsed.data?.trainMode?.todayWorkout?.workoutTemplateId)
    assertEquals(true, parsed.data?.trainMode?.isTrainingDay)
    assertEquals(1, parsed.data?.trainMode?.catchUpSuggestion?.missedTrainingDays)
  }

  @Test
  fun exploreFixture_roundTripsWithoutLoss() {
    val raw = readFixture("explore-response.json")
    val parsed = MovitJson.decodeFromString(ExploreApiResponse.serializer(), raw)

    assertTrue(parsed.success)
    assertNotNull(parsed.data)
    assertEquals(1, parsed.data?.exercises?.size)
    assertEquals("bodyweight-squat", parsed.data?.exercises?.firstOrNull()?.slug)
    assertEquals(1, parsed.data?.workoutTemplates?.size)

    val reencoded = MovitJson.encodeToString(ExploreApiResponse.serializer(), parsed)
    assertEquals(parsed, MovitJson.decodeFromString(ExploreApiResponse.serializer(), reencoded))
  }

  @Test
  fun syncFixture_parsesUserPrograms() {
    val raw = readFixture("sync-response.json")
    val parsed = MovitJson.decodeFromString(MobileSyncApiResponse.serializer(), raw)

    assertTrue(parsed.success)
    assertEquals(1, parsed.data?.userPrograms?.size)
    assertEquals("up-001", parsed.data?.userPrograms?.first()?.id)
    assertEquals("prog-001", parsed.data?.userPrograms?.first()?.programId)
    assertEquals("Foundation", parsed.data?.userPrograms?.first()?.name?.en)
    assertEquals("2026-01-01", parsed.data?.userPrograms?.first()?.startDate)
    assertEquals("2026-06-10T00:00:00Z", parsed.data?.userPrograms?.first()?.updatedAt)
    assertEquals(listOf(1, 3, 5), parsed.data?.userPrograms?.first()?.trainingWeekdays)
    assertTrue(parsed.data?.userPrograms?.first()?.isActive == true)
  }

  @Test
  fun userProgramsEndpointResponse_parsesEnrollmentList() {
    val raw = """
      {
        "success": true,
        "userPrograms": [
          {
            "id": "up-001",
            "programId": "prog-001",
            "name": { "en": "Foundation", "ar": "أساس" },
            "startDate": "2026-01-01",
            "isActive": true,
            "updatedAt": "2026-06-10T00:00:00Z",
            "trainingWeekdays": [1, 3, 5]
          }
        ]
      }
    """.trimIndent()
    val parsed = MovitJson.decodeFromString(UserProgramsApiResponse.serializer(), raw)

    assertTrue(parsed.success)
    assertEquals(1, parsed.userPrograms.size)
    assertEquals("up-001", parsed.userPrograms.first().id)
    assertEquals("prog-001", parsed.userPrograms.first().programId)
  }

  @Test
  fun trainingConfigFixture_parsesJsonPayload() {
    val parsed = MovitJson.decodeFromString(
      TrainingConfigApiResponse.serializer(),
      readFixture("training-config-response.json"),
    )
    assertTrue(parsed.success)
    assertNotNull(parsed.data)
  }

  @Test
  fun audioManifestFixture_roundTrips() {
    val raw = readFixture("audio-manifest-response.json")
    val parsed = MovitJson.decodeFromString(EntityAudioManifestApiResponse.serializer(), raw)
    assertTrue(parsed.success)
    assertEquals("quick-legs", parsed.data?.slug)
    assertEquals(1, parsed.data?.audioManifest?.files?.size)
    val reencoded = MovitJson.encodeToString(EntityAudioManifestApiResponse.serializer(), parsed)
    assertEquals(parsed, MovitJson.decodeFromString(EntityAudioManifestApiResponse.serializer(), reencoded))
  }

  @Test
  fun plannedWorkoutStartFixture_parsesReportFields() {
    val parsed = MovitJson.decodeFromString(
      PlannedWorkoutApiResponse.serializer(),
      readFixture("planned-workout-start-response.json"),
    )
    assertTrue(parsed.success)
    assertEquals("pw-001", parsed.data?.plannedWorkoutId)
    assertEquals("in_progress", parsed.data?.status)
  }

  @Test
  fun exploreWorkoutUploadFixture_roundTripsWithoutLoss() {
    val raw = readFixture("explore-workout-upload-request.json")
    val parsed = MovitJson.decodeFromString(ExploreWorkoutUploadRequestDto.serializer(), raw)

    assertEquals("grp-001", parsed.workoutGroupId)
    assertEquals("explore_workout", parsed.context)
    assertEquals(1, parsed.executions.size)
    assertEquals("bodyweight-squat", parsed.executions.first().exerciseId)
    assertNotNull(parsed.executions.first().executionMetrics)

    val reencoded = MovitJson.encodeToString(ExploreWorkoutUploadRequestDto.serializer(), parsed)
    assertEquals(parsed, MovitJson.decodeFromString(ExploreWorkoutUploadRequestDto.serializer(), reencoded))
  }

  @Test
  fun exploreWorkoutUploadResponseFixture_parsesSavedExecutions() {
    val parsed = MovitJson.decodeFromString(
      ExploreWorkoutApiResponse.serializer(),
      readFixture("explore-workout-upload-response.json"),
    )
    assertTrue(parsed.success)
    assertEquals(1, parsed.data?.savedCount)
    assertEquals(10, parsed.data?.executions?.firstOrNull()?.totalReps)
  }

  @Test
  fun workoutExecutionUpload_matchesExploreExecutionShape() {
    val explore = WorkoutExecutionContractFixtures.sampleExploreUploadRequest()
    val execution = explore.executions.first()
    val standalone = WorkoutExecutionContractFixtures.sampleExecutionUpload()

    val exploreJson = MovitJson.encodeToString(WorkoutExecutionUploadRequestDto.serializer(), execution)
    val standaloneJson = MovitJson.encodeToString(WorkoutExecutionUploadRequestDto.serializer(), standalone)
    assertEquals(
      MovitJson.parseToJsonElement(exploreJson),
      MovitJson.parseToJsonElement(standaloneJson),
    )
  }

  @Test
  fun syncMessageLibraryFixture_parsesAudioUrlsInContent() {
    val parsed = MovitJson.decodeFromString(MobileSyncApiResponse.serializer(), readFixture("sync-message-library-audio.json"))
    val message = parsed.data?.messageLibrary?.single()
    assertNotNull(message)
    assertEquals("msg-audio-001", message.id)
    assertEquals("Great depth", message.content.en)
    assertEquals("عمق ممتاز", message.content.ar)
    assertEquals("https://cdn.example/tts_en.wav", message.content.audioEn)
    assertEquals("https://cdn.example/tts_ar.wav", message.content.audioAr)

    val reencoded = MovitJson.encodeToString(SyncMessageTemplateDto.serializer(), message)
    val roundTrip = MovitJson.decodeFromString(SyncMessageTemplateDto.serializer(), reencoded)
    assertEquals(message, roundTrip)
    assertEquals(message.content, roundTrip.content)
  }

  @Test
  fun syncDeltaEmptyAuthenticatedFixture_isLeanPayload() {
    val raw = readFixture("sync-delta-empty-authenticated.json")
    val parsed = MovitJson.decodeFromString(MobileSyncApiResponse.serializer(), raw)

    assertTrue(parsed.success)
    assertEquals(false, parsed.meta?.isFullSync)
    assertEquals(0, parsed.data?.exercises?.size)
    assertEquals(0, parsed.data?.systemMessages?.size)
    assertEquals(0, parsed.data?.plannedWorkoutReports?.size)
    assertEquals(0, parsed.data?.audioManifest?.files?.size)
    assertEquals(0, parsed.data?.userPrograms?.size)
    // Golden budget: empty authenticated delta body stays under 2 KB uncompressed.
    assertTrue(raw.length < 2_000, "empty delta fixture grew to ${raw.length} bytes")
  }

  @Test
  fun syncFixture_extraBackendFields_ignoredByLenientParser() {
    val root = MovitJson.parseToJsonElement(readFixture("sync-response.json")).jsonObject
    val data = root["data"]?.jsonObject ?: error("data missing")
    assertTrue(data.containsKey("audioManifestVersion"), "Backend sends fields beyond current DTO")
    MovitJson.decodeFromString(MobileSyncApiResponse.serializer(), readFixture("sync-response.json"))
  }


  @Test
  fun verifyAppStoreRequestFixture_roundTripsWithoutLoss() {
    val raw = readFixture("verify-app-store-request.json")
    val parsed = MovitJson.decodeFromString(VerifyAppStoreRequest.serializer(), raw)

    assertEquals("550e8400-e29b-41d4-a716-446655440000", parsed.planId)
    assertEquals("monthly", parsed.billingPeriod)
    assertEquals("movit.pro.monthly", parsed.productId)
    assertEquals("2000000123456789", parsed.transactionId)
    assertEquals("2000000123456789", parsed.originalTransactionId)
    assertTrue(parsed.signedTransactionInfo.isNotBlank())

    val reencoded = MovitJson.encodeToString(VerifyAppStoreRequest.serializer(), parsed)
    assertEquals(parsed, MovitJson.decodeFromString(VerifyAppStoreRequest.serializer(), reencoded))
  }

  @Test
  fun verifyAppStoreResponseFixture_parsesSubscriptionEnvelope() {
    val raw = readFixture("verify-app-store-response.json")
    val parsed = MovitJson.decodeFromString(
      SubscriptionApiEnvelope.serializer(VerifyAppStoreResponse.serializer()),
      raw,
    )

    assertTrue(parsed.success)
    assertNotNull(parsed.data)
    assertEquals("sub-001", parsed.data?.subscription?.id)
    assertEquals(true, parsed.data?.status?.isPro)
    assertEquals("2026-07-17T00:00:00.000Z", parsed.data?.status?.subscriptionExpiry)
  }
  private fun readFixture(name: String): String {
    javaClass.classLoader?.getResource("fixtures/$name")?.readText()?.let { return it }
    val candidates = listOf(
      "src/commonTest/resources/fixtures/$name",
      "core/network/src/commonTest/resources/fixtures/$name",
    )
    for (relative in candidates) {
      val file = java.io.File(relative)
      if (file.isFile) return file.readText()
    }
    error("Missing fixture: fixtures/$name")
  }
}
