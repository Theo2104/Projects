# Llama - Sprachassistent für autistische Nutzer

Eine Android-Anwendung, die einen Sprachassistenten mit besonderen Anpassungen für autistische Nutzer bietet. Die App verwendet lokale LLM-Technologie (GPT4All) im Backend und eine benutzerfreundliche Android-Oberfläche im Frontend.

## Funktionen

- **Konsistente Interaktionsmuster**: Gleichbleibende, vorhersehbare UI-Elemente
- **Einfache Sprache**: Klare, direkte Kommunikation ohne Mehrdeutigkeiten
- **Kontextwahrung**: Das System merkt sich den Gesprächsverlauf
- **xAI-Funktionalität**: Option zur Anzeige von Erklärungen zu Antworten
- **Anpassbare Sprachausgabe**: Einstellbare Parameter für Stimmlage, Geschwindigkeit und Lautstärke
- **Visueller Komfort**: Dark Mode und optimierte Farb- und Kontrastverhältnisse
- **Taktiles Feedback**: Sanfte Vibration zum Signalisieren von Aktionen

## Technische Details

### Backend (Flask)

- Verwendet eine lokal eingebundene LLM (GPT4All) für die Antwortgenerierung
- Bietet einen REST-API-Endpunkt für die Android-App
- Verwaltet Sitzungen für mehrere Nutzer
- Verarbeitet Antworten speziell für autistische Nutzer

### Frontend (Android/Kotlin)

- Implementiert mit Jetpack Compose für moderne UI
- Intuitive Benutzeroberfläche mit klaren visuellen Hinweisen
- SpeechRecognizer für Spracheingabe
- TextToSpeech für Sprachausgabe
- Retrofit für API-Kommunikation

## Installation

### Backend

1. Stellen Sie sicher, dass Python 3.8+ installiert ist
2. Installieren Sie die erforderlichen Pakete:
   ```
   pip install -r backend/requirements.txt
   ```
3. Starten Sie den Backend-Server:
   ```
   cd backend
   python app.py
   ```

### Frontend

1. Öffnen Sie das Projekt in Android Studio
2. Konfigurieren Sie die API-URL in der Datei `ApiService.kt`
3. Erstellen und installieren Sie die App auf Ihrem Android-Gerät

## Verwendung

1. Starten Sie die App auf Ihrem Android-Gerät
2. Tippen Sie auf "Sprich mit mir", um eine Sprachanfrage zu stellen
3. Der Assistent verarbeitet Ihre Anfrage und gibt eine angepasste Antwort
4. Verwenden Sie die Einstellungen, um die Sprachausgabeparameter anzupassen

## Herausforderungen und Lösungen

Während der Entwicklung dieses Projekts wurden verschiedene technische Herausforderungen identifiziert und gelöst. Diese Dokumentation kann bei ähnlichen Problemen helfen:

### API-Verbindungsprobleme

#### 1. Verbindung zwischen Android-App und Flask-Server

**Probleme:**
- Die Android-App konnte keine stabile Verbindung zum Flask-Backend herstellen
- "Connection reset"-Fehler traten häufig auf
- SSL/TLS-Handshake-Fehler verhinderten sichere Verbindungen
- Die App konnte die vom Server gesendeten selbstsignierten Zertifikate nicht validieren

**Lösungen:**
- Implementierung eines flexiblen API-Client-Systems mit automatischer Umgebungserkennung (Emulator vs. reales Gerät)
- Support für verschiedene Server-URLs mit unterschiedlichen IP-Adressen
- Option zum Umschalten zwischen HTTP (Entwicklung) und HTTPS (Produktion)
- Verbesserter OkHttpClient mit TLS-Konfiguration, der selbstsignierte Zertifikate akzeptiert
- Detaillierte Fehlerprotokollierung für Diagnose und Debugging

#### 2. Verschiedene Umgebungen und Netzwerke

**Probleme:**
- Unterschiedliche IP-Adressen je nach Testumgebung (Emulator, physisches Gerät, WLAN, mobile Daten)
- Der Emulator benötigt die spezielle IP `10.0.2.2` für den Zugriff auf den Host-Computer
- Finden der richtigen IP-Adresse in verschiedenen Netzwerken

**Lösungen:**
- Implementierung einer automatischen Umgebungserkennung über `isEmulator()`
- Mehrere vorkonfigurierte Server-Optionen für verschiedene Netzwerkumgebungen
- Automatische Auswahl der optimalen Server-URL basierend auf der erkannten Umgebung

#### 3. HTTP vs. HTTPS

**Probleme:**
- HTTPS erfordert gültige Zertifikate, die auf Android schwer zu akzeptieren sind
- SSL-Handshake-Fehler bei selbstsignierten Zertifikaten
- Zusätzliche Konfiguration in Android für die Akzeptanz unsicherer Verbindungen

**Lösungen:**
- Entwicklungsmodus mit HTTP für einfache Tests und Debugging
- Produktionsmodus mit HTTPS für sichere Kommunikation
- Verbesserter OkHttpClient, der SSL-Probleme umgeht
- Netzwerk-Sicherheitskonfiguration in Android für erlaubte Cleartext-Verbindungen

### Spracherkennung und -verarbeitung

#### 1. Deutsche Sprache

**Probleme:**
- Schwierigkeiten bei der Erkennung deutscher Sprache auf einigen Geräten
- Inkonsistente Ergebnisse bei der Spracherkennung
- Fehlerhafte Locale-Konfiguration führte zu unerwarteten Ergebnissen

**Lösungen:**
- Implementierung eines Zwei-Wege-Ansatzes zur Spracherkennung
- Explizite Konfiguration für "de-DE" statt komplexer Locale-Objekte
- Verbesserte Fehlerbehandlung und Benutzer-Feedback

#### 2. Anpassungen für autistische Nutzer

**Probleme:**
- Standardmäßige Sprachmodell-Antworten nicht optimal für autistische Nutzer
- Typische KI-Antworten enthalten oft Metaphern, komplexe Strukturen und Mehrdeutigkeiten
- Schwankende Antwortqualität und -konsistenz

**Lösungen:**
- Spezieller Prompt für das Sprachmodell mit klaren Anweisungen für einfache Sprache
- Nachbearbeitung der Antworten zur Entfernung von Mehrdeutigkeiten und komplexen Strukturen
- Begrenzung der Satzlänge und Komplexität
- Implementierung von Session-Management für konsistenteren Kontext

### Technische Integration

#### 1. Modell-Einbindung

**Probleme:**
- Große Größe des Sprachmodells (mehrere GB)
- Hohe Speicher- und Rechenanforderungen
- Langsame Startzeit beim ersten Laden des Modells

**Lösungen:**
- Implementierung von Model-Caching für schnellere Startzeiten
- Pre-Warming-Mechanismus für das Modell beim Serverstart
- Optimierte Konfiguration für schnellere Antwortzeiten

#### 2. API-Endpunktkonfiguration

**Probleme:**
- Falsche Routen-Definitionen führten zu 404-Fehlern
- Unterschiedliche Endpunkt-Definitionen zwischen Server und Client
- Inkonsistente Parameter in API-Anfragen

**Lösungen:**
- Standardisierte API-Endpunkte (`/chat` für die Hauptfunktionalität)
- Konsistente Parameter-Benennungen zwischen Client und Server
- Verbesserte Fehlerbehandlung für API-Anfragen und -Antworten

### UI und Benutzererfahrung

#### 1. Barrierefreiheit

**Probleme:**
- Standard-UI-Elemente nicht optimal für autistische Nutzer
- Mangelnde visuelle Hinweise für Systemzustände
- Begrenzte Anpassungsoptionen für individuelle Bedürfnisse

**Lösungen:**
- Implementierung einer klaren visuellen Hierarchie mit Jetpack Compose
- Konsistente Interaktionsmuster und eindeutige Beschriftungen
- Erweiterte Einstellungsmöglichkeiten für Sprachausgabe und Darstellung
- Taktiles Feedback durch anpassbare Vibration

#### 2. Feedback und Fehlerbehandlung

**Probleme:**
- Unzureichende Rückmeldungen bei Fehlern oder Systemzuständen
- Verwirrende oder technische Fehlermeldungen
- Fehlende Fortschrittsanzeige während der Verarbeitung

**Lösungen:**
- Benutzerfreundliche Fehlermeldungen mit klaren Handlungsanweisungen
- Visuelle Ladeanzeigen für bessere Vorhersehbarkeit
- Verbessertes Logging für Diagnose und Fehlerbehebung
- Sprachausgabe für Fehlermeldungen und Systemzustände

## Entwicklungstipps

1. **Server-Konfiguration:**
   - Für Entwicklung: Setze `development_mode = True` in `app.py` für HTTP
   - Für Produktion: Setze `development_mode = False` für HTTPS mit SSL

2. **Android-Konfiguration:**
   - Überprüfe die Server-URLs in `RetrofitClient.kt` für deine Netzwerkumgebung
   - Android Emulator verwendet `10.0.2.2` für den Host
   - Physische Geräte benötigen die tatsächliche IP-Adresse des Servers

3. **Debugging-Tipps:**
   - Nutze die umfangreichen Logs mit Tags wie "ApiRequest", "ServerCheck" und "SSL"
   - Bei Verbindungsproblemen, prüfe Firewall-Einstellungen und Netzwerkzugriff
   - Für SSL-Probleme: Überprüfe die Zertifikatdateien und SSL-Konfiguration

4. **Modell-Performance:**
   - Das erste Laden des Modells kann einige Zeit dauern
   - Pre-Warming-Mechanismus reduziert Latenz bei der ersten Anfrage
   - Modell-Cache verbessert die Antwortzeiten für wiederkehrende Anfragen

5. **API-Verbindungsfehler:**
   - 500 Internal Server Error bei POST-Anfragen (besonders an den /chat Endpunkt)
   - DLL-Ladefehler bei CUDA-Bibliotheken (Failed to load llamamodel-mainline-cuda-avxonly.dll)
   - Unzuverlässige Modellinitialisierung bei ersten Anfragen
   - Fehlerhafte Kontextbehandlung bei längeren Konversationen

   **Lösungen:**
   - Verbesserte Modellinitialisierung mit Pre-Warming und Retry-Mechanismen
   - Robuste Fehlerbehandlung auf Server- und Client-Seite
   - Detaillierte Fehlerprotokolle zur einfacheren Diagnose
   - Intelligente Fallback-Antworten in der App bei Serverausfällen
   - Automatische Wiederholungsversuche mit exponentieller Rückzugsstrategie
   - Kontextspezifische Fehlermeldungen für bessere Benutzerführung

## Anpassung für autistische Nutzer

Diese Anwendung wurde speziell für autistische Nutzer entwickelt und bietet:

- Reduzierte sensorische Belastung
- Vorhersehbare Interaktionen
- Klare Kommunikation ohne Metaphern oder abstrakte Konzepte
- Visuelle Unterstützung durch gut strukturierte UI
- Flexible Anpassungsoptionen für individuelle Bedürfnisse

## Lizenz

Dieses Projekt steht unter der MIT-Lizenz.
