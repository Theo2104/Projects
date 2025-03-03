import gpt4all
from flask import Flask, request, jsonify
from flask_talisman import Talisman
from flask_caching import Cache
import re
import time
import traceback
import sys
import os
import logging
import threading
import multiprocessing
import queue
import uuid
import signal

# Konfiguriere Logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger('llama-backend')

app = Flask(__name__)
Talisman(app)

# Flask-Caching konfigurieren (Simple Cache speichert Daten im RAM)
app.config['CACHE_TYPE'] = 'simple'
cache = Cache(app)

# Maximale Anfragegröße begrenzen (auf 1MB)
app.config['MAX_CONTENT_LENGTH'] = 1 * 1024 * 1024

# Lade das Modell einmalig in den Speicher (Caching des Modells)
model_path = "D:/Programme/gpt4all/Llama-3.2-3B-Instruct-Q4_0.gguf"
model = None
model_loading_attempts = 0
model_max_attempts = 3

# Globale Prozesslisten und Queues für die isolierte Antwortgenerierung
response_queue = multiprocessing.Queue()
request_queue = multiprocessing.Queue()
model_process = None
process_active = False
fallback_mode = True  # Flag für den Fallback-Modus

# Globale Variable für den Gesprächskontext (für den aktuellen Nutzer)
# Verwende ein Dictionary, um mehrere Nutzersessions zu unterstützen
conversation_contexts = {}

# Globale Fehlerbehandlung
@app.errorhandler(Exception)
def handle_exception(e):
    """Globaler Exception-Handler für alle API-Routen"""
    logger.error(f"Globaler Fehler: {str(e)}")
    logger.error(traceback.format_exc())
    return jsonify({
        "error": str(e),
        "message": "Der Server hat einen Fehler festgestellt. Bitte versuche es noch einmal.",
        "status": "error"
    }), 500

# Funktion, die in einem separaten Prozess ausgeführt wird, um das Modell zu isolieren
def model_worker(model_path, request_queue, response_queue):
    """Separate Prozessfunktion für die sichere Ausführung des Sprachmodells"""
    try:
        logger.info(f"Modell-Worker-Prozess gestartet (PID: {os.getpid()})")
        
        # Modell im eigenen Prozess laden
        try:
            logger.info("Lade das Modell im separaten Prozess...")
            if not os.path.exists(model_path):
                logger.error(f"KRITISCH: Modell nicht gefunden unter: {model_path}")
                response_queue.put(("ERROR", "Modelldatei nicht gefunden"))
                return
                
            model = gpt4all.GPT4All(model_path, allow_download=False)
            logger.info("Modell erfolgreich im Worker-Prozess geladen")
            
            # Modell aufwärmen
            logger.info("Prozess wärmt das Modell auf...")
            _ = model.generate("Hello, world!", max_tokens=5)
            logger.info("Modell erfolgreich aufgewärmt")
            
        except Exception as e:
            logger.error(f"Fehler beim Laden des Modells im Worker: {e}")
            logger.error(traceback.format_exc())
            response_queue.put(("ERROR", f"Modellfehler: {str(e)}"))
            return
            
        # Hauptschleife für die Anfrageverarbeitung
        while True:
            try:
                # Auf Anfragen warten
                request_id, prompt = request_queue.get()
                
                # Prüfen auf Beendigungssignal
                if request_id == "TERMINATE":
                    logger.info("Worker-Prozess wird beendet")
                    break
                    
                logger.info(f"Worker verarbeitet Anfrage: {request_id[:8]}...")
                
                try:
                    # Generiere die Antwort
                    raw_response = model.generate(
                        prompt,
                        max_tokens=50,
                        top_p=0.7,
                        temp=0.7,
                        repeat_penalty=1.2,
                        repeat_last_n=64
                    )
                    
                    # Sende erfolgreiche Antwort zurück
                    response_queue.put((request_id, raw_response))
                    logger.info(f"Antwort für {request_id[:8]} erfolgreich generiert")
                    
                except Exception as gen_err:
                    logger.error(f"Fehler bei Generierung für {request_id[:8]}: {gen_err}")
                    response_queue.put((request_id, f"ERROR: {str(gen_err)}"))
                    
            except Exception as loop_err:
                logger.error(f"Fehler in Worker-Hauptschleife: {loop_err}")
                logger.error(traceback.format_exc())
                # Weiter versuchen, nächste Anfrage zu verarbeiten
    
    except Exception as worker_err:
        logger.error(f"Kritischer Fehler im Worker-Prozess: {worker_err}")
        logger.error(traceback.format_exc())
        response_queue.put(("ERROR", f"Worker-Fehler: {str(worker_err)}"))
    
    finally:
        logger.info("Worker-Prozess wird beendet")

def ensure_model_process():
    """Stellt sicher, dass der Modell-Prozess läuft"""
    global model_process, process_active
    
    if model_process is None or not model_process.is_alive():
        if model_process is not None:
            logger.warning("Vorheriger Modell-Prozess nicht mehr aktiv, bereinige...")
            try:
                model_process.terminate()
            except:
                pass
                
        logger.info("Starte neuen Modell-Worker-Prozess...")
        model_process = multiprocessing.Process(
            target=model_worker, 
            args=(model_path, request_queue, response_queue)
        )
        model_process.daemon = True  # Prozess wird beendet, wenn Hauptprozess endet
        model_process.start()
        process_active = True
        logger.info(f"Neuer Modell-Prozess gestartet mit PID: {model_process.pid}")
        
        # Warte einen Moment, damit der Prozess starten kann
        time.sleep(2)
        
        return True
    return True

def run_external_worker_process():
    """Startet den Worker als separaten Python-Prozess"""
    try:
        logger.info("Starte externen Worker-Prozess...")
        # Erstelle den Kommandozeilenbefehl
        cmd = [sys.executable, "model_worker.py", model_path]
        
        # Starte den Prozess ohne zu warten
        import subprocess
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1
        )
        
        # Starte einen Thread zum Lesen der Ausgabe
        def read_output():
            for line in process.stdout:
                logger.info(f"Worker: {line.strip()}")
            for line in process.stderr:
                logger.error(f"Worker-Fehler: {line.strip()}")
                
        threading.Thread(target=read_output, daemon=True).start()
        
        logger.info(f"Externer Worker-Prozess gestartet mit PID: {process.pid}")
        return process
    
    except Exception as e:
        logger.error(f"Fehler beim Starten des externen Workers: {e}")
        logger.error(traceback.format_exc())
        return None

def generate_fallback_response(prompt, session_id):
    """Generiert eine Fallback-Antwort, wenn das Modell nicht verfügbar ist"""
    logger.warning(f"Verwende Fallback-Antwortgenerator für {session_id[:8]}")
    
    # Extrahiere die Kernfrage aus dem Prompt
    query = prompt.split("Frage: ")[-1].strip() if "Frage: " in prompt else prompt
    
    # Basierend auf dem Prompt type, generiere eine passende Antwort
    query_lower = query.lower()
    
    if any(word in query_lower for word in ["hallo", "hi", "hey", "guten tag"]):
        return "Hallo! Wie kann ich dir helfen?"
        
    if any(word in query_lower for word in ["wie geht es dir", "wie fühlst du"]):
        return "Mir geht es gut. Ich bin hier, um dir zu helfen."
        
    if any(word in query_lower for word in ["danke", "dankeschön"]):
        return "Gerne. Ich helfe dir jederzeit."
        
    if any(word in query_lower for word in ["wer bist du", "was bist du"]):
        return "Ich bin ein Assistent, speziell entwickelt für autistische Nutzer."
    
    if any(word in query_lower for word in ["wetter", "temperatur"]):
        return "Ich kann derzeit keine aktuellen Wetterdaten abrufen."
    
    # Für Wissensfragen
    if query_lower.startswith(("wer ist", "was ist", "wie", "warum", "wo", "wann")):
        return "Ich kann diese Frage derzeit nicht beantworten. Das Sprachmodell ist im Fallback-Modus."
    
    # Standardantwort
    return "Ich verstehe deine Anfrage. Das Sprachmodell befindet sich aktuell im Fallback-Modus mit eingeschränkter Funktionalität."

def generate_response_safe(prompt, session_id, timeout=30):
    """Sicheres Generieren einer Antwort mit Timeout und Fehlerbehandlung"""
    global fallback_mode, process_active, model_process
    
    try:
        # Wenn wir im Fallback-Modus sind, verwende direkt den Fallback
        if fallback_mode:
            logger.warning(f"Verwende Fallback-Antwortgenerator für {session_id[:8]}")
            return generate_fallback_response(prompt, session_id)
        
        # Stelle sicher, dass der Worker-Prozess läuft
        if not ensure_model_process():
            logger.error("Modell-Prozess konnte nicht gestartet werden, verwende Fallback")
            fallback_mode = True
            return generate_fallback_response(prompt, session_id)
        
        # Generiere eine eindeutige ID für diese Anfrage
        request_id = str(uuid.uuid4())
        
        try:
            # Sende die Anfrage an den Worker-Prozess
            logger.info(f"Sende Anfrage {request_id[:8]} an Worker-Prozess")
            request_queue.put((request_id, prompt))
            
            # Warte auf Antwort mit Timeout
            start_time = time.time()
            while time.time() - start_time < timeout:
                try:
                    # Nicht-blockierend prüfen
                    if not response_queue.empty():
                        resp_id, content = response_queue.get(block=False)
                        
                        # Prüfe, ob die Antwort für unsere Anfrage ist
                        if resp_id == request_id:
                            if content.startswith("ERROR:"):
                                logger.error(f"Fehler vom Worker für {request_id[:8]}: {content}")
                                # Bei einem Fehler im Worker, wechsle in den Fallback-Modus
                                fallback_mode = True
                                return generate_fallback_response(prompt, session_id)
                            
                            # Verarbeite die erfolgreiche Antwort
                            return process_response(content)
                        elif resp_id == "ERROR":
                            logger.error(f"Allgemeiner Worker-Fehler: {content}")
                            # Worker-Fehler - wechsle in den Fallback-Modus
                            fallback_mode = True
                            return generate_fallback_response(prompt, session_id)
                        else:
                            # Falsche ID - lege zurück in Queue
                            response_queue.put((resp_id, content))
                    
                    # Kurze Pause, um CPU zu schonen
                    time.sleep(0.1)
                    
                except queue.Empty:
                    # Queue ist leer, warte weiter
                    continue
                except Exception as inner_err:
                    # Fange alle anderen Fehler ab, die hier auftreten können
                    logger.error(f"Unerwarteter Fehler in Warte-Schleife: {inner_err}")
                    logger.error(traceback.format_exc())
                    continue
                    
            # Timeout erreicht
            logger.warning(f"Timeout für Anfrage {request_id[:8]} nach {timeout} Sekunden")
            fallback_mode = True  # Switch to fallback mode after a timeout
            return generate_fallback_response(prompt, session_id)
            
        except Exception as e:
            logger.error(f"Fehler bei sicherer Antwortgenerierung: {e}")
            logger.error(traceback.format_exc())
            # Bei einem kritischen Fehler, wechsle in den Fallback-Modus
            fallback_mode = True
            return generate_fallback_response(prompt, session_id)
    
    except Exception as outer_err:
        logger.error(f"Kritischer Fehler in generate_response_safe: {outer_err}")
        logger.error(traceback.format_exc())
        # Auch hier: bei einem kritischen Fehler in der äußeren Funktion, Fallback verwenden
        fallback_mode = True
        return "Es gab ein technisches Problem. Bitte versuche es später erneut."

def load_model():
    """Lädt das Sprachmodell"""
    global model, model_loading_attempts
    
    try:
        if model_loading_attempts >= model_max_attempts:
            logger.error(f"Maximale Anzahl von Ladeversuchen ({model_max_attempts}) erreicht")
            return False
            
        logger.info(f"Lade Modell aus {model_path}...")
        model_loading_attempts += 1
        
        if not os.path.exists(model_path):
            logger.error(f"Modelldatei nicht gefunden: {model_path}")
            return False
            
        model = gpt4all.GPT4All(model_path, allow_download=False)
        logger.info("Modell erfolgreich geladen")
        
        # Wärme das Modell auf
        logger.info("Wärme das Modell auf...")
        _ = model.generate("Hallo, wie geht es dir?", max_tokens=5)
        logger.info("Modell erfolgreich aufgewärmt")
        
        return True
        
    except Exception as e:
        logger.error(f"Fehler beim Laden des Modells: {e}")
        logger.error(traceback.format_exc())
        return False

@app.route("/", methods=["GET"])
def health_check():
    """Health Check Endpunkt"""
    status = "OK" if model_process is not None and model_process.is_alive() else "Model not loaded"
    
    # Prüfe, ob wir im Fallback-Modus sind
    mode = "fallback" if fallback_mode else "normal"
    
    return jsonify({
        "status": status,
        "server": "running",
        "mode": mode,
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
    })

@app.route("/chat", methods=["POST"])
def chat():
    """Verarbeitet Chat-Anfragen und gibt Antworten zurück."""
    try:
        # Debug-Ausgabe der Anfrage
        print(f"Anfrage-Daten: {request.json}")
        print(f"Anfrage-Header: {dict(request.headers)}")
        
        # Extrahiere einen Sessionschlüssel aus der Anfrage (falls vorhanden) oder erstelle einen Standard
        session_id = request.json.get("session_id", "default_user")
        
        # Stelle sicher, dass ein Kontext für diese Session existiert
        if session_id not in conversation_contexts:
            conversation_contexts[session_id] = ""
        
        data = request.json
        user_input = data.get("input", "").strip()
        explain_flag = data.get("explain", "False")  # Optionaler Parameter, um xAI zu aktivieren
        
        # Debug-Informationen
        print(f"Verarbeite Anfrage: session_id={session_id}, input='{user_input}', explain={explain_flag}")
        
        # Konvertiere explain_flag von String zu Boolean, wenn nötig
        if isinstance(explain_flag, str):
            explain_flag = explain_flag.lower() == "true"
        
        # Prüfe, ob die Eingabe zu groß ist
        if len(user_input) > 500:
            return jsonify({
                "response": "Entschuldigung, aber deine Eingabe ist zu lang. Bitte beschränke dich auf maximal 500 Zeichen.",
                "explanation": "Die Längengrenze schützt vor zu hoher Serverlast.",
                "status": "error"
            })
        
        # Caching-Schlüssel erstellen, einschließlich Gesprächskontext
        cached_key = f"{session_id}:{user_input}"
        cached_response = cache.get(cached_key)
        
        # Falls eine gecachte Antwort vorhanden ist, verwende diese
        if cached_response:
            print(f"Cache-Treffer für Anfrage: {cached_key}")
            
            # Falls gewünscht, generiere zusätzlich eine Erklärung (xAI-Funktion)
            explanation = ""
            if explain_flag:
                explanation = generate_explanation(cached_response, user_input)
            
            return jsonify({
                "response": cached_response, 
                "explanation": explanation,
                "status": "success",
                "cached": True
            })
        
        # Erstelle einen Prompt, der alle gewünschten Anforderungen enthält.
        prompt = (
            "Du bist ein sprachgesteuerter Assistent für autistische Nutzer. "
            "Beachte folgende wichtige Regeln:\n\n"
            "1. Antworte immer in kurzen, einfachen Sätzen mit 5-15 Wörtern pro Satz.\n"
            "2. Verwende eine neutrale Sprache ohne Metaphern oder Redewendungen.\n"
            "3. Gib nur relevante Informationen und vermeide unnötige Details.\n"
            "4. Formuliere direkt und eindeutig ohne Mehrdeutigkeiten.\n"
            "5. Erkenne auch monotone oder unkonventionelle Sprachmuster.\n"
            "6. Vermeide Übertreibungen oder überflüssige Wörter.\n\n"
            "Gesprächskontext:\n" + conversation_contexts[session_id] + "\n\n"
            "Frage: " + user_input
        )

        # Überprüfe, ob der Modellprozess aktiv ist
        if not fallback_mode and not ensure_model_process():
            # Bei Problemen mit dem Modellprozess zum Fallback-Modus wechseln
            logger.warning("Modellprozess konnte nicht gestartet werden, wechsle zu Fallback-Modus")
            fallback_mode = True
        
        # Verwende die neu erstellte Funktion mit verbesserter Fehlerbehandlung
        response = generate_response_safe(prompt, session_id)
        
        # Aktualisiere den Gesprächskontext mit der aktuellen Frage und Antwort
        conversation_contexts[session_id] += f"\nFrage: {user_input}\nAntwort: {response}"
        
        # Beschränke den Kontext auf die letzten 10 Zeilen
        context_lines = conversation_contexts[session_id].split('\n')
        if len(context_lines) > 10:  # 5 Fragen und 5 Antworten
            conversation_contexts[session_id] = '\n'.join(context_lines[-10:])
        
        # Speichere die Antwort im Cache (z.B. für 5 Minuten)
        cache.set(cached_key, response, timeout=300)
        
        # Falls gewünscht, generiere zusätzlich eine Erklärung (xAI-Funktion)
        explanation = ""
        if explain_flag:
            explanation = generate_explanation(response, user_input)
        
        return jsonify({
            "response": response, 
            "explanation": explanation,
            "status": "success"
        })
    except Exception as e:
        print(f"Fehler im Chat-Endpoint: {str(e)}")
        print(traceback.format_exc())
        
        # Detailliertere Antwort für Debugging
        error_message = str(e)
        error_type = type(e).__name__
        
        if "model" in locals() and model is None:
            return jsonify({
                "response": "Das Sprachmodell ist nicht verfügbar. Bitte starten Sie den Server neu.",
                "explanation": "",
                "status": "error",
                "error_type": error_type,
                "error_details": error_message
            }), 500
        
        return jsonify({
            "response": "Fehler bei der Verarbeitung der Anfrage. Bitte versuche es noch einmal.",
            "explanation": "",
            "status": "error",
            "error_type": error_type,
            "error_details": error_message
        }), 500

def process_response(response):
    """Verarbeitet die Antwort für autistische Nutzer gemäß den Anforderungen."""
    # Entferne unerwünschte Präfixe
    response = response.strip()
    
    # Entferne häufige Präfixe
    for prefix in ["antwort:", "antowrt:", "antwort", "assistent:", "assistent"]:
        if response.lower().startswith(prefix):
            response = response[len(prefix):].strip()
    
    # Prüfe, ob die Antwort mit Fragmenten einer Frage beginnt
    # Dies kann passieren, wenn das Modell Teile der Eingabeaufforderung zurückgibt
    question_fragments = ["?", "Zwiebeln", "Tomaten", "wie", "was", "warum", "wann", "wo", "wer"]
    parts = response.split("Antwort:")
    
    if len(parts) > 1:
        # Wenn "Antwort:" in der Antwort gefunden wird, nimm nur den Teil danach
        response = parts[1].strip()
    else:
        # Prüfe auf Fragmente am Anfang des Texts
        for fragment in question_fragments:
            if response.startswith(fragment):
                # Suche nach dem ersten Satzende nach dem Fragment
                sentence_end = response.find(". ")
                if sentence_end > 0:
                    response = response[sentence_end + 2:]
                break
    
    # Teile in Sätze auf
    sentences = re.split(r'(?<=[.!?])\s+', response)
    
    # Filtere leere Sätze und sehr kurze Fragmente
    sentences = [s.strip() for s in sentences if len(s.strip()) > 3]
    
    # Beschränke auf max. 3 Sätze für kurze, fokussierte Antworten
    sentences = sentences[:3]
    
    # Vereinfache komplexe Sätze (teile lange Sätze)
    simplified_sentences = []
    for sentence in sentences:
        words = sentence.split()
        if len(words) > 15:  # Wenn Satz zu lang ist
            # Teile in kleinere Stücke
            chunks = [' '.join(words[i:i+12]) for i in range(0, len(words), 12)]
            for chunk in chunks:
                if not chunk.endswith(('.', '!', '?')):
                    chunk += '.'
                simplified_sentences.append(chunk)
        else:
            # Stelle sicher, dass der Satz ein Satzzeichen am Ende hat
            if not sentence.endswith(('.', '!', '?')):
                sentence += '.'
            simplified_sentences.append(sentence)
    
    # Filtere unnötige Phrasen und Füllwörter
    filtered_sentences = []
    unhelpful_phrases = [
        "Ich kann also", "Daher lautet", "Als KI-Modell", "Ich hoffe", 
        "ich bin gerne", "Ich bin hier", "Kann ich sonst", "Gibt es etwas",
        "Frage:", "Sie fragen", "Du fragst", "Die Frage ist"
    ]
    
    for sentence in simplified_sentences:
        filtered = sentence
        for phrase in unhelpful_phrases:
            filtered = filtered.replace(phrase, "")
        
        # Entferne Füllwörter
        filler_words = ["eigentlich", "sozusagen", "quasi", "praktisch", "irgendwie", "gewissermaßen"]
        for word in filler_words:
            filtered = re.sub(r'\b' + word + r'\b', '', filtered)
        
        filtered = filtered.strip()
        if filtered and not filtered.isspace():
            filtered_sentences.append(filtered)
    
    # Stelle sicher, dass jeder Satz mit einem Satzzeichen endet
    for i in range(len(filtered_sentences)):
        if not filtered_sentences[i][-1] in ['.', '!', '?']:
            filtered_sentences[i] += '.'
    
    # Verbinde zu einem Text
    final_response = ' '.join(filtered_sentences)
    
    # Entferne doppelte Leerzeichen
    final_response = re.sub(r'\s+', ' ', final_response).strip()
    
    # Entferne noch einmal alle Fragmente von Fragen
    if "?" in final_response and ". " in final_response:
        question_end = final_response.find("?")
        sentence_after = final_response.find(". ", question_end)
        if sentence_after > 0:
            final_response = final_response[sentence_after + 2:]
    
    return final_response

def generate_explanation(answer, query):
    """Generiert eine einfache Erklärung zur Antwort"""
    try:
        # Einfache Erklärung für den Fallback-Modus
        if fallback_mode:
            return "Die Antwort wurde mit einem vereinfachten Mechanismus generiert."
            
        # Einfache Erklärung basierend auf der Länge der Antwort
        if len(answer) < 30:
            return "Die Antwort ist kurz und präzise, um dir die wichtigsten Informationen zu geben."
        elif "nicht" in answer.lower() or "kein" in answer.lower():
            return "Die Antwort enthält eine Verneinung, was bedeutet, dass etwas nicht zutrifft oder nicht verfügbar ist."
        else:
            return "Die Antwort wurde so formuliert, dass sie einfach zu verstehen ist und wichtige Informationen enthält."
    except Exception as e:
        logger.error(f"Fehler bei Erklärungsgenerierung: {e}")
        return "Es konnte keine Erklärung generiert werden."

if __name__ == "__main__":
    success = ensure_model_process()  # Modell vor dem Serverstart laden
    
    # Versuche mehrmals, das Modell zu laden, falls es fehlschlägt
    max_attempts = 3
    current_attempt = 0
    while not success and current_attempt < max_attempts:
        print(f"Modell-Ladeversuch {current_attempt+1}/{max_attempts}...")
        time.sleep(2)  # Kurze Pause vor dem nächsten Versuch
        success = ensure_model_process()
        current_attempt += 1
    
    if not success:
        print("Konnte das Modell nicht laden. Der Server wird beendet.")
        sys.exit(1)
    
    # Signal-Handler für sauberes Beenden
    def signal_handler(sig, frame):
        """Handler für sauberes Beenden der Prozesse"""
        print("\nBeende Server und Worker-Prozess...")
        if model_process is not None and model_process.is_alive():
            try:
                # Sende Terminierungssignal an Worker
                request_queue.put(("TERMINATE", ""))
                # Warte kurz auf Beendigung
                model_process.join(timeout=3)
                # Falls der Prozess noch läuft, erzwinge Beendigung
                if model_process.is_alive():
                    model_process.terminate()
            except Exception as e:
                print(f"Fehler beim Beenden des Worker-Prozesses: {e}")
        print("Server wird beendet.")
        sys.exit(0)
        
    # Registriere Signal-Handler
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    with app.app_context():
        if success:
            # Pre-Warming des Modells
            print("Wärme das Modell auf...")
            _ = generate_response_safe("Warming up", "default_user")
            print("Modell erfolgreich aufgewärmt")
    
    # Einfach mit HTTP starten für die Entwicklung
    development_mode = True  # Auf False setzen für HTTPS
    
    if development_mode:
        print("ENTWICKLUNGSMODUS: Starte Server mit HTTP (unsicher)...")
        app.run(host="0.0.0.0", port=5000, debug=True, use_reloader=False)  # Reloader deaktivieren wegen des Multiprocessing
    else:
        print("PRODUKTIONSMODUS: Starte Server mit HTTPS...")
        # Hier sollte die SSL-Konfiguration stehen
        app.run(host="0.0.0.0", port=443, ssl_context='adhoc')
