package com.schemadiag.cloud

import android.graphics.Bitmap
import android.util.Base64
import com.schemadiag.data.DiagnosticResult
import com.schemadiag.data.DiagnosticStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Diagnostic cloud : envoie quelques pages (image + texte extrait) à l'API Claude.
 *
 * ATTENTION : en production, n'embarquez jamais la clé dans l'APK. Passez par votre
 * propre serveur (proxy authentifié) et changez [endpoint]. Ici la clé est saisie
 * dans l'appli pour les tests.
 */
class ClaudeClient(
    private val apiKey: String,
    private val endpoint: String = "https://api.anthropic.com/v1/messages",
    private val model: String = "claude-sonnet-5-5"
) {
    private val http = OkHttpClient.Builder()
        .callTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** @param pages paires (numéro de page affiché à partir de 1, image, texte extrait). */
    suspend fun diagnose(
        symptom: String,
        suspectTag: String?,
        pages: List<Triple<Int, Bitmap, String>>
    ): DiagnosticResult = withContext(Dispatchers.IO) {
        val content = JSONArray()
        pages.forEach { (num, bmp, text) ->
            content.put(JSONObject().put("type", "text").put("text", "Page $num du schéma :"))
            content.put(
                JSONObject().put("type", "image").put(
                    "source", JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/jpeg")
                        .put("data", bmp.toBase64Jpeg())
                )
            )
            content.put(JSONObject().put("type", "text")
                .put("text", "Texte extrait de la page $num (peut contenir des erreurs) :\n${text.take(3000)}"))
        }
        content.put(JSONObject().put("type", "text").put("text", buildPrompt(symptom, suspectTag)))

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 2000)
            .put("system", SYSTEM_PROMPT)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val request = Request.Builder()
            .url(endpoint)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Erreur API ${resp.code} : ${raw.take(300)}")
            val text = JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text")
            parse(text)
        }
    }

    private fun buildPrompt(symptom: String, suspectTag: String?) = buildString {
        append("Panne observée : $symptom\n")
        if (!suspectTag.isNullOrBlank()) append("Appareil suspect : $suspectTag\n")
        append(
            "\nRéponds UNIQUEMENT avec un JSON de la forme :\n" +
            "{\"summary\": \"...\", \"steps\": [{\"component\": \"KM1\", \"pages\": [4, 7], " +
            "\"check\": \"...\"}]}\n" +
            "Les steps sont ordonnés du plus probable / plus en amont au plus en aval. " +
            "Les pages sont les numéros affichés (à partir de 1). " +
            "Si le schéma est illisible ou ambigu, dis-le dans summary."
        )
    }

    private fun parse(text: String): DiagnosticResult {
        val json = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
        val steps = json.optJSONArray("steps") ?: JSONArray()
        return DiagnosticResult(
            summary = json.optString("summary"),
            steps = (0 until steps.length()).map { i ->
                val s = steps.getJSONObject(i)
                val pages = s.optJSONArray("pages") ?: JSONArray()
                DiagnosticStep(
                    order = i + 1,
                    component = s.optString("component"),
                    pages = (0 until pages.length()).map { pages.getInt(it) - 1 },
                    check = s.optString("check")
                )
            },
            source = "cloud"
        )
    }

    private fun Bitmap.toBase64Jpeg(): String {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "Tu es un assistant de dépannage électrique industriel. Tu lis des schémas " +
            "électriques multi-pages, suis les renvois entre pages et proposes des contrôles " +
            "ordonnés pour localiser une panne. Tu n'inventes jamais de repères absents du " +
            "schéma. Tu rappelles toujours de consigner l'installation avant toute intervention " +
            "et de faire intervenir un personnel habilité."
    }
}
