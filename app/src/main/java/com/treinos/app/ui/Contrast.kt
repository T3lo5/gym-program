package com.treinos.app.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** ARGB (0xAARRGGBB). */
fun relativeLuminance(argb: Long): Double {
    val r = linearChannel(((argb shr 16) and 0xFF).toInt())
    val g = linearChannel(((argb shr 8) and 0xFF).toInt())
    val b = linearChannel((argb and 0xFF).toInt())
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

fun contrastRatio(foregroundArgb: Long, backgroundArgb: Long): Double {
    val fg = blendAlphaIfNeeded(foregroundArgb, backgroundArgb)
    val l1 = relativeLuminance(fg)
    val l2 = relativeLuminance(backgroundArgb)
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

/** Mistura [foregroundArgb] sobre [backgroundArgb] quando o alfa for < 1 (opacas ficam inalteradas). */
private fun blendAlphaIfNeeded(fg: Long, bg: Long): Long {
    val a = ((fg shr 24) and 0xFF).toInt() / 255.0
    if (a >= 1.0) return fg
    val inv = 1.0 - a
    val r = ((((fg shr 16) and 0xFF).toInt() * a) + (((bg shr 16) and 0xFF).toInt() * inv)).toInt()
    val g = ((((fg shr 8) and 0xFF).toInt() * a) + (((bg shr 8) and 0xFF).toInt() * inv)).toInt()
    val b = (((fg and 0xFF).toInt() * a) + ((bg and 0xFF).toInt() * inv)).toInt()
    return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
}

private fun linearChannel(channel: Int): Double {
    val srgb = channel / 255.0
    return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
}
