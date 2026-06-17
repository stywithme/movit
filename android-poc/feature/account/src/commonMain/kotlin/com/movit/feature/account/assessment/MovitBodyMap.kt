package com.movit.feature.account.assessment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.movit.designsystem.movitColors
import com.movit.feature.account.AssessmentRegionTone
import com.movit.feature.account.AssessmentRegionUi
import com.movit.resources.movitText

@Composable
fun MovitBodyMap(
    regions: List<AssessmentRegionUi>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val a11y = contentDescription ?: movitText("assessment_body_map_a11y")
    val movit = MaterialTheme.movitColors
    val outline = movit.textTertiary.copy(alpha = 0.55f)
    val toneColors = mapOf(
        AssessmentRegionTone.Good to movit.success,
        AssessmentRegionTone.Warning to movit.warning,
        AssessmentRegionTone.Neutral to movit.limeDeep,
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .semantics { this.contentDescription = a11y },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = minOf(size.width, size.height) / 300f
        drawBodyOutline(cx, cy, scale, outline)
        regions
            .groupBy { it.regionKey }
            .forEach { (regionKey, regionList) ->
                val best = regionList.maxByOrNull { it.score } ?: return@forEach
                drawRegionHighlight(regionKey, best, cx, cy, scale, toneColors)
            }
    }
}

private fun DrawScope.drawBodyOutline(cx: Float, cy: Float, scale: Float, color: Color) {
    val stroke = Stroke(width = 2.dp.toPx())
    drawCircle(color, 18f * scale, center = Offset(cx, cy - 100f * scale), style = stroke)
    val path = Path().apply {
        moveTo(cx, cy - 82f * scale)
        lineTo(cx, cy - 70f * scale)
        moveTo(cx - 50f * scale, cy - 65f * scale)
        lineTo(cx + 50f * scale, cy - 65f * scale)
        moveTo(cx - 35f * scale, cy - 65f * scale)
        lineTo(cx - 30f * scale, cy + 10f * scale)
        moveTo(cx + 35f * scale, cy - 65f * scale)
        lineTo(cx + 30f * scale, cy + 10f * scale)
        moveTo(cx - 30f * scale, cy + 10f * scale)
        lineTo(cx + 30f * scale, cy + 10f * scale)
        moveTo(cx - 20f * scale, cy + 10f * scale)
        lineTo(cx - 25f * scale, cy + 70f * scale)
        lineTo(cx - 22f * scale, cy + 110f * scale)
        moveTo(cx + 20f * scale, cy + 10f * scale)
        lineTo(cx + 25f * scale, cy + 70f * scale)
        lineTo(cx + 22f * scale, cy + 110f * scale)
        moveTo(cx - 50f * scale, cy - 65f * scale)
        lineTo(cx - 65f * scale, cy - 20f * scale)
        lineTo(cx - 60f * scale, cy + 15f * scale)
        moveTo(cx + 50f * scale, cy - 65f * scale)
        lineTo(cx + 65f * scale, cy - 20f * scale)
        lineTo(cx + 60f * scale, cy + 15f * scale)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawRegionHighlight(
    regionKey: String,
    region: AssessmentRegionUi,
    cx: Float,
    cy: Float,
    scale: Float,
    toneColors: Map<AssessmentRegionTone, Color>,
) {
    val (rx, ry, radius) = regionPosition(regionKey, cx, cy, scale)
    val fill = (toneColors[region.tone] ?: toneColors.getValue(AssessmentRegionTone.Neutral)).copy(
        alpha = if (region.confidence.equals("low", ignoreCase = true)) 0.35f else 0.65f,
    )
    drawCircle(fill, radius, center = Offset(rx, ry))
}

private fun regionPosition(regionKey: String, cx: Float, cy: Float, scale: Float): Triple<Float, Float, Float> =
    when (regionKey.lowercase()) {
        "shoulders", "shoulder" -> Triple(cx, cy - 65f * scale, 15f * scale)
        "spine", "core" -> Triple(cx, cy - 25f * scale, 16f * scale)
        "hips", "hip" -> Triple(cx, cy + 10f * scale, 14f * scale)
        "knees", "knee" -> Triple(cx, cy + 55f * scale, 12f * scale)
        "balance" -> Triple(cx, cy + 120f * scale, 12f * scale)
        else -> Triple(cx, cy, 12f * scale)
    }

