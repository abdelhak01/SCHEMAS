# SchemaDiag – Cahier des charges et architecture

Application Android qui importe un PDF multi-pages de schémas électriques, relie les pages entre elles (renvois, repères d'appareils) et aide au diagnostic de pannes.

## 1. Fonctionnalités

### MVP
1. Importer un PDF (sélecteur de fichiers Android, accès SAF).
2. Afficher les pages avec zoom/déplacement.
3. Extraire le texte de chaque page :
   - PDF numérique (EPLAN, SEE Electrical…) : texte vectoriel via PdfBox-Android.
   - PDF scanné : OCR ML Kit sur la page rendue en image.
4. Détecter les repères d'appareils (KM1, Q1, F2, -K3, X1…) et les renvois de pages (« /12.4 », « voir page 5 »).
5. Construire un graphe de liaisons entre pages :
   - renvois explicites (page A → page B),
   - repères partagés (même appareil présent sur plusieurs pages : bobine d'un côté, contacts de l'autre).
6. Navigation : depuis une page, liste des pages liées, saut en un clic.
7. Diagnostic :
   - hors-ligne : moteur de règles (symptôme → appareils à contrôler, pages associées, ordre logique) ;
   - en ligne : envoi des pages pertinentes (image + texte) à l'API Claude, réponse structurée.

### Évolutions
- Recherche d'un repère dans tout le document.
- Historique des pannes par machine (Room), notes et photos terrain.
- Annotation des pages, favoris.
- Export du rapport de diagnostic en PDF.
- Base de règles enrichie par type de machine (moteurs, pompes, convoyeurs, variateurs).

## 2. Architecture

```
UI (Jetpack Compose)  ──  MainViewModel (StateFlow)
        │
        ├── pdf/        PdfDocumentLoader (rendu PdfRenderer)
        │               PageTextExtractor (PdfBox) + OcrExtractor (ML Kit)
        ├── analysis/   ReferenceLinker (repères, renvois, graphe)
        │               DiagnosticEngine (règles hors-ligne)
        └── cloud/      ClaudeClient (API Messages, vision)
```

Flux d'analyse : PDF → pour chaque page : texte (vectoriel, sinon OCR) → `PageInfo` → `ReferenceLinker.link()` → `DocumentGraph` → affiché / utilisé pour le diagnostic.

## 3. Choix techniques
- Kotlin, Jetpack Compose, coroutines, minSdk 26.
- Rendu : `android.graphics.pdf.PdfRenderer`.
- Texte PDF : `com.tom-roster:pdfbox-android`.
- OCR : ML Kit Text Recognition (modèle embarqué, fonctionne hors-ligne).
- Cloud : OkHttp vers `https://api.anthropic.com/v1/messages`.

## 4. Sécurité
- Ne jamais embarquer une clé API dans l'APK en production. Utiliser un petit serveur proxy (authentification utilisateur) qui détient la clé. Le squelette lit une clé saisie dans l'application pour les tests uniquement.
- Les schémas peuvent être confidentiels : prévenir l'utilisateur avant tout envoi cloud (écran de consentement).

## 5. Limites à connaître
- Un moteur de règles ne « comprend » pas un schéma : il liste des contrôles probables. Le diagnostic précis passe par le modèle de vision.
- Les schémas scannés de mauvaise qualité réduisent fortement la fiabilité de l'OCR.
- Les conventions de repérage varient (IEC 81346, DIN, normes maison) : adapter les expressions régulières de `ReferenceLinker`.
- Tout diagnostic doit être vérifié par un électricien habilité ; consignation avant intervention.

## 6. Plan de développement
1. Ouvrir le projet dans Android Studio, synchroniser Gradle, lancer sur un appareil.
2. Valider l'extraction texte sur vos vrais PDF, ajuster les regex.
3. Ajouter la persistance (Room) pour ne pas ré-analyser à chaque ouverture.
4. Ajouter un proxy serveur pour l'API cloud.
5. Enrichir les règles hors-ligne avec vos machines réelles.
6. Tests terrain, puis publication.
