# Initialer Setup

## Starten der Webanwendung

### Installieren der Abhängigkeiten

    npm i
oder

    yarn install

### Starten des Clients

    npm run dev:win
oder

    npm run dev
### Starten des Servers und der Datenbanken

 1. Navigieren zum Verzeichnis 
> server

    cd src/server
    
 2. Installieren der Abhängigkeiten, siehe [hier](#Installieren-der-Abhängigkeiten)
 3. Starten des Servers


    npx ts-node-dev src/index.ts --esm
    

## Starten der Unity Anwendung
Das Starten der Unity Anwendung funktioniert wie bei jeder anderen Unity Anwendung auch jedoch kann es durchaus zu Fehlern kommen. Falls es zu Fehlern kommt gibt es mehrere Dinge die überprüft / getan werden können.

 1. Starten des Mixed Reality Feature Tool und das Betätigen des Buttons 
> Restore Features
 2. Überprüfen der NuGet Pakete. Es muss das NuGet Paket Microsoft.MixedReality.QR vorhanden sein.
 3. Überprüfen des Package Manager. Es müssen alle Pakete installiert sein die in diesem [Screenshot](help/Package_Manager.png) aufgeführt sind.

Sollte es keine Fehler geben sollte die WebView ordnungsgemäß geladen werden.
Bei Verwendung der Hololens muss außerdem Holographic Remoting aktiviert werden. [Weiter Infos](https://learn.microsoft.com/en-us/windows/mixed-reality/mrtk-unity/mrtk2/features/tools/holographic-remoting?view=mrtkunity-2022-05).

### Scannen des QR Codes
Der Nutzer kann einen QR Code mit beliebigen Text scannen, welcher jedoch im Inspector der Komponente MixedRealitySceneContent vordefiniert ist. Der Defaulttext ist dabei 0.
Um den QR Code zu scannen muss der Nutzer sich dem Code mit der Brille nähern bis schließlich die WebView senkrecht über dem QR Code ersichtlich ist. Dabei wird die WebView mittig über dem QR Code platziert. 

## Konfiguration der IP Adresse
Falls die Anwendung über das Netzwerk zugänglich sein soll, müssen folgende Einträge geändert werden.
Setzen der Variablen VITE_API und VITE_HOST. Ersetzen des 

> localhost

 durch eigene IP, siehe [.env](.env)

Ebenfalls notwendig ist die Änderung des Befehls dev bzw dev:win in [package.json](package.json). Ebenfalls muss an dieser Stelle 

> localhost

 durch die eigene IP ersetzt werden. 
 Auch in der Datei [Editor.tsx](./src/components/editor/Editor.tsx) muss in Zeile 11 das 

> localhost

 durch die eigene IP ersetzt werden.
### Ändern der IP in Unity
In der Hierarchie muss die WebView Komponente ausgewählt werden. Anschließend kann im Inspector die URL angepasst werden. Als Default ist die URL 

> http://localhost:5173/

 angegeben.

# Allgemeine Informationen
Die Komponenten des Editors liegen [in](./src/components/editor/).

Die einzelnen Szenarios liegen [in](./src/components/scenario-parts/).

In dem [Ordner](src/components/shapes/) liegen alle Dateien für die Shapes in denen der Inhalt wie zum Beispiel die Szenarien eingefügt werden.

Der [Ordner](src/components/workspace-picker/) beinhaltet alles von der Index Seite. Darin enthalten ist das Verwalten der Räume und das Aktivieren / Deaktivieren des Read-Only Modus. 

In dem Server [Ordner](./src/server/) sind alle relevanten Server Inhalte. Es werden darin die Konfigurationen der Räume sowie die Logs gespeichert, wenn Logging aktiv ist. 

Alle Unity Inhalte sind währenddessen in dem Ordner [Unity](./Unity) zu finden.
# Benutzerhandbuch

## Erstellen eines neuen Raums
Auf der Index Seite der Anwendung ist es möglich die einzelnen Räume zu verwalten. Dies beinhaltet neue Räume zu erstellen und bestehende Löschen zu können. Alle Räume sind im Default im Admin-Modus.

## Aktivierung des Read-Only Modus
Ebenfalls auf der Index Seite kann durch das Betätigen des Buttons der Read-Only Modus aktiviert werden. Wurde der Button betätigt und der Nutzer geht in einen Raum ist dieser nun im Read-Only Modus. Der Modus muss vor jedem Raumbeitritt neu gesetzt werden.

## Admin-Modus
Der Admin-Modus dient dazu existierende Komponenten dem Raum hinzuzufügen. Es wird empfohlen den Admin-Modus lediglich im Browser zu nutzen, da dieser auf der Hololens nicht vollständig unterstützt wird. Dies kann durch die Button in der Mitte unten erfolgen. Wurde eine Komponente angeklickt ist diese nun dem Raum hinzugefügt worden. Bei einem erneuten Klick auf die Komponente (sodass die Ränder blau sind) kann folgendes durchgeführt werden.
Über 

> get position

 und 

> get size

kann der Nutzer die aktuelle Position und aktuelle Größe der Komponente erhalten und über 

> set position

 und 

> apply size

manuell anpassen.
Konfigurationen für die jeweiligen Setups wie dem Surface Hub oder der Display Wall kann [hier](#Konfiguration-der-Position-und-Größe-zur-Verwendung-der-Komponente-auf-der-Hololens-2) eingesehen werden.

Wurde zunächst die gewünschte Größe und Position eingegeben kann nun auf Center Camera geklickt werden und das Szenario gespeichert werden. Dafür kann oben rechts ein neues Szenario erstellt, umbenannt und gespeichert werden. Ebenfalls können so auch existierende Szenarien überschrieben werden

Soll eine Komponente entfernt werden kann diese mit 

> Rechtsklick > Löschen

 entfernt werden.
## Read-Only Modus
Der Read-Only Modus ist für die Durchführung der Studie. Hier ist der Nutzer nicht mehr in der Lage die Komponente zu bewegen oder zu verändern. Ebenfalls stehen die Werkzeuge nicht mehr zur Verfügung und der Zoom ist deaktiviert.

## Konfiguration der Position und Größe zur Verwendung der Komponente auf der Hololens 2
Damit die Konfiguration für die Position und Größe der Komponenten für die Hololens leichter fällt, gibt es hier die Konfigurationen, welche bereits für die Szenarien in der Demo verwendet wurden
### Display Wall
| Position |Size  |
|--|--|
| -545 x -240 | 1090 x 460 |

### Surface Hub
| Position |Size  |
|--|--|
| -420 x -260 | 840 x 460 |

## Logging
Falls ein Logging während der Ausführung des Szenarios erwünscht ist kann neben der Szenario Komponente zusätzlich die Logging Komponente hinzugefügt werden. Dabei ist sowohl innerhalb der Komponente der Log ersichtlich, jedoch wird er ebenso zusätzlich in der [Datei](src\server\logs.json) gespeichert.

## Reset der Anwendung
Soll die Anwendung komplett zurückgesetzt werden so kann ganz einfach der Inhalt dieser [Datei](src\server\database.json) gelöscht werden.