import gpt4all
from flask import Flask, request, jsonify
from flask_talisman import Talisman
from flask_caching import Cache
import re

app = Flask(__name__)
Talisman(app)

# Flask-Caching konfigurieren (Simple Cache speichert Daten im RAM)
app.config['CACHE_TYPE'] = 'simple'
cache = Cache(app)

# Lade das Modell einmalig in den Speicher (Caching des Modells)
model_path = "D:/Programme/gpt4all/Llama-3.2-3B-Instruct-Q4_0.gguf"
model = None

# Globale Variable für den Gesprächskontext (für den aktuellen Nutzer)
# Verwende ein Dictionary, um mehrere Nutzersessions zu unterstützen
conversation_contexts = {}

def load_model():
    """Lädt das GPT-4All Modell in den Speicher."""
    global model
    if model is None:
        print("Lade das Modell...")
        model = gpt4all.GPT4All(model_path)
        print("Modell erfolgreich geladen.")

def warm_up_model():
    """Führt eine Pre-Warming-Anfrage aus, um das Modell zu initialisieren."""
    try:
        print("Warming up the model...")
        _ = model.generate("Warming up", max_tokens=10)
        print("Model pre-warming successful.")
    except Exception as e:
        print("Model pre-warming failed:", e)

@app.route("/", methods=["GET"])
def health_check():
    return jsonify({"status": "Server is running"})

@app.route("/", methods=["POST"])
def chat():
    # Extrahiere einen Sessionschlüssel aus der Anfrage (falls vorhanden) oder erstelle einen Standard
    session_id = request.json.get("session_id", "default_user")
    
    # Stelle sicher, dass ein Kontext für diese Session existiert
    if session_id not in conversation_contexts:
        conversation_contexts[session_id] = ""
    
    data = request.json
    user_input = data.get("input", "").strip()
    explain_flag = data.get("explain", False)  # Optionaler Parameter, um xAI zu aktivieren

    # Optional: Prüfe, ob es bereits eine zwischengespeicherte Antwort gibt
    cached_key = f"{session_id}:{user_input}"
    cached_response = cache.get(cached_key)
    if cached_response:
        # Falls xAI gewünscht wird, generiere Erklärung auch bei gecachten Antworten
        explanation = ""
        if explain_flag:
            explanation = generate_explanation(cached_response, user_input)
        return jsonify({"response": cached_response, "explanation": explanation})

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

    try:
        # Reduziere max_tokens für kürzere, prägnantere Antworten
        raw_response = model.generate(
            prompt,
            max_tokens=50,  # Höheres Limit für komplexere Antworten, wird durch Nachbearbeitung gekürzt
            top_p=0.7,       # Geringerer Wert für konsistentere Antworten
        )
    except Exception as e:
        return jsonify({"error": str(e)})

    response = process_response(raw_response)
    
    # Aktualisiere den Gesprächskontext: füge die Nutzerfrage und die Antwort hinzu
    # Behalte nur die letzten 5 Nachrichten im Kontext (10 Zeilen: 5 Fragen + 5 Antworten)
    conversation_contexts[session_id] += "\nNutzer: " + user_input + "\nAssistent: " + response
    
    # Begrenze den Kontext auf die letzten 5 Interaktionen
    context_lines = conversation_contexts[session_id].split('\n')
    if len(context_lines) > 10:  # 5 Fragen und 5 Antworten
        conversation_contexts[session_id] = '\n'.join(context_lines[-10:])
    
    # Speichere die Antwort im Cache (z.B. für 5 Minuten)
    cache.set(cached_key, response, timeout=300)
    
    # Falls gewünscht, generiere zusätzlich eine Erklärung (xAI-Funktion)
    explanation = ""
    if explain_flag:
        explanation = generate_explanation(response, user_input)
    
    return jsonify({"response": response, "explanation": explanation})

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

def generate_explanation(answer, user_input):
    """Generiert eine Erklärung für die gegebene Antwort mittels xAI-Mechanismen."""
    explanation_prompt = (
        "Du sollst kurz und einfach erklären, warum diese Antwort auf die Frage gegeben wurde. "
        "Beachte diese Regeln:\n"
        "1. Verwende maximal 3 kurze Sätze für die Erklärung.\n"
        "2. Erkläre nur die wichtigsten Faktoren für die Entscheidung.\n"
        "3. Vermeide Fachjargon und komplexe Konzepte.\n"
        "4. Sei konkret und verwende einfache Sprache.\n\n"
        "Frage: " + user_input + "\n"
        "Antwort: " + answer + "\n\n"
        "Erklärung:"
    )
    try:
        raw_explanation = model.generate(
            explanation_prompt,
            max_tokens=100,
            top_p=0.7,
        )
        explanation = process_response(raw_explanation)
    except Exception as e:
        explanation = "Fehler beim Generieren der Erklärung: " + str(e)
    return explanation

if __name__ == "__main__":
    load_model()  # Modell vor dem Serverstart laden
    with app.app_context():
        warm_up_model()  # Pre-Warming des Modells
    app.run(ssl_context=("cert.pem", "key.pem"), host="0.0.0.0", port=5000, debug=True)
