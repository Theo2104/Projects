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

def generate_response(prompt, communication_mode='default', temperature=0.2, max_tokens=300):
    """Generiert eine Antwort mit modalitätsabhängigen Parametern."""
    mode_settings = {
        'default': {'temp': 0.2, 'max_tokens': 300},
        'precise': {'temp': 0.1, 'max_tokens': 200},
        'detailed': {'temp': 0.3, 'max_tokens': 700}
    }
    
    settings = mode_settings.get(communication_mode, mode_settings['default'])
    
    with model_lock:
        return model.generate(
            prompt, 
            temp=settings['temp'], 
            max_tokens=settings['max_tokens']
        )

def update_context(session_id: str, user_input: str, answer: str, communication_mode: str):
    """Speichert den Gesprächskontext mit Kommunikationsmodus."""
    with conversation_lock:
        context = conversation_contexts.get(session_id, [])
        context.append({
            "role": "user", 
            "content": user_input,
            "mode": communication_mode
        })
        context.append({
            "role": "assistant", 
            "content": answer,
            "mode": communication_mode
        })
        # Begrenze den Kontext
        if len(context) > 10:
            context = context[-10:]
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

def clear_context_if_off_topic(session_id: str, user_input: str, threshold: float = 0.4):
    """
    Löscht den gespeicherten Kontext, wenn der aktuelle Input thematisch 
    zu weit von der letzten relevanten Nachricht abweicht.
    
    Enthält der neue Input anaphorische Pronomen (z.B. "ihn", "ihm", "seine"),
    wird angenommen, dass er sich auf vorherige Inhalte bezieht und der Kontext bleibt erhalten.
    """
    # Prüfe auf anaphorische Pronomen im neuen Input
    if re.search(r"\b(ihn|ihm|seine|seiner|ihr|ihre|er|sie|es|damit)\b", user_input, re.IGNORECASE):
        return

    context = conversation_contexts.get(session_id, [])
    if context:
        # Kombiniere die letzten Nachrichten, um einen repräsentativen Kontext zu erhalten
        last_relevant = ""
        for msg in reversed(context):
            last_relevant = msg["content"] + " " + last_relevant
            if msg["role"] == "assistant":
                break
        if last_relevant:
            embeddings = embedding_model.encode([last_relevant, user_input], convert_to_tensor=True)
            similarity = float(util.pytorch_cos_sim(embeddings[0], embeddings[1]))
            if similarity < threshold:
                conversation_contexts[session_id] = []
                print(f"Kontext zurückgesetzt (Ähnlichkeit: {similarity:.2f}).")

def post_process_answer(answer: str) -> str:
    """
    Verbesserte Funktion zum Entfernen unerwünschter Zusatzinformationen,
    Begrenzung der Antwortlänge und Sicherstellung vollständiger Sätze.
    """
    # Wenn die Antwort eine Frage enthält, schneide alles danach ab
    question_patterns = [
        r"\?(\s|$)",  # Fragezeichen gefolgt von Leerzeichen oder Ende
        r"Keine weitere Frage",
        r"Möchtest du",
        r"Kann ich",
        r"Soll ich",
        r"Willst du"
    ]
    
    for pattern in question_patterns:
        match = re.search(pattern, answer)
        if match:
            # Schneide bei der ersten Frage ab und behalte nur den Satz mit dem Fragezeichen
            pos = match.start()
            # Finde das Ende des Satzes
            end_pos = answer.find('.', pos)
            if end_pos != -1:
                answer = answer[:end_pos+1]
            else:
                # Wenn kein Satzende gefunden wird, nimm alles bis zur Frage
                answer = answer[:pos]
            break
    
    # Dialogmuster erkennen und entfernen (alles nach dem ersten Dialogwechsel)
    dialogue_patterns = [
        r"\n[A-Z][^\.]*:", # Neue Zeile, Großbuchstabe, dann Doppelpunkt
        r"\nDu:",
        r"\nIch:",
        r"\nNutzer:",
        r"\nAssistent:"
    ]
    
    for pattern in dialogue_patterns:
        match = re.search(pattern, answer)
        if match:
            answer = answer[:match.start()]
            break
    
    # Entferne Sätze mit selbstreferenziellen Inhalten
    self_ref_patterns = [
        r"Ich kann dir[^\.]*\.",
        r"Benötigst du[^\.]*\?",
        r"Möchtest du[^\.]*\?",
        r"Kann ich[^\.]*\?",
        r"Du möchtest[^\.]*\?"
    ]
    
    for pattern in self_ref_patterns:
        answer = re.sub(pattern, "", answer)
    
    # Entferne explizite Marker und alles danach
    markers = ["Hinweis:", "Die Frage wurde", "Die Antwort sollte", "Nutzer:", "Frage:", "Falsche",
               "Bitte korrigiere", "Hier sind einige Beispiele"]
    
    for marker in markers:
        if marker in answer:
            parts = answer.split(marker)
            answer = parts[0].strip()
    
    # Entferne "Antwort:" und alles davor
    if "Antwort:" in answer:
        parts = answer.split("Antwort:")
        if len(parts) > 1:
            answer = parts[1].strip()
    
    # Entferne alles nach "welche Art von" oder ähnlichen Fragmenten neuer Fragen
    new_topic_fragments = [
        "welche Art von", 
        "wie funktioniert", 
        "was ist", 
        "warum ist", 
        "wann sollte", 
        "wo kann"
    ]
    
    for fragment in new_topic_fragments:
        if fragment.lower() in answer.lower():
            pos = answer.lower().find(fragment.lower())
            # Finde den Anfang des Satzes
            start_pos = answer.rfind('.', 0, pos)
            if start_pos != -1:
                answer = answer[:start_pos+1]
            break
    
    # Teile die Antwort in Sätze auf
    sentences = re.split(r'(?<=[.!?])\s+', answer)
    sentences = [s.strip() for s in sentences if s.strip()]
    
    # Behalte nur vollständige Sätze (die mit Punkt, Ausrufezeichen oder Fragezeichen enden)
    complete_sentences = []
    for s in sentences:
        if s and re.search(r'[.!?]$', s):
            complete_sentences.append(s)
    
    # Entferne Wiederholungen von Sätzen
    unique_sentences = []
    for sentence in complete_sentences:
        if sentence not in unique_sentences:
            unique_sentences.append(sentence)
    
    # Begrenze auf 3-5 Sätze
    if len(unique_sentences) > 5:
        unique_sentences = unique_sentences[:5]
    
    # Wenn weniger als 3 Sätze vorhanden sind, behalte alle bei
    final_sentences = unique_sentences
    
    # Stelle die Antwort wieder zusammen
    processed_answer = ' '.join(final_sentences)
    
    # Entferne überflüssige Leerzeichen
    processed_answer = re.sub(r'\s+', ' ', processed_answer)
    processed_answer = processed_answer.strip()
    
    return processed_answer


def generate_dynamic_prompt(user_input, context_str, communication_mode='default'):
    """
    Erzeugt einen dynamischen Prompt, der den perfekten Prompt für neurodiverse Kommunikation
    integriert. Dieser Prompt kombiniert die festgelegten Kommunikationsprinzipien mit dem 
    aktuellen Gesprächskontext und der Nutzeranfrage.
    """
    # Perfekter Prompt für autistische Nutzer (auf Englisch)
    perfect_prompt = (
        "Communication Protocol for Neurodiverse Interaction:\n\n"
        "1. Analyze the core question precisely:\n"
        "   - Identify and list the key elements of the question.\n"
        "   - Break down any complex instructions into the smallest possible steps.\n\n"
        "2. Use literal, direct language:\n"
        "   - Avoid idioms, metaphors, or ambiguous expressions.\n"
        "   - Express all ideas in a clear, factual manner with concrete examples when necessary.\n\n"
        "3. Provide structured, step-by-step explanations:\n"
        "   - Present information using numbered steps and bullet points.\n"
        "   - Ensure each step is sequential and builds logically on the previous one.\n"
        "   - Include observable details and explicit descriptions for every instruction.\n\n"
        "4. Maintain consistency and validate understanding:\n"
        "   - Continuously check that every part of your response aligns with the original question.\n"
        "   - Summarize the key points at the end and ask for confirmation or if further clarification is needed.\n\n"
        "5. Prioritize factual and objective information:\n"
        "   - Limit emotional language and subjective interpretations.\n"
        "   - Ensure that every statement is supported by objective data or clearly defined reasoning.\n\n"
        "Response configuration:\n"
        "- Sentence count: Use 3-5 clear, complete sentences per answer.\n"
        "- Complexity: Keep the language simple and direct.\n"
        "- Style: Structured, factual, and sequential.\n\n"
        "Your Answer:\n"
    )
    
    # Anpassung je nach Kommunikationsmodus
    communication_modes = {
        'default': {
            'length': 3,
            'complexity': 'neutral',
            'style': 'direct'
        },
        'precise': {
            'length': 2,
            'complexity': 'low',
            'style': 'scientific'
        },
        'detailed': {
            'length': 5,
            'complexity': 'high',
            'style': 'structured'
        }
    }
    
    mode = communication_modes.get(communication_mode, communication_modes['default'])
    
    dynamic_prompt = (
        f"{perfect_prompt}"
        f"---\nKommunikationsanforderungen:\n"
        f"- Satzanzahl: {mode['length']}\n"
        f"- Komplexitätslevel: {mode['complexity']}\n"
        f"- Stil: {mode['style']}\n\n"
        f"Bisheriger Kontext:\n{context_str}\n\n"
        f"Aktuelle Frage: {user_input}\n\n"
        "Wichtige Regeln:\n"
        "- Keine Selbstüberprüfung oder Meta-Kommentare.\n"
        "- Halte die Antwort präzise und ohne unnötige Wiederholungen.\n\n"
        "Antwort:"
    )
    
    return dynamic_prompt

@app.route("/", methods=["GET"])
def health_check():
    return jsonify({"status": "Server is running"})

@app.route("/", methods=["POST"])
def chat():
    data = request.json
    session_id = data.get("session_id", "default_user")
    user_input = data.get("input", "").strip()
    explain_flag = data.get("explain", False)
    communication_mode = data.get("mode", 'default')

    # Prüfe, ob der Kontext thematisch passt – ansonsten zurücksetzen
    clear_context_if_off_topic(session_id, user_input)

    # Cache prüfen
    cached_key = f"{session_id}:{user_input}:{explain_flag}:{communication_mode}"
    cached_response = cache.get(cached_key)
    if cached_response:
        return jsonify(cached_response)

    # Dynamisch den Kontext zusammenbauen
    context_str = build_context_string(session_id)

    prompt = generate_dynamic_prompt(
        user_input, 
        context_str, 
        communication_mode
    )

    try:
        raw_response = executor.submit(
            generate_response, 
            prompt, 
            temperature=0.3,  # Niedrigere Temperatur für konsistentere Ergebnisse
            max_tokens=500    # Begrenzte Tokenlänge verhindert ausufernde Antworten
        ).result()
    except Exception as e:
        return jsonify({"error": str(e)})

    # Post-Processing: Unerwünschte Zusatztexte entfernen
    answer = post_process_answer(raw_response)
    update_context(session_id, user_input, answer, communication_mode)

    explanation_text = ""
    if explain_flag:
        explanation_text = generate_explanation(answer, user_input, communication_mode)

    response_data = {"response": answer, "explanation": explanation_text}
    cache.set(cached_key, response_data, timeout=300)

    return jsonify(response_data)

def generate_explanation(answer, user_input, communication_mode):
    """Generiert eine kontextabhängige Erklärung."""
    explanation_styles = {
        'default': "Erkläre kurz und einfach.",
        'precise': "Gib eine wissenschaftlich präzise Erklärung.",
        'detailed': "Biete eine strukturierte, schrittweise Erklärung."
    }
    
    explanation_prompt = (
        f"{explanation_styles.get(communication_mode, explanation_styles['default'])}\n"
        f"Kommunikationsmodus: {communication_mode}\n"
        f"Frage: {user_input}\n"
        f"Antwort: {answer}\n\n"
        "Deine Erklärung:"
    )
    
    try:
        raw_explanation = model.generate(
            explanation_prompt, 
            temp=0.2, 
            max_tokens=200
        )
        return post_process_answer(raw_explanation)
    except Exception as e:
        return f"Fehler bei der Erklärung: {str(e)}"

if __name__ == "__main__":
    load_model()
    with app.app_context():
        warm_up_model()
    app.run(ssl_context=("cert.pem", "key.pem"), host="0.0.0.0", port=5000, debug=True)
