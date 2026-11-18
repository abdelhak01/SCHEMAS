package com.schemadiag.analysis

import com.schemadiag.data.DocumentGraph
import com.schemadiag.data.LinkType
import com.schemadiag.data.PageInfo
import com.schemadiag.data.PageLink
import com.schemadiag.data.PageRef

/**
 * Détecte les repères d'appareils et les renvois, puis construit le graphe de liaisons.
 * Code Kotlin pur (sans dépendance Android) : facile à tester.
 *
 * Les expressions régulières suivent les conventions courantes (KM1, -Q2, F3, X1...).
 * À adapter à vos normes de repérage (IEC 81346, DIN, normes maison).
 */
object ReferenceLinker {

    /** Repère d'appareil : préfixe de lettres usuel + numéro, ex. KM1, -Q2, F12A. */
    private val TAG_REGEX = Regex(
        """(?<![A-Za-z0-9])-?(KM|KA|KT|QM|QF|FU|SB|SQ|SA|XT|HL|YV|TC|[KQFSMTXHYRAUGBP])\s?-?(\d{1,3}[A-Z]?)(?![A-Za-z0-9])"""
    )

    /** Renvoi de type "/12.4" (page 12, colonne 4) ou "/12". */
    private val SLASH_REF = Regex("""(?<![\d/])/(\d{1,3})(?:\.(\d{1,2}))?(?![\d])""")

    /** Renvoi en toutes lettres : "voir page 5", "p. 5", "see sheet 5". */
    private val WORD_REF = Regex(
        """(?i)\b(?:voir|see|cf\.?|page|p\.|pg\.?|feuille|sheet)\s*(?:page|p\.|sheet|feuille)?\s*(\d{1,3})\b"""
    )

    fun extractTags(text: String): Set<String> =
        TAG_REGEX.findAll(text)
            .map { it.groupValues[1] + it.groupValues[2] }
            .toSet()

    /**
     * Renvois vers des pages. Les numéros de page affichés sur les schémas commencent
     * en général à 1 : on les convertit en index à partir de 0 et on ignore ceux
     * qui dépassent le nombre de pages.
     */
    fun extractRefs(text: String, pageCount: Int, selfIndex: Int): List<PageRef> {
        val refs = mutableListOf<PageRef>()
        SLASH_REF.findAll(text).forEach { m ->
            refs += PageRef(m.groupValues[1].toInt() - 1, m.value)
        }
        WORD_REF.findAll(text).forEach { m ->
            refs += PageRef(m.groupValues[1].toInt() - 1, m.value.trim())
        }
        return refs
            .filter { it.targetPage in 0 until pageCount && it.targetPage != selfIndex }
            .distinctBy { it.targetPage to it.raw }
    }

    fun buildPageInfo(index: Int, text: String, pageCount: Int, usedOcr: Boolean) = PageInfo(
        index = index,
        text = text,
        tags = extractTags(text),
        pageRefs = extractRefs(text, pageCount, index),
        usedOcr = usedOcr
    )

    /**
     * Graphe :
     *  - renvoi explicite : page A -> page B ;
     *  - repère partagé : A <-> B quand le même appareil figure sur les deux
     *    (ex. bobine KM1 page 4, contacts KM1 page 7).
     *
     * Un repère présent sur plus de [maxPagesPerTag] pages est ignoré : c'est
     * probablement une alimentation commune ou du bruit, qui relierait tout à tout.
     */
    fun link(pages: List<PageInfo>, maxPagesPerTag: Int = 8): DocumentGraph {
        val links = mutableListOf<PageLink>()

        pages.forEach { p ->
            p.pageRefs.forEach { r ->
                links += PageLink(p.index, r.targetPage, LinkType.EXPLICIT_REF, r.raw)
            }
        }

        val byTag = mutableMapOf<String, MutableList<Int>>()
        pages.forEach { p -> p.tags.forEach { t -> byTag.getOrPut(t) { mutableListOf() }.add(p.index) } }
        byTag.forEach { (tag, idx) ->
            if (idx.size in 2..maxPagesPerTag) {
                for (a in idx) for (b in idx) if (a != b) {
                    links += PageLink(a, b, LinkType.SHARED_TAG, tag)
                }
            }
        }
        return DocumentGraph(pages, links.distinct())
    }
}
