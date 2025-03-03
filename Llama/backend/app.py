import re
import threading
import concurrent.futures
import gpt4all
from flask import Flask, request, jsonify
from flask_talisman import Talisman
from flask_caching import Cache

app = Flask(__name__)
Talisman(app)

# Flask-Caching konfigurieren (Simple Cache speichert Daten im RAM)
app.config['CACHE_TYPE'] = 'simple'
cache = Cache(app)

# Lade das Modell einmalig in den Speicher (Caching des Modells)
model_path = "D:/Programme/gpt4all/Llama-3.2-3B-Instruct-Q4_0.gguf"
model = None

# Globale Variable für den Gesprächskontext (für den aktuellen Nutzer)
conversation_contexts = {}

# Locks für thread-sicheren Zugriff
model_lock = threading.Lock()
conversation_lock = threading.Lock()

# Executor für die Auslagerung von Modellaufrufen
executor = concurrent.futures.ThreadPoolExecutor(max_workers=1)

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
        _ = executor.submit(generate_response, "Warming up", 10, 0.7).result()
        print("Model pre-warming successful.")
    except Exception as e:
        print("Model pre-warming failed:", e)

def generate_response(prompt, max_tokens, top_p):
    """Führt den Modellaufruf thread-sicher aus."""
    with model_lock:
        return model.generate(prompt, max_tokens=max_tokens, top_p=top_p)

@app.route("/", methods=["GET"])
def health_check():
    return jsonify({"status": "Server is running"})

@app.route("/", methods=["POST"])
def chat():
    data = request.json

    # Session-Handling
    session_id = data.get("session_id", "default_user")
    with conversation_lock:
        if session_id not in conversation_contexts:
            conversation_contexts[session_id] = ""

    user_input = data.get("input", "").strip()

    # Prüfe, ob es bereits eine zwischengespeicherte Antwort gibt
    cached_key = f"{session_id}:{user_input}"
    cached_response = cache.get(cached_key)
    if cached_response:
        return jsonify({"response": cached_response})

    # Erstelle den Prompt unter Einbeziehung des bisherigen Kontexts
    with conversation_lock:
        context = conversation_contexts[session_id]
    if len(context) > 500:
        context = context[-500:]
    prompt = (
        "Du bist ein sprachgesteuerter Assistent für autistische Nutzer. "
        "Beachte folgende wichtige Regeln:\n\n"
        "1. Antworte in kurzen, einfachen Sätzen (5-15 Wörter pro Satz).\n"
        "2. Verwende eine neutrale Sprache ohne Metaphern oder Redewendungen.\n"
        "3. Gib nur relevante Informationen und vermeide Smalltalk.\n"
        "4. Formuliere direkt und eindeutig.\n"
        "5. Erkenne auch unkonventionelle Sprachmuster.\n"
        "6. Vermeide überflüssige Wörter.\n\n"
        "Gesprächskontext:\n" + context + "\n\n"
        "Frage: " + user_input
    )

    try:
        raw_response = executor.submit(generate_response, prompt, 50, 0.7).result()
    except Exception as e:
        return jsonify({"error": str(e)})

    response = process_response(raw_response)

    # Aktualisiere den Gesprächskontext thread-sicher
    with conversation_lock:
        conversation_contexts[session_id] += "\nNutzer: " + user_input + "\nAssistent: " + response
        context_lines = conversation_contexts[session_id].split('\n')
        if len(context_lines) > 10:
            conversation_contexts[session_id] = '\n'.join(context_lines[-10:])

    # Cache die Antwort für 5 Minuten
    cache.set(cached_key, response, timeout=300)

    return jsonify({"response": response})

def process_response(response):
    """Verarbeitet die Antwort für autistische Nutzer gemäß den Anforderungen."""
    response = response.strip()
    for prefix in ["antwort:", "antowrt:", "antwort", "assistent:", "assistent"]:
        if response.lower().startswith(prefix):
            response = response[len(prefix):].strip()

    parts = response.split("Antwort:")
    if len(parts) > 1:
        response = parts[1].strip()
    else:
        question_fragments = ["?", "Zwiebeln", "Tomaten", "wie", "was", "warum", "wann", "wo", "wer"]
        for fragment in question_fragments:
            if response.startswith(fragment):
                sentence_end = response.find(". ")
                if sentence_end > 0:
                    response = response[sentence_end + 2:]
                break

    sentences = re.split(r'(?<=[.!?])\s+', response)
    sentences = [s.strip() for s in sentences if len(s.strip()) > 3]
    sentences = sentences[:3]

    simplified_sentences = []
    for sentence in sentences:
        words = sentence.split()
        if len(words) > 15:
            chunks = [' '.join(words[i:i+12]) for i in range(0, len(words), 12)]
            for chunk in chunks:
                if not chunk.endswith(('.', '!', '?')):
                    chunk += '.'
                simplified_sentences.append(chunk)
        else:
            if not sentence.endswith(('.', '!', '?')):
                sentence += '.'
            simplified_sentences.append(sentence)

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
        filler_words = ["eigentlich", "sozusagen", "quasi", "praktisch", "irgendwie", "gewissermaßen"]
        for word in filler_words:
            filtered = re.sub(r'\b' + word + r'\b', '', filtered)
        filtered = filtered.strip()
        if filtered and not filtered.isspace():
            filtered_sentences.append(filtered)

    for i in range(len(filtered_sentences)):
        if not filtered_sentences[i][-1] in ['.', '!', '?']:
            filtered_sentences[i] += '.'

    final_response = ' '.join(filtered_sentences)
    final_response = re.sub(r'\s+', ' ', final_response).strip()

    if "?" in final_response and ". " in final_response:
        question_end = final_response.find("?")
        sentence_after = final_response.find(". ", question_end)
        if sentence_after > 0:
            final_response = final_response[sentence_after + 2:]
    
    return final_response

if __name__ == "__main__":
    load_model()  # Modell vor dem Serverstart laden
    with app.app_context():
        warm_up_model()  # Pre-Warming des Modells
    app.run(ssl_context=("cert.pem", "key.pem"), host="0.0.0.0", port=5000, debug=True)
