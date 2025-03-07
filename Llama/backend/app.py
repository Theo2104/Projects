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

# Gesprächskontexte der Nutzer
conversation_contexts = {}

# Thread-Sicherheit
model_lock = threading.Lock()
conversation_lock = threading.Lock()

# Executor für parallele Modellaufrufe
executor = concurrent.futures.ThreadPoolExecutor(max_workers=1)

# Embedding-Modell zur Kontextbewertung
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
        _ = executor.submit(generate_response, "Warming up", 10, 0.7).result()
        print("Model pre-warming successful.")
    except Exception as e:
        print("Model pre-warming failed:", e)

def generate_response(prompt, max_tokens, top_p):
    """Generiert eine Antwort thread-sicher."""
    with model_lock:
        return model.generate(prompt, max_tokens=max_tokens, top_p=top_p)

def is_relevant_context(context: str, user_input: str, threshold: float = 0.5) -> bool:
    """Prüft, ob der neue Input thematisch zum bisherigen Kontext passt."""
    if not context:
        return True
    context_embedding = embedding_model.encode(context, convert_to_tensor=True)
    input_embedding = embedding_model.encode(user_input, convert_to_tensor=True)
    similarity = util.pytorch_cos_sim(input_embedding, context_embedding).item()
    return similarity >= threshold

def update_context(session_id: str, user_input: str, answer: str):
    """Aktualisiert oder setzt den Gesprächskontext zurück, je nach thematischer Relevanz."""
    with conversation_lock:
        current_context = conversation_contexts.get(session_id, "")
        if not is_relevant_context(current_context, user_input):
            conversation_contexts[session_id] = f"Nutzer: {user_input}\nAssistent: {answer}"
        else:
            conversation_contexts[session_id] += f"\nNutzer: {user_input}\nAssistent: {answer}"
        # Kontextlänge begrenzen
        context_lines = conversation_contexts[session_id].split('\n')
        if len(context_lines) > 10:
            conversation_contexts[session_id] = '\n'.join(context_lines[-10:])

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

    # Kontext abrufen und verarbeiten
    with conversation_lock:
        context = conversation_contexts.get(session_id, "")
    
    prompt = (
        "Du bist ein sprachgesteuerter Assistent für autistische Nutzer. "
        "Beachte folgende Regeln:\n\n"
        "1. Antworte in kurzen, klaren Sätzen (maximal 15 Wörter).\n"
        "2. Keine Metaphern, Redewendungen oder Mehrdeutigkeiten.\n"
        "3. Gib nur relevante Informationen.\n"
        "4. Vermeide unnötige Details oder Übertreibungen.\n\n"
        f"Gesprächskontext:\n{context}\n\n"
        f"Frage: {user_input}"
    )

    try:
        raw_response = executor.submit(generate_response, prompt, 50, 0.7).result()
    except Exception as e:
        return jsonify({"error": str(e)})

    answer = process_response(raw_response)
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
        return process_response(raw_explanation)
    except Exception as e:
        return f"Fehler beim Generieren der Erklärung: {str(e)}"

def process_response(response):
    """Bereinigt und optimiert die Antwort für autistische Nutzer."""
    response = response.strip()
    prefixes = ["antwort:", "assistent:", "Antwort:", "Assistent:"]
    for prefix in prefixes:
        if response.lower().startswith(prefix):
            response = response[len(prefix):].strip()

    sentences = re.split(r'(?<=[.!?])\s+', response)
    sentences = [s.strip() for s in sentences if len(s.strip()) > 3][:3]

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

    unhelpful_phrases = [
        "Ich kann also", "Daher lautet", "Als KI-Modell", "Ich hoffe",
        "ich bin gerne", "Ich bin hier", "Kann ich sonst", "Gibt es etwas",
        "Frage:", "Sie fragen", "Du fragst", "Die Frage ist", "Hinweis:"
    ]
    for i, sentence in enumerate(simplified_sentences):
        for phrase in unhelpful_phrases:
            sentence = sentence.replace(phrase, "")
        filler_words = ["eigentlich", "sozusagen", "quasi", "praktisch", "irgendwie", "gewissermaßen"]
        for word in filler_words:
            sentence = re.sub(r'\b' + word + r'\b', '', sentence)
        simplified_sentences[i] = sentence.strip()

    final_response = ' '.join(simplified_sentences)
    final_response = re.sub(r'\s+', ' ', final_response).strip()

    if "?" in final_response and ". " in final_response:
        question_end = final_response.find("?")
        sentence_after = final_response.find(". ", question_end)
        if sentence_after > 0:
            final_response = final_response[sentence_after + 2:]

    return final_response

if __name__ == "__main__":
    load_model()
    with app.app_context():
        warm_up_model()
    app.run(ssl_context=("cert.pem", "key.pem"), host="0.0.0.0", port=5000, debug=True)
