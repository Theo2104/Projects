import re
import threading
import concurrent.futures
import gpt4all
from flask import Flask, request, jsonify
from flask_talisman import Talisman
from flask_caching import Cache
from sentence_transformers import SentenceTransformer, util

app = Flask(__name__)
Talisman(app)

# Flask-Caching konfigurieren
app.config['CACHE_TYPE'] = 'simple'
cache = Cache(app)

# Modellpfad
model_path = "D:/Programme/gpt4all/Llama-3.2-3B-Instruct-Q4_0.gguf"
model = None

# Gesprächskontexte als strukturierte Liste speichern
conversation_contexts = {}

# Thread-Sicherheit
model_lock = threading.Lock()
conversation_lock = threading.Lock()

# Executor für parallele Modellaufrufe
executor = concurrent.futures.ThreadPoolExecutor(max_workers=1)

# Embedding-Modell
embedding_model = SentenceTransformer('all-MiniLM-L6-v2')

def load_model():
    """Lädt das GPT-4All Modell in den Speicher."""
    global model
    if model is None:
        print("Lade das Modell...")
        model = gpt4all.GPT4All(model_path)
        print("Modell erfolgreich geladen.")

def warm_up_model():
    """Pre-Warming des Modells."""
    try:
        print("Warming up the model...")
        _ = executor.submit(generate_response, "Warming up").result()
        print("Model pre-warming successful.")
    except Exception as e:
        print("Model pre-warming failed:", e)

def generate_response(prompt):
    """Generiert eine Antwort thread-sicher."""
    with model_lock:
        return model.generate(prompt)

def update_context(session_id: str, user_input: str, answer: str):
    """Speichert den Gesprächskontext als strukturierte Liste."""
    with conversation_lock:
        context = conversation_contexts.get(session_id, [])
        context.append({"role": "user", "content": user_input})
        context.append({"role": "assistant", "content": answer})
        # Begrenze den Kontext auf die letzten 20 Nachrichten
        if len(context) > 20:
            context = context[-20:]
        conversation_contexts[session_id] = context

def build_context_string(session_id: str) -> str:
    """
    Baut dynamisch einen Kontext-String zusammen, indem er
    nur die relevanten Nachrichten aus der strukturierten Liste einbezieht.
    """
    context = conversation_contexts.get(session_id, [])
    lines = []
    for msg in context:
        if msg["role"] == "user":
            lines.append(f"Nutzer: {msg['content']}")
        elif msg["role"] == "assistant":
            lines.append(f"Assistent: {msg['content']}")
    return "\n".join(lines)

@app.route("/", methods=["GET"])
def health_check():
    return jsonify({"status": "Server is running"})

@app.route("/", methods=["POST"])
def chat():
    data = request.json
    session_id = data.get("session_id", "default_user")
    user_input = data.get("input", "").strip()
    explain_flag = data.get("explain", False)

    # Cache prüfen
    cached_key = f"{session_id}:{user_input}:{explain_flag}"
    cached_response = cache.get(cached_key)
    if cached_response:
        return jsonify(cached_response)

    # Dynamisch den Kontext zusammenbauen
    context_str = build_context_string(session_id)

    prompt = (
    "Du bist ein sachlicher und direkter Sprachassistent für autistische Nutzer. "
    "Beachte folgende wichtige Regeln:\n\n"
    "1. Antworte in kurzen, einfachen Sätzen (maximal 10 Wörter pro Satz).\n"
    "2. Verwende eine neutrale Sprache ohne Metaphern oder Redewendungen.\n"
    "3. Gib nur relevante Informationen und vermeide Smalltalk.\n"
    "4. Formuliere direkt und eindeutig.\n"
    "5. Erkenne auch unkonventionelle Sprachmuster.\n"
    "6. Vermeide überflüssige Wörter und Fakten.\n"
    "7. Gib ausschließlich deine Antwort als Assistent, ohne die Frage, den Kontext oder Zusatzinformationen zu wiederholen.\n\n"
    "Berücksichtige den folgenden Gesprächskontext, aber gib ihn nicht in deiner Antwort wieder:\n"
    f"{context_str}\n\n"
    f"Frage: {user_input}\n\n"
    "Antwort:"
)

    try:
        raw_response = executor.submit(generate_response, prompt).result()
    except Exception as e:
        return jsonify({"error": str(e)})

    answer = raw_response
    update_context(session_id, user_input, answer)

    explanation_text = ""
    if explain_flag:
        explanation_text = generate_explanation(answer, user_input)

    response_data = {"response": answer, "explanation": explanation_text}
    cache.set(cached_key, response_data, timeout=300)

    return jsonify(response_data)

def generate_explanation(answer, user_input):
    """Generiert eine einfache Erklärung für die Antwort."""
    explanation_prompt = (
        "Erkläre kurz und einfach, warum diese Antwort gegeben wurde:\n"
        f"Frage: {user_input}\n"
        f"Antwort: {answer}\n\n"
        "Erklärung:"
    )
    try:
        raw_explanation = model.generate(explanation_prompt, max_tokens=50, top_p=0.7)
        return raw_explanation
    except Exception as e:
        return f"Fehler beim Generieren der Erklärung: {str(e)}"

if __name__ == "__main__":
    load_model()
    with app.app_context():
        warm_up_model()
    app.run(ssl_context=("cert.pem", "key.pem"), host="0.0.0.0", port=5000, debug=True, use_reloader=False)
