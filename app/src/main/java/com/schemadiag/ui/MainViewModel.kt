package com.schemadiag.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.schemadiag.analysis.DiagnosticEngine
import com.schemadiag.analysis.ReferenceLinker
import com.schemadiag.cloud.ClaudeClient
import com.schemadiag.data.DiagnosticResult
import com.schemadiag.data.DocumentGraph
import com.schemadiag.pdf.OcrExtractor
import com.schemadiag.pdf.PageTextExtractor
import com.schemadiag.pdf.PdfDocumentLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loading: Boolean = false,
    val progress: String = "",
    val graph: DocumentGraph? = null,
    val currentPage: Int = 0,
    val pageBitmap: Bitmap? = null,
    val diagnostic: DiagnosticResult? = null,
    val diagnosing: Boolean = false,
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loader: PdfDocumentLoader? = null
    private val ocr = OcrExtractor()

    /** Importe le PDF, extrait le texte (OCR si besoin) puis construit le graphe. */
    fun openPdf(uri: Uri) = viewModelScope.launch {
        _state.value = UiState(loading = true, progress = "Ouverture du PDF…")
        try {
            val result = withContext(Dispatchers.Default) {
                loader?.close()
                val l = PdfDocumentLoader(getApplication(), uri)
                loader = l
                val texts = PageTextExtractor(getApplication()).extract(l.file)
                val pages = texts.mapIndexed { i, raw ->
                    _state.value = _state.value.copy(progress = "Analyse page ${i + 1}/${texts.size}…")
                    var text = raw
                    var usedOcr = false
                    if (raw.trim().length < PageTextExtractor.MIN_CHARS_FOR_TEXT_PAGE) {
                        text = ocr.recognize(l.renderPage(i))
                        usedOcr = true
                    }
                    ReferenceLinker.buildPageInfo(i, text, texts.size, usedOcr)
                }
                ReferenceLinker.link(pages)
            }
            _state.value = _state.value.copy(loading = false, graph = result)
            goToPage(0)
        } catch (e: Exception) {
            _state.value = UiState(error = "Impossible d'analyser le PDF : ${e.message}")
        }
    }

    fun goToPage(index: Int) = viewModelScope.launch {
        val l = loader ?: return@launch
        if (index !in 0 until l.pageCount) return@launch
        val bmp = withContext(Dispatchers.Default) { l.renderPage(index) }
        _state.value = _state.value.copy(currentPage = index, pageBitmap = bmp)
    }

    fun diagnoseOffline(symptom: String, suspect: String?) {
        val g = _state.value.graph ?: return
        _state.value = _state.value.copy(diagnostic = DiagnosticEngine.diagnose(g, symptom, suspect), error = null)
    }

    /** Diagnostic cloud : on n'envoie que les pages pertinentes (max 5). */
    fun diagnoseCloud(symptom: String, suspect: String?, apiKey: String) = viewModelScope.launch {
        val g = _state.value.graph ?: return@launch
        val l = loader ?: return@launch
        if (apiKey.isBlank()) {
            _state.value = _state.value.copy(error = "Saisissez une clé API pour le diagnostic cloud.")
            return@launch
        }
        _state.value = _state.value.copy(diagnosing = true, error = null)
        try {
            val tag = suspect?.trim()?.uppercase()?.replace("-", "")?.replace(" ", "")
            val relevant = buildList {
                add(_state.value.currentPage)
                if (!tag.isNullOrEmpty()) {
                    val tagPages = g.pagesWithTag(tag)
                    addAll(tagPages)
                    addAll(g.links.filter { it.from in tagPages }.map { it.to })
                }
            }.distinct().take(5)
            val payload = withContext(Dispatchers.Default) {
                relevant.map { Triple(it + 1, l.renderPage(it, 1600), g.pages[it].text) }
            }
            val res = ClaudeClient(apiKey).diagnose(symptom, suspect, payload)
            _state.value = _state.value.copy(diagnosing = false, diagnostic = res)
        } catch (e: Exception) {
            _state.value = _state.value.copy(diagnosing = false, error = "Diagnostic cloud : ${e.message}")
        }
    }

    override fun onCleared() {
        loader?.close()
    }
}
