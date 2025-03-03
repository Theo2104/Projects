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

## Anpassung für autistische Nutzer

Diese Anwendung wurde speziell für autistische Nutzer entwickelt und bietet:

- Reduzierte sensorische Belastung
- Vorhersehbare Interaktionen
- Klare Kommunikation ohne Metaphern oder abstrakte Konzepte
- Visuelle Unterstützung durch gut strukturierte UI
- Flexible Anpassungsoptionen für individuelle Bedürfnisse

## Lizenz

Dieses Projekt steht unter der MIT-Lizenz.
