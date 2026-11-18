package com.schemadiag.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Copie le PDF choisi dans le cache de l'appli puis fournit le rendu des pages.
 * PdfRenderer n'autorise qu'une page ouverte à la fois : on synchronise.
 */
class PdfDocumentLoader(private val context: Context, uri: Uri) : AutoCloseable {

    val file: File = File(context.cacheDir, "current.pdf").also { out ->
        context.contentResolver.openInputStream(uri)!!.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
    }

    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)

    val pageCount: Int get() = renderer.pageCount

    /** Rend la page en bitmap de largeur [targetWidth] (fond blanc). */
    @Synchronized
    fun renderPage(index: Int, targetWidth: Int = 2000): Bitmap {
        renderer.openPage(index).use { page ->
            val scale = targetWidth.toFloat() / page.width
            val bmp = Bitmap.createBitmap(
                targetWidth, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888
            )
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }

    override fun close() {
        renderer.close()
        pfd.close()
    }
}
