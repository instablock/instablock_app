package dev.instablock.input

import android.content.Context
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display

/**
 * The screen's size, density and system bar heights, plus positions derived from them.
 *
 * Elements are normally found in the view tree. The derived positions are only a last resort
 * for when an element can't be found there. The dp offsets were measured on Instagram 446
 * (1080x2424 screen, density 2.625) and may drift with app updates.
 */
data class Geometry(
    val width: Int,
    val height: Int,
    val density: Float,
    val topInset: Int,
    val bottomInset: Int,
) {
    fun dp(value: Number): Float = value.toFloat() * density

    /** Vertical centre of Instagram's top bar, which is about 56dp tall. */
    val actionBarCenterY: Float get() = topInset + dp(28)

    /** Where the ⋯ button sits at the top right of someone else's profile. */
    val profileOverflow: PointF get() = PointF(width - dp(26), actionBarCenterY)

    /** True if [y] is in the band holding Instagram's top bar. */
    fun inTopBar(y: Int): Boolean = y < topInset + dp(64)

    /** True if [y] is in the band holding Instagram's bottom tab bar. */
    fun inBottomBar(y: Int): Boolean = y > height - bottomInset - dp(64)

    companion object {
        fun measure(ctx: Context): Geometry {
            val display = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            return Geometry(
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                density = metrics.density,
                topInset = systemDimen(ctx, "status_bar_height"),
                // With gesture navigation, this is the height of the gesture handle area.
                bottomInset = systemDimen(ctx, "navigation_bar_height"),
            )
        }

        @Suppress("DiscouragedApi", "InternalInsetResource")
        private fun systemDimen(ctx: Context, name: String): Int {
            val id = ctx.resources.getIdentifier(name, "dimen", "android")
            return if (id != 0) ctx.resources.getDimensionPixelSize(id) else 0
        }
    }
}
