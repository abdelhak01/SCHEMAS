package com.schemadiag

import com.schemadiag.analysis.DiagnosticEngine
import com.schemadiag.analysis.ReferenceLinker
import com.schemadiag.data.LinkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceLinkerTest {

    private val texts = listOf(
        "Alimentation générale -Q1 -F1 X1",              // page 1
        "Commande KM1 bobine -S1 arrêt d'urgence /3.2",  // page 2
        "Puissance contacts KM1 moteur M1 -F2",          // page 3
        "Schéma voir page 1 pour l'alimentation"          // page 4
    )

    private fun graph() = ReferenceLinker.link(
        texts.mapIndexed { i, t -> ReferenceLinker.buildPageInfo(i, t, texts.size, false) }
    )

    @Test fun detectsTags() {
        val tags = ReferenceLinker.extractTags(texts[1])
        assertTrue("KM1" in tags)
        assertTrue("S1" in tags)
    }

    @Test fun detectsSlashReference() {
        val refs = ReferenceLinker.extractRefs(texts[1], texts.size, 1)
        assertEquals(listOf(2), refs.map { it.targetPage }) // page 3 -> index 2
    }

    @Test fun detectsWordReference() {
        val refs = ReferenceLinker.extractRefs(texts[3], texts.size, 3)
        assertEquals(listOf(0), refs.map { it.targetPage })
    }

    @Test fun linksPagesSharingATag() {
        val g = graph()
        assertTrue(g.links.any { it.from == 1 && it.to == 2 && it.type == LinkType.SHARED_TAG && it.label == "KM1" })
        assertTrue(g.links.any { it.from == 2 && it.to == 1 && it.type == LinkType.SHARED_TAG })
    }

    @Test fun diagnosticFindsSuspectPages() {
        val res = DiagnosticEngine.diagnose(graph(), "le moteur ne démarre pas", "KM1")
        assertTrue(res.steps.first().pages.containsAll(listOf(1, 2)))
        assertTrue(res.steps.size > 2)
    }
}
