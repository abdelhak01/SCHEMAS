package com.schemadiag.pdf

import android.content.Context
import com.tom_roster.pdfbox.android.PDFBoxResourceLoader
import com.tom_roster.pdfbox.pdmodel.PDDocument
import com.tom_roster.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Extrait le texte vectoriel de chaque page (PDF générés par EPLAN, SEE Electrical...).
 * Si une page n'a (presque) pas de texte, elle est probablement scannée -> OCR.
 */
class PageTextExtractor(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /** Retourne le texte de chaque page, dans l'ordre. */
    fun extract(file: File): List<String> {
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            return (1..doc.numberOfPages).map { p ->
                stripper.startPage = p
                stripper.endPage = p
                stripper.getText(doc)
            }
        }
    }

    companion object {
        /** En dessous de ce nombre de caractères, on considère la page comme scannée. */
        const val MIN_CHARS_FOR_TEXT_PAGE = 30
    }
}
