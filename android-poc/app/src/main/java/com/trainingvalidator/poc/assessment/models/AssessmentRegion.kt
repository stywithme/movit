package com.trainingvalidator.poc.assessment.models

/**
 * AssessmentRegion - Detailed assessment data for a single body region.
 * 
 * Contains absolute ROM, reference norm comparison, movement quality grade,
 * stability score, and confidence level.
 */
data class AssessmentRegion(
    val region: BodyRegion,
    val side: RegionSide,
    val absoluteRom: Float,     // Measured ROM in degrees
    val errorBand: Float,       // ± measurement error in degrees
    val referenceNorm: Float,   // Reference norm in degrees
    val romPercentage: Float,   // ROM as % of reference (center value)
    val romPercentageMin: Float, // Lower bound considering error
    val romPercentageMax: Float, // Upper bound considering error
    val movementQualityGrade: Int, // 1=fail, 2=compensation, 3=clean
    val stabilityScore: Float,  // 0-100
    val regionalScore: Float,   // 0-100 (weighted composite)
    val confidence: ConfidenceLevel,
    val status: RegionStatus
) {
    fun getFormattedRom(): String = "%.0f° ± %.0f°".format(absoluteRom, errorBand)
    fun getFormattedScore(): String = "%.0f%%".format(regionalScore)
    fun getFormattedRomPercentage(): String = "%.0f%%".format(romPercentage)
    fun getRomRange(): String = "%.0f° – %.0f°".format(absoluteRom - errorBand, absoluteRom + errorBand)
}

enum class BodyRegion(val labelAr: String, val labelEn: String) {
    SHOULDER("الكتف", "Shoulder"),
    UPPER_BACK("أعلى الظهر", "Upper Back"),
    CORE("الجذع", "Core"),
    LOWER_BACK("أسفل الظهر", "Lower Back"),
    HIP("الورك", "Hip"),
    KNEE("الركبة", "Knee"),
    ANKLE("الكاحل", "Ankle"),
    BALANCE("التوازن", "Balance");

    fun getLabel(language: String = "en"): String =
        if (language == "ar") labelAr else labelEn
}

enum class RegionSide {
    LEFT,
    RIGHT,
    CENTER
}
