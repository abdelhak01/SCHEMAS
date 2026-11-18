# SchemaDiag

Application Android (Kotlin + Jetpack Compose) : import d'un PDF de schémas électriques multi-pages, liaison entre les pages, diagnostic de pannes (hors-ligne et cloud).

## Lancer le projet
1. Installer Android Studio (version récente).
2. Menu **File > Open** et choisir ce dossier. Android Studio crée le wrapper Gradle et synchronise les dépendances (internet requis).
3. Lancer sur un téléphone ou un émulateur (Android 8.0 / API 26 minimum).
4. Dans l'appli : **Ouvrir PDF**, attendre l'analyse, puis onglets **Schéma**, **Liaisons**, **Diagnostic**.

Les tests de la logique de liaison : `./gradlew test`.

## Structure
- `pdf/` : rendu des pages, extraction du texte vectoriel (PdfBox), OCR (ML Kit).
- `analysis/ReferenceLinker.kt` : repères d'appareils, renvois de pages, graphe de liaisons.
- `analysis/DiagnosticEngine.kt` : règles de diagnostic hors-ligne (à enrichir).
- `cloud/ClaudeClient.kt` : diagnostic par modèle de vision.
- `ui/` : écrans Compose et ViewModel.

## À savoir
- Ce projet n'a pas été compilé ici (pas d'Android SDK disponible) : des ajustements mineurs de versions Gradle sont possibles à la première synchronisation.
- Adaptez les expressions régulières de `ReferenceLinker` à vos conventions de repérage et testez sur vos vrais PDF.
- Clé API : ne l'embarquez pas dans l'APK en production, utilisez un serveur proxy.
- Voir `ARCHITECTURE.md` pour le cahier des charges et le plan de développement.
