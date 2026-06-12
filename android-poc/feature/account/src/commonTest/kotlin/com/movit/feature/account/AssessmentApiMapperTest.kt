package com.movit.feature.account

import com.movit.core.network.dto.AssessmentRegionDto
import com.movit.core.network.dto.BodyScanResultDto
import com.movit.core.network.dto.DomainLevelDto
import com.movit.core.network.dto.LevelInfoDetailDto
import com.movit.core.network.dto.LevelProfileDetailDto
import com.movit.core.network.dto.LimitingFactorDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.RegionLevelDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssessmentApiMapperTest {

    @Test
    fun map_buildsLevelLabelAndDomains() {
        val dto = LevelProfileDetailDto(
            overallLevel = 2,
            bodyScore = 72.0,
            domainLevels = listOf(
                DomainLevelDto(domain = "mobility", score = 78.0),
                DomainLevelDto(domain = "control", score = 65.0),
            ),
            regionLevels = listOf(
                RegionLevelDto(region = "hips", score = 84.0, isLimiting = false),
                RegionLevelDto(region = "shoulders", score = 61.0, isLimiting = true),
            ),
            levelInfo = LevelInfoDetailDto(
                number = 2,
                name = LocalizedNameDto(en = "Building", ar = "بناء"),
            ),
        )

        val results = AssessmentApiMapper.map(dto, "en")
        assertEquals(72, results.bodyScore)
        assertEquals("Level 2 · Building", results.levelLabel)
        assertEquals(2, results.domains.size)
        assertEquals(2, results.regions.size)
        assertEquals(AssessmentRegionTone.Warning, results.regions[1].tone)
    }

    @Test
    fun map_limitingFactorsBecomeInsights() {
        val dto = LevelProfileDetailDto(
            bodyScore = 60.0,
            limitingFactors = listOf(
                LimitingFactorDto(code = "shoulder_flexion", gap = 2),
            ),
        )

        val results = AssessmentApiMapper.map(dto)
        assertEquals(1, results.insights.size)
        assertEquals("assessment_insight_limiting_title", results.insights.first().titleKey)
        assertEquals("shoulder_flexion", results.insights.first().titleArgs.first())
    }

    @Test
    fun mapBodyScanResult_addsSafetyGatesForLimitedRegions() {
        val dto = BodyScanResultDto(
            bodyScore = 55.0,
            mobilityScore = 60.0,
            controlScore = 58.0,
            safetyScore = 70.0,
            regions = listOf(
                AssessmentRegionDto(
                    region = "knees",
                    regionalScore = 38.0,
                    status = "weak",
                    confidence = "high",
                ),
            ),
        )

        val results = AssessmentApiMapper.map(dto)
        assertTrue(results.safetyGates.isNotEmpty())
        assertEquals("assessment_safety_gate_weak", results.safetyGates.first().reasonKey)
    }

    @Test
    fun map_limitingRegionsFallbackWhenNoFactors() {
        val dto = LevelProfileDetailDto(
            bodyScore = 55.0,
            regionLevels = listOf(
                RegionLevelDto(region = "knee", score = 52.0, isLimiting = true),
            ),
        )

        val results = AssessmentApiMapper.map(dto)
        assertTrue(results.insights.isNotEmpty())
        assertEquals("assessment_insight_region_limit_title", results.insights.first().titleKey)
    }
}
