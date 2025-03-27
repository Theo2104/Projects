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

def generate_response(prompt, communication_mode='default'):
    """Generiert eine Antwort mit modalitätsabhängigen Parametern."""
    mode_settings = {
        'default': {'temp': 0.1, 'max_tokens': 400},
        'precise': {'temp': 0.05, 'max_tokens': 200},
        'detailed': {'temp': 0.15, 'max_tokens': 700},
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
    wie interner Instruktionen, Gedankengänge und Bewertungen des Modells,
    sowie zur Sicherstellung vollständiger Sätze.
    """
    # Entferne Text ab "Kontrollpunkte:" falls vorhanden
    if "Kontrollpunkte:" in answer:
        answer = answer.split("Kontrollpunkte:")[0].strip()
    
    # Entferne interne Modellgedankengänge (Chain-of-Thought)
    if "Die Antwort enthält" in answer:
        answer = answer.split("Die Antwort enthält")[0].strip()
    
    # Entferne interne Bewertungen und Korrekturhinweise:
    if "Eine bessere Antwort wäre:" in answer:
        answer = answer.split("Eine bessere Antwort wäre:")[-1].strip()
    
    # Entferne führende Sternchen oder Formatierungen, die auf interne Notizen hinweisen
    answer = re.sub(r'^\*+\s*', '', answer).strip()
    
    question_patterns = [
        r"\?(\s|$)",
        r"Keine weitere Frage",
        r"Möchtest du",
        r"Kann ich",
        r"Soll ich",
        r"Willst du"
    ]
    
    for pattern in question_patterns:
        match = re.search(pattern, answer)
        if match:
            pos = match.start()
            end_pos = answer.find('.', pos)
            if end_pos != -1:
                answer = answer[:end_pos+1]
            else:
                answer = answer[:pos]
            break

    dialogue_patterns = [
        r"\n[A-Z][^\.]*:",
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

    self_ref_patterns = [
        r"Ich kann dir[^\.]*\.",
        r"Benötigst du[^\.]*\?",
        r"Möchtest du[^\.]*\?",
        r"Kann ich[^\.]*\?",
        r"Du möchtest[^\.]*\?"
    ]
    
    for pattern in self_ref_patterns:
        answer = re.sub(pattern, "", answer)
    
    markers = [
        "Hinweis:",
        "Die Frage wurde",
        "Die Antwort sollte",
        "Falsche",
        "Bitte korrigiere",
        "Hier sind einige Beispiele"
    ]
    
    for marker in markers:
        if marker in answer:
            parts = answer.split(marker)
            answer = parts[0].strip()
    
    if "Antwort:" in answer:
        parts = answer.split("Antwort:")
        if len(parts) > 1:
            answer = parts[1].strip()
    
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
            start_pos = answer.rfind('.', 0, pos)
            if start_pos != -1:
                answer = answer[:start_pos+1]
            break

    # Aufteilen in Sätze
    sentences = re.split(r'(?<=[.!?])\s+', answer)
    sentences = [s.strip() for s in sentences if s.strip()]
    
    complete_sentences = [s for s in sentences if re.search(r'[.!?]$', s)]
    
    # Entferne Sätze, die nur aus einer Zahl und einem Punkt bestehen (z. B. "3.")
    filtered_sentences = [s for s in complete_sentences if not re.fullmatch(r'\d+\.', s)]
    
    # Falls alle Sätze entfernt würden, nutze die Original-Sätze
    if filtered_sentences:
        unique_sentences = []
        for sentence in filtered_sentences:
            if sentence not in unique_sentences:
                unique_sentences.append(sentence)
    else:
        unique_sentences = complete_sentences

    # Begrenze auf maximal 5 Sätze
    if len(unique_sentences) > 5:
        unique_sentences = unique_sentences[:5]
    
    processed_answer = ' '.join(unique_sentences)
    processed_answer = re.sub(r'\s+', ' ', processed_answer).strip()
    
    return processed_answer

def generate_dynamic_prompt(user_input, context_str, communication_mode='default'):
    """
    Erzeugt einen dynamischen Prompt, der den perfekten Prompt für neurodiverse Kommunikation
    integriert. Dieser Prompt kombiniert die festgelegten Kommunikationsprinzipien mit dem 
    aktuellen Gesprächskontext und der Nutzeranfrage.
    """
    # Perfekter Prompt für autistische Nutzer (auf Deutsch)
    perfect_prompt = (
        "Deine Aufgabe ist es, einem autistischen Nutzer mit klarer, strukturierter Kommunikation zu helfen. Befolge diese Schritte:\n\n"
        "1. Analysiere die Kernfrage präzise:\n"
        "   - Identifiziere und liste die Schlüsselelemente der Frage auf.\n"
        "   - Zerlege komplexe Anweisungen in die kleinsten möglichen Schritte.\n\n"
        "2. Verwende wörtliche, direkte Sprache:\n"
        "   - Vermeide Idiome, Metaphern oder mehrdeutige Ausdrücke.\n"
        "   - Drücke alle Ideen klar und sachlich aus, mit konkreten Beispielen, wenn nötig.\n\n"
        "3. Gib strukturierte, schrittweise Erklärungen:\n"
        "   - Stelle sicher, dass maximal 1 Fakt pro Satz wiedergegeben wird.\n"
        "   - Stelle sicher, dass jeder Schritt auf den vorherigen logisch aufbaut.\n"
        "   - Vermeide verschachtelte Satzstrukturen.\n\n"
        "4. Halte Konsistenz aufrecht und überprüfe das Verständnis:\n"
        "   - Stelle kontinuierlich sicher, dass jeder Teil deiner Antwort mit der ursprünglichen Frage übereinstimmt.\n"
        "   - Fasse die wichtigsten Punkte am Ende zusammen und frage nach Bestätigung oder ob weitere Klärung benötigt wird.\n\n"
        "5. Priorisiere sachliche und objektive Informationen:\n"
        "   - Begrenze emotionale Sprache und subjektive Interpretationen.\n"
        "   - Stelle sicher, dass jede Aussage durch objektive Daten oder klar definierte Argumente gestützt wird.\n\n"
        "6. Verliere keine wichtigen Fakten und Informationen:\n"
        "   - Überprüfe, ob du alle relevanten Details in deiner Antwort enthalten hast.\n"
        "   - Keine internen Modellgedanken oder Bewertungen\n\n"
        "Antwortkonfiguration:\n"
        "- Satzanzahl: Verwende maximal 5 klare, vollständige Sätze pro Antwort.\n"
        "- Komplexität: Halte die Sprache einfach und direkt.\n"
        "- Stil: Strukturiert, sachlich und schrittweise.\n"
        "- Denke nicht laut nach.\n"
        "- Schreibe keine internen Überlegungen, keine Kontrollpunkte, keine Selbstkritik.\n"
        "- Beginne direkt mit der Antwort.\n"
        "- Keine Hinweise auf die Frageformulierung oder die eigene Antwortstruktur.\n"
        "- Keine Einleitung wie 'Hier ist deine Antwort' oder 'Ich denke, dass...'\n\n"
        "Deine Antwort:\n"
    )
    
    # Erweiterte Kommunikationsmodi mit detaillierteren Anweisungen
    communication_modes = {
        'default': {
            'length': 3,
            'complexity': 'neutral',
            'style': 'direct',
            'detailed_instructions': "Antworte klar und verständlich, ohne zu sehr ins Detail zu gehen. Verwende maximal 3 Sätze"
        },
        'precise': {
            'length': 2,
            'complexity': 'low',
            'style': 'scientific',
            'detailed_instructions': "Fasse die wesentlichen Fakten zusammen und liefere eine präzise, evidenzbasierte Antwort. Verwende maximal 2 Sätze"
        },
        'detailed': {
            'length': 5,
            'complexity': 'high',
            'style': 'structured',
            'detailed_instructions': "Gib eine ausführliche Erklärung mit Schritt-für-Schritt-Anleitungen, die alle Aspekte der Frage abdecken. Verwende maximal 5 Sätze"
        }    
    }
    
    mode = communication_modes.get(communication_mode, communication_modes['default'])
    
    dynamic_prompt = (
        f"{perfect_prompt}"
        f"---\nKommunikationsanforderungen:\n"
        f"- Satzanzahl: {mode['length']}\n"
        f"- Komplexitätslevel: {mode['complexity']}\n"
        f"- Stil: {mode['style']}\n"
        f"- Detaillierte Anweisungen: {mode['detailed_instructions']}\n\n"
        f"Bisheriger Kontext:\n{context_str}\n\n"
        f"Aktuelle Frage: {user_input}\n\n"
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
            communication_mode
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
