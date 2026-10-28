package dev.instablock.debug

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import dev.instablock.BlockerService
import dev.instablock.i18n.tx
import dev.instablock.input.Geometry
import dev.instablock.instagram.INSTAGRAM_PACKAGE
import dev.instablock.instagram.bounds
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves what's on screen, for troubleshooting: the view tree as text, plus a screenshot on
 * Android 11 and later. Files go to Android/data/dev.instablock/files/dumps/.
 *
 * The app saves one automatically when something unexpected happens during a run.
 */
object ScreenDump {
    fun save(svc: BlockerService) {
        val dir = File(svc.getExternalFilesDir(null), "dumps").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

        val sb = StringBuilder()
        sb.append("# ").append(Geometry.measure(svc)).append('\n')
        sb.append("# system locale=").append(Locale.getDefault()).append(" sdk=").append(Build.VERSION.SDK_INT).append('\n')
        sb.append("# instagram=").append(instagramVersion(svc)).append('\n')
        for (w in svc.windows) {
            sb.append("## window type=").append(w.type).append(" layer=").append(w.layer)
                .append(" active=").append(w.isActive).append(" title=").append(w.title).append('\n')
            w.root?.let { writeNode(it, 0, sb) }
        }
        val tree = File(dir, "tree-$stamp.txt")
        tree.writeText(sb.toString())
        svc.log(tx("Enregistré : ${tree.name}", "Saved ${tree.name}"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) screenshot(svc, File(dir, "screen-$stamp.png"))
    }

    /** One line per node, indented by depth: class, id, text, description, bounds, flags. */
    private fun writeNode(n: AccessibilityNodeInfo, depth: Int, sb: StringBuilder) {
        repeat(depth) { sb.append("  ") }
        sb.append(n.className?.toString()?.substringAfterLast('.'))
        n.viewIdResourceName?.let { sb.append(" id=").append(it.substringAfter(":id/")) }
        n.text?.let { sb.append(" text=\"").append(it).append('"') }
        n.contentDescription?.let { sb.append(" desc=\"").append(it).append('"') }
        sb.append(' ').append(n.bounds().toShortString())
        if (n.isClickable) sb.append(" clickable")
        if (n.isEditable) sb.append(" editable")
        if (n.isFocused) sb.append(" focused")
        if (!n.isVisibleToUser) sb.append(" hidden")
        sb.append('\n')
        for (i in 0 until n.childCount) n.getChild(i)?.let { writeNode(it, depth + 1, sb) }
    }

    private fun instagramVersion(svc: BlockerService): String =
        runCatching { svc.packageManager.getPackageInfo(INSTAGRAM_PACKAGE, 0).versionName }.getOrNull() ?: "not installed"

    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    private fun screenshot(svc: BlockerService, file: File) {
        svc.takeScreenshot(Display.DEFAULT_DISPLAY, svc.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                result.hardwareBuffer.close()
                if (bitmap == null) return
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                svc.log(tx("Enregistré : ${file.name}", "Saved ${file.name}"))
            }

            override fun onFailure(errorCode: Int) {
                svc.log(tx("Échec de la capture d'écran (code $errorCode)", "Screenshot failed (code $errorCode)"))
            }
        })
    }
}
