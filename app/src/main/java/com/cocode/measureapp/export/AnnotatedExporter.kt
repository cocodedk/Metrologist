package com.cocode.measureapp.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.cocode.measureapp.R
import com.cocode.measureapp.core.MeasurementView
import com.cocode.measureapp.ui.text.resolve
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renders the measurement summary onto the photo and shares it as a PNG. */
object AnnotatedExporter {
    suspend fun shareAnnotated(context: Context, source: Bitmap, view: MeasurementView) {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            color = Color.YELLOW
            textSize = out.width * 0.04f
            isAntiAlias = true
        }
        val lines = listOf(
            context.getString(R.string.export_width, view.width),
            context.getString(R.string.export_height, view.height),
            context.getString(R.string.export_area, view.area),
            context.getString(R.string.results_confidence, view.confidenceLabel.resolve(context), view.confidencePercent),
        )
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, 24f, (i + 1) * paint.textSize * 1.2f, paint)
        }

        val fileName = "measurement_${System.currentTimeMillis()}.png"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_share_title)))
        }
    }
}
