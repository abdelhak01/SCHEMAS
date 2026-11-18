package com.schemadiag.data

/** Informations extraites d'une page du PDF (index à partir de 0). */
data class PageInfo(
    val index: Int,
    val text: String,
    val tags: Set<String>,          // repères d'appareils : KM1, Q1, F2...
    val pageRefs: List<PageRef>,    // renvois explicites vers d'autres pages
    val usedOcr: Boolean
)

/** Renvoi trouvé dans le texte, ex. "/12.4" ou "voir page 5". */
data class PageRef(val targetPage: Int, val raw: String)

enum class LinkType { EXPLICIT_REF, SHARED_TAG }

/** Liaison entre deux pages. */
data class PageLink(
    val from: Int,
    val to: Int,
    val type: LinkType,
    val label: String
)

data class DocumentGraph(
    val pages: List<PageInfo>,
    val links: List<PageLink>
) {
    fun linksFrom(page: Int): List<PageLink> =
        links.filter { it.from == page }.sortedBy { it.to }

    fun pagesWithTag(tag: String): List<Int> =
        pages.filter { tag in it.tags }.map { it.index }
}

/** Une étape de contrôle proposée à l'électricien. */
data class DiagnosticStep(
    val order: Int,
    val component: String,
    val pages: List<Int>,
    val check: String
)

data class DiagnosticResult(
    val summary: String,
    val steps: List<DiagnosticStep>,
    val source: String // "hors-ligne" ou "cloud"
)
