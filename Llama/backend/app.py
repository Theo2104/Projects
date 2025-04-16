import re
import threading
import concurrent.futures
import gpt4all
from flask import Flask, request, jsonify
from flask_talisman import Talisman
from flask_caching import Cache
from sentence_transformers import SentenceTransformer, util
from deep_translator import GoogleTranslator

app = Flask(__name__)
Talisman(app)

# Flask-Caching konfigurieren
app.config['CACHE_TYPE'] = 'simple'
cache = Cache(app)

# Modellpfad
model_path = "D:/Programme/gpt4all/Meta-Llama-3-8B-Instruct.Q4_0.gguf"
model = None

# Gesprächskontexte als strukturierte Liste speichern
conversation_contexts = {}

# Thread-Sicherheit
model_lock = threading.Lock()
conversation_lock = threading.Lock()
translator_lock = threading.Lock()

# Executor für parallele Modellaufrufe
executor = concurrent.futures.ThreadPoolExecutor(max_workers=1)

# Embedding-Modell
embedding_model = SentenceTransformer('all-MiniLM-L6-v2')

# Übersetzer initialisieren
def translate_to_english(text):
    with translator_lock:
        try:
            return GoogleTranslator(source='de', target='en').translate(text)
        except Exception as e:
            print(f"Übersetzungsfehler (DE->EN): {e}")
            return text  # Fallback auf Originaltext

def translate_to_german(text):
    with translator_lock:
        try:
            return GoogleTranslator(source='en', target='de').translate(text)
        except Exception as e:
            print(f"Übersetzungsfehler (EN->DE): {e}")
            return text  # Fallback auf Originaltext

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

def generate_response(prompt, temperature=0.2):
    """Generiert eine Antwort thread-sicher mit kontrollierten Parametern."""
    with model_lock:
        return model.generate(prompt, temp=temperature)

def update_context(session_id: str, user_input: str, answer: str):
    """Speichert den Gesprächskontext als strukturierte Liste."""
    with conversation_lock:
        context = conversation_contexts.get(session_id, [])
        context.append({"role": "user", "content": user_input})
        context.append({"role": "assistant", "content": answer})
        # Begrenze den Kontext auf die letzten 10 Nachrichten
        if len(context) > 10:
            context = context[-10:]
        conversation_contexts[session_id] = context

def build_context_string(session_id: str, in_english=False) -> str:
    """
    Baut dynamisch einen Kontext-String zusammen, indem er
    nur die relevanten Nachrichten aus der strukturierten Liste einbezieht.
    
    Wenn in_english=True, werden die Rollen auf Englisch übersetzt.
    """
    context = conversation_contexts.get(session_id, [])
    lines = []
    
    user_label = "User" if in_english else "Nutzer"
    assistant_label = "Assistant" if in_english else "Assistent"
    
    for msg in context:
        content = msg["content"]
        # Übersetze den Inhalt ins Englische
        if in_english:
            content = translate_to_english(content)
            
        if msg["role"] == "user":
            lines.append(f"{user_label}: {content}")
        elif msg["role"] == "assistant":
            lines.append(f"{assistant_label}: {content}")
            
    return "\n".join(lines)

def clear_context_if_off_topic(session_id: str, user_input: str, threshold: float = 0.4):
    """
    Löscht den gespeicherten Kontext, wenn der aktuelle Input thematisch 
    zu weit von der letzten relevanten Nachricht abweicht.
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
        r"No further question",
        r"Would you like",
        r"Can I",
        r"Should I",
        r"Do you want",
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
        r"\nAssistent:",
        r"\nYou:",
        r"\nI:",
        r"\nUser:",
        r"\nAssistant:"
    ]
    
    for pattern in dialogue_patterns:
        match = re.search(pattern, answer)
        if match:
            answer = answer[:match.start()]
            break
    
    # Entferne Sätze mit selbstreferenziellen Inhalten (deutsch und englisch)
    self_ref_patterns = [
        r"Ich kann dir[^\.]*\.",
        r"Benötigst du[^\.]*\?",
        r"Möchtest du[^\.]*\?",
        r"Kann ich[^\.]*\?",
        r"Du möchtest[^\.]*\?",
        r"I can help you[^\.]*\.",
        r"Do you need[^\.]*\?",
        r"Would you like[^\.]*\?",
        r"Can I[^\.]*\?",
        r"You want[^\.]*\?"
    ]
    
    for pattern in self_ref_patterns:
        answer = re.sub(pattern, "", answer)
    
    # Entferne explizite Marker und alles danach (deutsch und englisch)
    markers = [
        "Hinweis:", "Die Frage wurde", "Die Antwort sollte", "Nutzer:", "Frage:", "Falsche",
        "Bitte korrigiere", "Hier sind einige Beispiele",
        "Note:", "The question was", "The answer should", "User:", "Question:", "Wrong",
        "Please correct", "Here are some examples"
    ]
    
    for marker in markers:
        if marker in answer:
            parts = answer.split(marker)
            answer = parts[0].strip()
    
    # Entferne "Antwort:" oder "Answer:" und alles davor
    for marker in ["Antwort:", "Answer:"]:
        if marker in answer:
            parts = answer.split(marker)
            if len(parts) > 1:
                answer = parts[1].strip()
    
    # Entferne alles nach "welche Art von" oder ähnlichen Fragmenten neuer Fragen (deutsch und englisch)
    new_topic_fragments = [
        "welche Art von", "wie funktioniert", "was ist", "warum ist", "wann sollte", "wo kann",
        "what kind of", "how does", "what is", "why is", "when should", "where can"
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
    
    # Stelle die Antwort wieder zusammen
    processed_answer = ' '.join(unique_sentences)
    
    # Entferne überflüssige Leerzeichen
    processed_answer = re.sub(r'\s+', ' ', processed_answer)
    processed_answer = processed_answer.strip()
    
    return processed_answer

@app.route("/", methods=["GET"])
def health_check():
    return jsonify({"status": "Server is running"})

@app.route("/", methods=["POST"])
def chat():
    data = request.json
    session_id = data.get("session_id", "default_user")
    user_input = data.get("input", "").strip()
    explain_flag = data.get("explain", False)

    # Prüfe, ob der Kontext thematisch passt – ansonsten zurücksetzen
    clear_context_if_off_topic(session_id, user_input)

    # Cache prüfen
    cached_key = f"{session_id}:{user_input}:{explain_flag}"
    cached_response = cache.get(cached_key)
    if cached_response:
        return jsonify(cached_response)

    # Übersetze die Nutzereingabe ins Englische
    translated_input = translate_to_english(user_input)
    print(f"Übersetzt: '{user_input}' -> '{translated_input}'")

    # Dynamisch den Kontext in englischer Sprache zusammenbauen
    context_str = build_context_string(session_id, in_english=True)

    # Englischer Prompt für bessere Ergebnisse
    prompt = (
    "Your task is to help an autistic user with clear, structured communication. Follow these steps:\n\n"
    "1. Analyze the core question precisely:\n"
    "   - Identify and list the key elements of the question.\n"
    "   - Break down complex instructions into the smallest possible steps.\n\n"
    "2. Use literal, direct language:\n"
    "   - Avoid idioms, metaphors, or ambiguous expressions.\n"
    "   - Generate multiple variants and choose the clearest and most direct formulation.\n\n"
    "3. Provide structured, step-by-step explanations:\n"
    "   - Ensure that a maximum of 1 fact is reported per sentence.\n"
    "   - Make sure that each step builds logically on the previous one.\n"
    "   - Avoid nested sentence structures to enable simple, understandable, linear explanation.\n\n"
    "4. Maintain consistency and check understanding:\n"
    "   - Continuously ensure that each part of your answer is consistent with the original question.\n\n"
    "5. Ensure that all information is objective and verifiable:\n"
    "   - Completely avoid emotional language and subjective interpretations.\n"
    "   - Use only verifiable facts and clear, neutral formulations.\n\n"
    "6. Ensure that your answer is complete and precise:\n"
    "   - Check whether you have included all relevant details in your answer.\n"
    "   - Avoid cutting content if relevant information is lost.\n"
    "   - Completely remove internal model thoughts, speculations, or evaluations.\n\n"
    "7. Consider multimodal processing preferences:\n"
    "   - For complex concepts, offer structured listings or simple visualizations.\n"
    "   - Use lists, tables, or hierarchical structures for multi-part information.\n"
    "   - Also describe visual elements in text to support different processing paths.\n\n"
    "8. Optimize the visual structure to avoid sensory overload:\n"
    "   - Use sufficient empty space between logical sections.\n"
    "   - Avoid text blocks that are too dense and long paragraphs.\n"
    "   - Highlight important key information through uniform, non-distracting formatting.\n\n"
    "9. Adapt the communication style individually:\n"
    "   - Consider the desired level of detail based on the request.\n"
    "   - Use terms and explanation levels that correspond to the user's level of understanding.\n"
    "   - Offer both simplified and more detailed explanations if necessary.\n\n"
    "10. Link with relevant special interests, if possible:\n"
    "    - Use examples from areas such as natural sciences, logic, or systematic processes.\n"
    "    - Establish connections to related concepts that could be conducive to understanding.\n\n"
    "Answer configuration:\n"
    "- Do not think out loud.\n"
    "- Do not write internal considerations, no control points, no self-criticism, no follow-up instructions.\n"
    "- Start directly with the answer.\n"
    "- No reference to the question formulation or the answer structure itself.\n"
    "- No introduction like 'Here is your answer' or 'I think that...'\n\n"
    "Your answer:\n"
    f"{context_str}\n\n"
    f"Current question: {translated_input}\n\n"
    "Your answer:"
)

    try:
        # Generiere Antwort auf Englisch
        raw_response = executor.submit(
            generate_response, 
            prompt, 
            temperature=0.2   
        ).result()
        
        # Post-Processing der englischen Antwort
        processed_en_response = post_process_answer(raw_response)
        
        # Übersetze zurück ins Deutsche
        german_response = translate_to_german(processed_en_response)
        print(f"Antwort übersetzt: EN -> DE")
        
        # Finale Nachbearbeitung der deutschen Antwort
        answer = post_process_answer(german_response)
        
    except Exception as e:
        return jsonify({"error": str(e)})

    # Kontext mit Originaleingabe und übersetzter Antwort aktualisieren
    update_context(session_id, user_input, answer)

    explanation_text = ""
    if explain_flag:
        explanation_text = generate_explanation(answer, user_input)

    response_data = {"response": answer, "explanation": explanation_text}
    cache.set(cached_key, response_data, timeout=300)

    return jsonify(response_data)

def generate_explanation(answer, user_input):
    """Generiert eine einfache Erklärung für die Antwort mit verbesserter Anweisung."""
    # Übersetze für die Erklärung ins Englische
    en_answer = translate_to_english(answer)
    en_input = translate_to_english(user_input)
    
    explanation_prompt = (
        "Your task is to generate a meta-explanation that helps an autistic user better understand your answer. \n\n"
        "Please follow these principles:\n"
        "1. Explain the logic behind your answer.\n"
        "2. Clarify the connections between the question and your answer.\n"
        "3. Identify and resolve potential ambiguities.\n"
        "4. Avoid repeating the original answer.\n\n"
        f"Question: {en_input}\n"
        f"Answer: {en_answer}\n\n"
        "Your explanation:"
    )
    
    try:
        # Generiere Erklärung auf Englisch
        raw_explanation = model.generate(
            explanation_prompt, 
            temp=0.2
        )
        
        # Bereinige die englische Erklärung
        processed_en_explanation = post_process_answer(raw_explanation)
        
        # Übersetze ins Deutsche
        german_explanation = translate_to_german(processed_en_explanation)
        
    
        return post_process_answer(german_explanation)
        
    except Exception as e:
        return f"Fehler bei der Erklärung: {str(e)}"

if __name__ == "__main__":
    load_model()
    with app.app_context():
        warm_up_model()
    app.run(ssl_context=("cert.pem", "key.pem"), host="0.0.0.0", port=5000, debug=False, use_reloader=False)