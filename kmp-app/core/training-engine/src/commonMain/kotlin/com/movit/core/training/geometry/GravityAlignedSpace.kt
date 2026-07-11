package com.movit.core.training.geometry

import com.movit.core.training.model.Landmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Rotates world landmarks so +Y aligns with opposite gravity (WP-20 / INNOV-5).
 * Gravity vector is device/sensor space (gx, gy, gz).
 */
object GravityAlignedSpace {
    data class Vec3(val x: Float, val y: Float, val z: Float) {
        fun length(): Float = sqrt(x * x + y * y + z * z)
        fun normalized(): Vec3? {
            val len = length()
            if (len < 1e-6f) return null
            return Vec3(x / len, y / len, z / len)
        }
        operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
        operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
        fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
        fun cross(o: Vec3) = Vec3(
            y * o.z - z * o.y,
            z * o.x - x * o.z,
            x * o.y - y * o.x,
        )
    }

    fun alignToGravity(world: List<Landmark>, gravity: FloatArray): List<Landmark>? {
        if (world.size < 33 || gravity.size < 3) return null
        val g = Vec3(gravity[0], gravity[1], gravity[2]).normalized() ?: return null
        // Target world +Y = up = -gravity
        val targetY = Vec3(-g.x, -g.y, -g.z)
        val currentY = Vec3(0f, 1f, 0f)
        val rot = rotationBetween(currentY, targetY) ?: return world
        return world.map { lm ->
            val p = rot(Vec3(lm.x, lm.y, lm.z))
            lm.copy(x = p.x, y = p.y, z = p.z)
        }
    }

    /** Angle in degrees between torso up vector and gravity-aligned +Y (0 = upright). */
    fun torsoTiltDegrees(alignedWorld: List<Landmark>): Double? {
        if (alignedWorld.size < 25) return null
        val ls = alignedWorld[11]; val rs = alignedWorld[12]
        val lh = alignedWorld[23]; val rh = alignedWorld[24]
        val shoulderMid = Vec3((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f, (ls.z + rs.z) / 2f)
        val hipMid = Vec3((lh.x + rh.x) / 2f, (lh.y + rh.y) / 2f, (lh.z + rh.z) / 2f)
        val torso = (shoulderMid - hipMid).normalized() ?: return null
        val up = Vec3(0f, 1f, 0f)
        val cos = torso.dot(up).coerceIn(-1f, 1f)
        return acos(cos.toDouble()) * (180.0 / kotlin.math.PI)
    }

    fun distanceMeters(a: Landmark, b: Landmark): Float {
        val dx = a.x - b.x; val dy = a.y - b.y; val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Rodrigues rotation taking [from] → [to] (both unit). */
    private fun rotationBetween(from: Vec3, to: Vec3): ((Vec3) -> Vec3)? {
        val cos = from.dot(to).coerceIn(-1f, 1f)
        if (cos > 0.9999f) return { it }
        if (cos < -0.9999f) {
            // 180° — pick arbitrary perpendicular axis
            val axis = if (abs(from.x) < 0.9f) {
                from.cross(Vec3(1f, 0f, 0f)).normalized()
            } else {
                from.cross(Vec3(0f, 1f, 0f)).normalized()
            } ?: return null
            return { p -> rotateRodrigues(p, axis, kotlin.math.PI.toFloat()) }
        }
        val axis = from.cross(to).normalized() ?: return null
        val angle = acos(cos.toDouble()).toFloat()
        return { p -> rotateRodrigues(p, axis, angle) }
    }

    private fun rotateRodrigues(v: Vec3, axis: Vec3, angle: Float): Vec3 {
        val c = kotlin.math.cos(angle)
        val s = kotlin.math.sin(angle)
        val dot = axis.dot(v)
        val cross = axis.cross(v)
        return v * c + cross * s + axis * (dot * (1f - c))
    }
}
