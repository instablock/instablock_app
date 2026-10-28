package dev.instablock.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.random.Random

/** A random duration in [min, max), in milliseconds. */
fun randomBetween(min: Long, max: Long): Long =
    if (max <= min) min else min + (Random.nextDouble() * (max - min)).toLong()

/**
 * Taps on the screen with the small variations of a real finger: each tap lands near the
 * centre of its target rather than exactly on it, lasts a variable time, drifts by a pixel
 * or two, and comes after an uneven pause.
 */
class Touch(private val svc: AccessibilityService) {
    private val rng = java.util.Random()

    /**
     * Waits a random time between [minMs] and [maxMs], more often near the middle. About one
     * time in 30, it waits up to 2.5 s longer, like a person briefly distracted.
     */
    suspend fun idle(minMs: Long, maxMs: Long) {
        val t = (rng.nextDouble() + rng.nextDouble()) / 2
        var ms = minMs + (t * (maxMs - minMs)).toLong()
        if (rng.nextInt(100) < 3) ms += randomBetween(800, 2_500)
        delay(ms)
    }

    /** The pause between deciding to tap and the finger landing. */
    suspend fun react() = delay(randomBetween(40, 140))

    /** A point inside [area] where a finger would land: near the centre, never near the edges. */
    fun pointIn(area: Rect): PointF {
        if (area.width() < 6 || area.height() < 6) return PointF(area.exactCenterX(), area.exactCenterY())
        val x = (area.exactCenterX() + rng.nextGaussian() * area.width() * 0.15)
            .coerceIn(area.left + area.width() * 0.2, area.right - area.width() * 0.2)
        val y = (area.exactCenterY() + rng.nextGaussian() * area.height() * 0.15)
            .coerceIn(area.top + area.height() * 0.2, area.bottom - area.height() * 0.2)
        return PointF(x.toFloat(), y.toFloat())
    }

    /** Taps somewhere inside [area]. */
    suspend fun tap(area: Rect): Boolean {
        val p = pointIn(area)
        return tapAt(p.x, p.y)
    }

    /** Taps around a point, scattered by about [scatterPx]. Used with fallback coordinates. */
    suspend fun tapNear(x: Float, y: Float, scatterPx: Float): Boolean =
        tapAt(x + (rng.nextGaussian() * scatterPx).toFloat(), y + (rng.nextGaussian() * scatterPx).toFloat())

    /**
     * Taps at a point. Pass [react] = false when the caller has already paused with [react],
     * for example to check what's under the point just before tapping.
     * Returns false if Android rejected or cancelled the gesture.
     */
    suspend fun tapAt(x: Float, y: Float, react: Boolean = true): Boolean {
        if (react) react()
        val sx = x.coerceAtLeast(1f)
        val sy = y.coerceAtLeast(1f)
        val path = Path().apply {
            moveTo(sx, sy)
            // A pixel or so of movement, well under the distance that would make it a swipe.
            lineTo(sx + drift(), sy + drift())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, randomBetween(55, 140))
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    private fun drift(): Float = (rng.nextGaussian() * 1.2).toFloat()

    private suspend fun dispatch(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(g: GestureDescription) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val accepted = svc.dispatchGesture(gesture, callback, null)
            if (!accepted && cont.isActive) cont.resume(false)
        }
}
