# Benutzerhandbuch: Autismus-unterstützender KI-Sprachassistent

Dieses Handbuch führt Sie durch die Installation und Einrichtung eines spezialisierten Sprachassistenten, der für die Kommunikation mit autistischen Nutzern optimiert wurde. Der Assistent verwendet ein lokales Sprachmodell.

## Inhaltsverzeichnis Backend
1. Systemanforderungen
2. Installation der erforderlichen Software
3. Download des Sprachmodells
4. Einrichtung des SSL-Zertifikats
5. Konfiguration und Start des Assistenten im Backend
6. Nutzung des Assistenten im Backend
7. Fehlerbehandlung
8. Einrichtung der Android-App (Frontend)
9. Anpassung der URL
10. Starten der Android-App
11. Fehlerbehebung


## 1. Systemanforderungen

- Windows, macOS oder Linux
- Mindestens 16 GB RAM (empfohlen)
- Mindestens 10 GB freier Festplattenspeicher
- Python 3.8 oder höher
- Internetverbindung für die Übersetzungsfunktionen

## 2. Installation der erforderlichen Software für die Serverstruktur

### 2.1 Python installieren
Falls noch nicht vorhanden, laden Sie Python von [python.org](https://www.python.org/downloads/) herunter und installieren Sie es. Stellen Sie sicher, dass die Option "Add Python to PATH" während der Installation aktiviert ist.

### 2.2 Erforderliche Python-Bibliotheken installieren
Öffnen Sie eine Kommandozeile oder Terminal und führen Sie folgenden Befehl aus:

```bash
pip install flask flask-talisman flask-caching gpt4all sentence-transformers deep-translator
```

## 3. Download des Sprachmodells

### 3.1 GPT4All-Anwendung installieren
1. Besuchen Sie [gpt4all.io](https://gpt4all.io) und laden Sie die GPT4All-Desktop-Anwendung herunter.
2. Installieren Sie die Anwendung auf Ihrem System.

### 3.2 Modell herunterladen
1. Starten Sie die GPT4All-Anwendung.
2. Gehen Sie zum Reiter "Model Explorer" oder "Modelle".
3. Suchen Sie nach "Meta-Llama-3-8B-Instruct" und laden Sie das Modell herunter.
4. Notieren Sie sich den Speicherort des Modells (standardmäßig unter Windows: "C:/Users/[Benutzername]/AppData/Local/nomic.ai/GPT4All").

### 3.3 Modellpfad im Code anpassen
Öffnen Sie den Python-Code und ändern Sie den Pfad in der Zeile:
```python
model_path = "D:/Programme/gpt4all/Meta-Llama-3-8B-Instruct.Q4_0.gguf"
```
Tragen Sie hier den korrekten Pfad zu Ihrer Modelldatei ein.

## 4. Einrichtung des SSL-Zertifikats (Optional)

Der Server benötigt SSL-Zertifikate für eine sichere Verbindung. Sie können selbstsignierte Zertifikate für Testzwecke erstellen (oder Sie verwenden die bereits exisiterenden):

### 4.1 OpenSSL installieren (falls noch nicht vorhanden)
- Windows: Laden Sie OpenSSL von [slproweb.com](https://slproweb.com/products/Win32OpenSSL.html) herunter
- macOS: `brew install openssl`
- Linux: `sudo apt-get install openssl`

### 4.2 Zertifikate generieren
Öffnen Sie ein Terminal oder eine Kommandozeile im gleichen Verzeichnis wie Ihr Python-Script und führen Sie folgenden Befehl aus:

```bash
openssl req -x509 -newkey rsa:4096 -nodes -out cert.pem -keyout key.pem -days 365
```

Folgen Sie den Anweisungen zur Eingabe der erforderlichen Informationen.

## 5. Konfiguration und Start des Sprachassistenten

### 5.1 Code
Navigieren Sie zu `app.py`.
```bash
cd Llama\backend\
```

### 5.2 Prüfen der Konfiguration
Stellen Sie sicher, dass alle Pfade korrekt sind:
- Der Modellpfad zeigt auf die heruntergeladene `.gguf`-Datei
- Die Zertifikatsdateien (`cert.pem` und `key.pem`) befinden sich im gleichen Verzeichnis wie das Script

### 5.3 Server starten
Öffnen Sie ein Terminal oder eine Kommandozeile im Verzeichnis des Scripts und führen Sie aus:

```bash
python app.py
```

Beim ersten Start wird das Modell geladen, was je nach Systemleistung einige Zeit dauern kann. Sie sollten folgende Meldungen sehen:
- "Lade das Modell..."
- "Modell erfolgreich geladen."
- "Warming up the model..."
- "Model pre-warming successful."

Der Server sollte nun unter `https://0.0.0.0:5000` laufen.

## 6. Nutzung des Sprachassistenten

### 6.1 API-Anfragen senden
Verwenden Sie einen REST-Client oder Programmcode, um POST-Anfragen an `https://localhost:5000/` zu senden. Die Anfrage sollte im JSON-Format sein:

```json
{
  "session_id": "user123",
  "input": "Ihre Frage hier",
  "explain": false
}
```

Parameter:
- `session_id`: Eine eindeutige ID für die Konversation (speichert den Gesprächskontext)
- `input`: Die Frage oder Anweisung an den Sprachassistenten (auf Deutsch)
- `explain`: `true` für eine zusätzliche Erklärung der Antwort, `false` sonst leer

### 6.2 Beispiel mit curl
```bash
curl -k -X POST https://localhost:5000/ -H "Content-Type: application/json" -d "{\"session_id\":\"user123\",\"input\":\"Was ist Autismus?\",\"explain\":true}"
```

Der Parameter `-k` ignoriert SSL-Zertifikatswarnungen bei selbstsignierten Zertifikaten.

### 6.3 Beispiel mit Postman
- Rufen Sie die Website: `https://www.postman.com/` auf
- New Request
- POST - URL
- Navigieren Sie zu `Body` und `raw`
- Geben Sie in dem leeren Feld dieses Format:
```json
{
  "session_id": "user123",
  "input": "Ihre Frage hier",
  "explain": false
}
```
- Drücken Sie auf Senden
### 6.4 Antwortformat
Die Antwort wird im JSON-Format zurückgegeben:

```json
{
  "response": "Die Hauptantwort des Sprachassistenten",
  "explanation": "Eine Erklärung der Antwort (nur wenn explain=true)"
}
```

## 7. Fehlerbehebung

### Problem: Modell kann nicht geladen werden
- Überprüfen Sie, ob der Pfad zum Modell korrekt ist
- Stellen Sie sicher, dass das Modell vollständig heruntergeladen wurde
- Prüfen Sie, ob genügend RAM verfügbar ist

### Problem: Übersetzungsfehler
- Stellen Sie sicher, dass eine aktive Internetverbindung besteht

### Problem: Der Server startet nicht
- Überprüfen Sie, ob alle erforderlichen Bibliotheken installiert sind
- Stellen Sie sicher, dass Port 5000 nicht von einer anderen Anwendung verwendet wird
- Prüfen Sie, ob die SSL-Zertifikatsdateien korrekt generiert wurden und im richtigen Verzeichnis liegen

### Problem: Langsame Antwortzeiten
- Die erste Anfrage ist normalerweise langsamer, da das Modell aufgewärmt wird
- Verwenden Sie einen leistungsstärkeren Computer oder reduzieren Sie die Größe des Sprachassistent-Kontexts

## 8. Einrichtung der Android-App (Frontend)
Installation von Android Studio und Kotlin
Android Studio herunterladen und installieren:

Besuchen Sie die Android Studio Webseite und laden Sie die neueste Version herunter. 
[Android Studio](https://developer.android.com/studio?hl=de)

Folgen Sie den Installationsanweisungen für Ihr Betriebssystem.

Bestehendes Projekt öffnen:

    Öffnen Sie Android Studio und öffnen Sie ein bestehendes Projekt unter dem Pfad `Llama\app`.

    Stellen Sie sicher, dass Kotlin als Programmiersprache ausgewählt ist.

Projektkonfiguration:

    Überprüfen Sie, ob Ihre build.gradle-Dateien (sowohl für das Projekt als auch das Modul) korrekt eingerichtet sind (beim Start müssen diese manuell synchronisiert werden).

    Fügen Sie ggf. Abhängigkeiten für Retrofit (für API-Aufrufe) und Jetpack Compose hinzu (sollte aber vorhanden sein).

Das Android-Projekt umfasst folgende wesentliche Komponenten:

- MainActivity.kt: Hauptaktivität, die die Benutzeroberfläche über Jetpack Compose definiert. Hier werden u. a. Sprachaufnahme, -wiedergabe,  Einstellungen und UI-Layouts (Portrait und Landscape) implementiert.
    - Navigieren Sie zu `Llama\app\src\main\java\com\example\llama\MainActivity.kt`

- RetrofitClient: Erstellt einen Retrofit-Client, der auch unsichere Zertifikate (selbstsignierte) akzeptiert.
    - Navigieren Sie zu `Llama\app\src\main\java\com\example\llama\RetrofitClient.kt`

- FlaskApiService: Definiert die API-Schnittstelle zur Kommunikation mit dem Backend (POST-Anfragen zur Generierung der Antworten).
    - Navigieren Sie zu `Llama\app\src\main\java\com\example\llama\FlaskApiService.kt`

## 9. Anpassung der URL
Passen Sie gegebenenfalls die .baseURL in `Llama\app\src\main\java\com\example\llama\RetrofitClient.kt` an.

```kotlin
private const val BASE_URL = "https://YOUR-IP/"
```

Passen Sie zusätzlich die URL in `Llama\app\src\main\res\xml\network_security_config.xml` an, um diese als vertrauenswürdig einzustufen.
```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">YOUR-IP</domain>
</domain-config>
```
## 10. Starten der Android-App
Nutzen Sie den integrierten Emulator oder nutzen Sie die Option "USB-Debugging"

Für USB-Debugging: - Gehen Sie bei Ihrem Android-Gerät in das Einstellungsmenü
                   - Suchen sie die Buildnummer des Gerätes (geht auch über Suchfunktion)
                   - Tippen Sie siebenmal auf die Buildnummer, um den Entwicklermodus zu starten
                   - Navigieren Sie im Einstellungsmenü Ihres Android-Geräts zu den neu erschienenen Entwickleroptionen
                   - Aktivieren Sie "USB-Debugging", dann "OK"
                   - Verbinden Sie ihr Android-Gerät mit Ihrem Computer per USB
                   - Das Android-Gerät sollte nun in Android-Studio erkannt werden
                   - Starten Sie die Anwendung mit ausgewählter Option

Achtung: Die Android-App erzeugt nur Ausgaben, wenn der Backend-Server läuft. Bitte stellen Sie also sicher, dass der Server aktiv ist, bevor Sie die App nutzen..

## 11. Fehlerbehebung
- Häufige Probleme entstehen durch Security-Policies oder blockierte Netzwerkverbindungen.
- Wenn keine Verbindung aufgebaut werden kann:
    - Prüfen Sie, ob Ihre Firewall den Port 5000 blockiert (Standardport für Flask).
        - Wenn ja: Firewall temporär deaktivieren
        - Oder: Regel hinzufügen, um Port 5000 freizugeben 
                
