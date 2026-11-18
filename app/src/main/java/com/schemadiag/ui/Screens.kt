package com.schemadiag.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schemadiag.data.LinkType

private enum class Tab(val label: String) { VIEWER("Schéma"), LINKS("Liaisons"), DIAG("Diagnostic") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.VIEWER) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.openPdf(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SchemaDiag") },
                actions = {
                    TextButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("Ouvrir PDF") }
                }
            )
        },
        bottomBar = {
            if (state.graph != null) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {},
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> Column(
                    Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(state.progress)
                }
                state.graph == null -> Column(
                    Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally
                ) {
                    Text("Importez un PDF de schémas électriques pour commencer.")
                    state.error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("Choisir un PDF") }
                }
                else -> when (tab) {
                    Tab.VIEWER -> ViewerTab(vm, state)
                    Tab.LINKS -> LinksTab(vm, state) { tab = Tab.VIEWER }
                    Tab.DIAG -> DiagnosticTab(vm, state) { tab = Tab.VIEWER }
                }
            }
        }
    }
}

@Composable
private fun ViewerTab(vm: MainViewModel, state: UiState) {
    val total = state.graph?.pages?.size ?: 0
    var scale by remember(state.currentPage) { mutableFloatStateOf(1f) }
    var offsetX by remember(state.currentPage) { mutableFloatStateOf(0f) }
    var offsetY by remember(state.currentPage) { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { vm.goToPage(state.currentPage - 1) },
                enabled = state.currentPage > 0
            ) { Text("◀") }
            Text("Page ${state.currentPage + 1} / $total")
            OutlinedButton(
                onClick = { vm.goToPage(state.currentPage + 1) },
                enabled = state.currentPage < total - 1
            ) { Text("▶") }
        }
        state.pageBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${state.currentPage + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(state.currentPage) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY
                    )
            )
        }
    }
}

@Composable
private fun LinksTab(vm: MainViewModel, state: UiState, openViewer: () -> Unit) {
    val graph = state.graph ?: return
    val page = graph.pages[state.currentPage]
    val links = graph.linksFrom(state.currentPage)

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Page ${state.currentPage + 1}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                if (page.usedOcr) "Texte obtenu par OCR (fiabilité réduite)" else "Texte vectoriel du PDF",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text("Repères détectés : " + page.tags.sorted().joinToString().ifEmpty { "aucun" })
            Spacer(Modifier.height(16.dp))
            Text("Pages liées (${links.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(links) { l ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                    vm.goToPage(l.to); openViewer()
                }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("→ Page ${l.to + 1}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (l.type) {
                            LinkType.EXPLICIT_REF -> "Renvoi « ${l.label} »"
                            LinkType.SHARED_TAG -> "Appareil commun : ${l.label}"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticTab(vm: MainViewModel, state: UiState, openViewer: () -> Unit) {
    var symptom by remember { mutableStateOf("") }
    var suspect by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(
            value = symptom, onValueChange = { symptom = it },
            label = { Text("Panne observée") },
            placeholder = { Text("ex. le moteur ne démarre pas") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = suspect, onValueChange = { suspect = it },
            label = { Text("Appareil suspect (optionnel)") },
            placeholder = { Text("ex. KM1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it },
            label = { Text("Clé API (diagnostic cloud, test uniquement)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.diagnoseOffline(symptom, suspect) }, enabled = symptom.isNotBlank() || suspect.isNotBlank()) {
                Text("Hors-ligne")
            }
            Button(
                onClick = { vm.diagnoseCloud(symptom, suspect, apiKey) },
                enabled = symptom.isNotBlank() && !state.diagnosing
            ) { Text(if (state.diagnosing) "Analyse…" else "Cloud") }
        }
        Text(
            "Le mode cloud envoie des pages du schéma à un service externe.",
            style = MaterialTheme.typography.bodySmall
        )
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        state.diagnostic?.let { d ->
            Spacer(Modifier.height(16.dp))
            Text("Résultat (${d.source})", style = MaterialTheme.typography.titleMedium)
            Text(d.summary)
            Spacer(Modifier.height(8.dp))
            d.steps.forEach { s ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${s.order}. ${s.component}", style = MaterialTheme.typography.titleSmall)
                        Text(s.check)
                        if (s.pages.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                s.pages.take(6).forEach { p ->
                                    AssistChip(
                                        onClick = { vm.goToPage(p); openViewer() },
                                        label = { Text("p.${p + 1}") }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Consignez l'installation avant toute intervention. Intervention réservée à du personnel habilité.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
