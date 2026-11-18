package com.schemadiag.analysis

import com.schemadiag.data.DiagnosticResult
import com.schemadiag.data.DiagnosticStep
import com.schemadiag.data.DocumentGraph

/**
 * Moteur de règles hors-ligne : à partir d'un symptôme (texte libre) et éventuellement
 * d'un appareil suspect, propose une liste ordonnée de contrôles, avec les pages
 * du schéma où chaque appareil apparaît.
 *
 * Ce n'est pas une compréhension du schéma : ce sont des contrôles probables.
 * Enrichissez [RULES] avec vos machines.
 */
object DiagnosticEngine {

    private data class Rule(
        val keywords: List<String>,
        val label: String,
        /** (préfixe de repère, contrôle à effectuer), dans l'ordre logique. */
        val checks: List<Pair<String, String>>
    )

    private val RULES = listOf(
        Rule(
            keywords = listOf("ne démarre pas", "ne demarre pas", "démarre pas", "pas de démarrage", "won't start"),
            label = "Moteur / machine qui ne démarre pas",
            checks = listOf(
                "Q" to "Disjoncteur / sectionneur : enclenché ? tension présente en entrée et en sortie ?",
                "F" to "Fusibles / protections : état, déclenchement, tension aux bornes.",
                "S" to "Arrêts d'urgence et boutons d'arrêt : circuit de sécurité fermé ?",
                "KM" to "Contacteur : bobine alimentée ? contacts de puissance qui se ferment ?",
                "K" to "Relais de commande : alimentation de bobine, contacts d'auto-maintien.",
                "M" to "Moteur : tension aux bornes, résistance des enroulements, isolement."
            )
        ),
        Rule(
            keywords = listOf("disjoncte", "déclenche", "declenche", "saute", "court-circuit", "tripping"),
            label = "Déclenchement de protection",
            checks = listOf(
                "Q" to "Disjoncteur qui déclenche : courbe, calibre, cause (surcharge ou court-circuit) ?",
                "F" to "Protection thermique : réglage conforme à l'intensité nominale du moteur ?",
                "M" to "Moteur : isolement à la masse, équilibre des phases, courant absorbé.",
                "X" to "Borniers et connexions : serrage, traces d'échauffement."
            )
        ),
        Rule(
            keywords = listOf("ne s'arrête pas", "ne s'arrete pas", "reste enclenché", "reste enclenche", "colle"),
            label = "Machine qui ne s'arrête pas",
            checks = listOf(
                "KM" to "Contacteur : contacts collés ? bobine toujours alimentée ?",
                "K" to "Relais : contact soudé, auto-maintien non coupé.",
                "S" to "Bouton d'arrêt : contact NF réellement ouvert à l'appui ?"
            )
        ),
        Rule(
            keywords = listOf("surchauffe", "chauffe", "échauffement", "echauffement", "overheat"),
            label = "Échauffement anormal",
            checks = listOf(
                "X" to "Borniers : connexions desserrées, oxydation.",
                "KM" to "Contacts de puissance : usure, piqûres.",
                "M" to "Moteur : ventilation, surcharge mécanique, courant par phase."
            )
        ),
        Rule(
            keywords = listOf("pas de tension", "sans tension", "hors tension", "no power"),
            label = "Absence de tension",
            checks = listOf(
                "Q" to "Source / disjoncteur général : tension amont.",
                "F" to "Fusibles et protections de l'alimentation.",
                "T" to "Transformateur : tension primaire et secondaire.",
                "X" to "Borniers d'arrivée : connexions."
            )
        )
    )

    fun diagnose(graph: DocumentGraph, symptom: String, suspectTag: String? = null): DiagnosticResult {
        val s = symptom.lowercase()
        val rule = RULES.firstOrNull { r -> r.keywords.any { it in s } }
        val steps = mutableListOf<DiagnosticStep>()
        var order = 1

        // 1) Appareil suspect : où il apparaît, et quels appareils l'entourent.
        val suspect = suspectTag?.trim()?.uppercase()?.replace("-", "")?.replace(" ", "")?.takeIf { it.isNotEmpty() }
        if (suspect != null) {
            val pages = graph.pagesWithTag(suspect)
            if (pages.isEmpty()) {
                steps += DiagnosticStep(order++, suspect, emptyList(),
                    "Repère introuvable dans le document : vérifiez l'écriture ou la qualité de l'OCR.")
            } else {
                steps += DiagnosticStep(order++, suspect, pages,
                    "Appareil suspect : contrôlez-le d'abord (alimentation, état, commande). " +
                    "Il apparaît sur ${pages.size} page(s) : bobine et contacts peuvent être séparés.")
                val neighbours = graph.links
                    .filter { it.from in pages && it.label != suspect }
                    .map { it.to }.distinct().sorted()
                if (neighbours.isNotEmpty()) {
                    steps += DiagnosticStep(order++, suspect, neighbours,
                        "Pages liées à consulter : ${neighbours.joinToString { "p.${it + 1}" }}.")
                }
            }
        }

        // 2) Contrôles génériques de la règle trouvée.
        rule?.checks?.forEach { (prefix, check) ->
            val matching = graph.pages
                .flatMap { p -> p.tags.filter { it.startsWith(prefix) && isFamily(it, prefix) }.map { it to p.index } }
            if (matching.isNotEmpty()) {
                matching.groupBy({ it.first }, { it.second }).entries
                    .sortedBy { it.key }
                    .take(4)
                    .forEach { (tag, pages) ->
                        steps += DiagnosticStep(order++, tag, pages.distinct(), check)
                    }
            }
        }

        val summary = when {
            rule != null -> "${rule.label} : ${steps.size} contrôle(s) proposé(s), du plus en amont au plus en aval."
            suspect != null -> "Symptôme non reconnu : contrôles centrés sur l'appareil $suspect."
            else -> "Symptôme non reconnu. Décrivez-le autrement ou indiquez un appareil suspect, " +
                "ou utilisez le diagnostic cloud."
        }
        return DiagnosticResult(summary, steps, "hors-ligne")
    }

    /** "KM1" appartient à la famille "KM", mais pas "K" (qui désigne les relais seuls). */
    private fun isFamily(tag: String, prefix: String): Boolean {
        val rest = tag.removePrefix(prefix)
        return rest.firstOrNull()?.isDigit() == true
    }
}
