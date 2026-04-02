import re
import threading
import concurrent.futures
import gpt4all
from flask import Flask, request, jsonify
from flask_talisman import Talisman
from flask_caching import Cache
from sentence_transformers import SentenceTransformer, util
#from deep_translator import GoogleTranslator
from transformers import MarianMTModel, MarianTokenizer
import torch

app = Flask(__name__)
Talisman(app)

# Flask-Caching konfigurieren (größerer Cache)
app.config['CACHE_TYPE'] = 'simple'
app.config['CACHE_DEFAULT_TIMEOUT'] = 3600  # 1 Stunde statt 5 Minuten
cache = Cache(app)

# Modellpfad
model_path = "D:/Programme/gpt4all/Meta-Llama-3-8B-Instruct.Q4_0.gguf"
model = None

# GPU-Optimierung für Übersetzungsmodelle
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {device}")

# Optimierte Übersetzungsmodelle mit GPU-Unterstützung
de_en_tokenizer = MarianTokenizer.from_pretrained('Helsinki-NLP/opus-mt-de-en')
de_en_model = MarianMTModel.from_pretrained('Helsinki-NLP/opus-mt-de-en').to(device)
de_en_model.eval()  # Inferenzmodus für bessere Performance

en_de_tokenizer = MarianTokenizer.from_pretrained('Helsinki-NLP/opus-mt-en-de')
en_de_model = MarianMTModel.from_pretrained('Helsinki-NLP/opus-mt-en-de').to(device)
en_de_model.eval()

# Cache für Übersetzungen
translation_cache = {}

# Gesprächskontexte als strukturierte Liste speichern
conversation_contexts = {}

# Thread-Sicherheit mit optimierten Locks
model_lock = threading.Lock()
conversation_lock = threading.Lock()
translator_lock = threading.Lock()

# Optimierter Executor mit mehr Threads für I/O-Operationen
executor = concurrent.futures.ThreadPoolExecutor(max_workers=3)

# Kleineres, schnelleres Embedding-Modell
embedding_model = SentenceTransformer('all-MiniLM-L6-v2')
embedding_model.to(device)

# Optimierte Übersetzerfunktionen mit Caching
def translate_to_english(text: str) -> str:
    # Cache-Key erstellen
    cache_key = f"de_en:{hash(text)}"
    
    if cache_key in translation_cache:
        return translation_cache[cache_key]
    
    with translator_lock:
        try:
            with torch.no_grad():  # Reduziert Memory-Overhead
                inputs = de_en_tokenizer(text, return_tensors="pt", truncation=True, max_length=128).to(device)
                # Optimierte Generation
                with torch.no_grad():
                    translated = de_en_model.generate(
                        **inputs,
                        max_length=128,
                        num_beams=2,  # Reduziert von default 5
                        early_stopping=True,
                        do_sample=False  # Deterministisch für bessere Cache-Hits
                    )
                result = de_en_tokenizer.decode(translated[0], skip_special_tokens=True)
                translation_cache[cache_key] = result
                return result
        except Exception as e:
            print(f"Übersetzungsfehler (DE->EN): {e}")
            return text


def translate_to_german(text: str) -> str:
    # Cache-Key erstellen
    cache_key = f"en_de:{hash(text)}"
    
    if cache_key in translation_cache:
        return translation_cache[cache_key]
    
    with translator_lock:
        try:
            with torch.no_grad():
                inputs = en_de_tokenizer(text, return_tensors="pt", truncation=True, max_length=128).to(device)
                with torch.no_grad():
                    translated = en_de_model.generate(
                        **inputs,
                        max_length=128,
                        num_beams=2,
                        early_stopping=True,
                        do_sample=False
                    )
                result = en_de_tokenizer.decode(translated[0], skip_special_tokens=True)
                translation_cache[cache_key] = result
                return result
        except Exception as e:
            print(f"Übersetzungsfehler (EN->DE): {e}")
            return text

def load_model():
    """Lädt das GPT-4All Modell in den Speicher."""
    global model
    if model is None:
        print("Lade das Modell...")
        # Optimierte Modell-Konfiguration
        model = gpt4all.GPT4All(
            model_path,
            device="gpu" if torch.cuda.is_available() else "cpu",
            n_threads=4  # Parallelisierung
        )
        print("Modell erfolgreich geladen.")

def warm_up_model():
    """Pre-Warming des Modells mit kurzem Test."""
    try:
        print("Warming up the model...")
        # Sehr kurzer Warmup-Prompt
        _ = model.generate("Hi", temp=0.2, max_tokens=10)
        print("Model pre-warming successful.")
    except Exception as e:
        print("Model pre-warming failed:", e)

def generate_response(prompt, temperature=0.2, max_tokens=250):
    """Generiert eine Antwort thread-sicher mit optimierten Parametern."""
    with model_lock:
        # Begrenzte Token-Anzahl für schnellere Generation
        return model.generate(
            prompt, 
            temp=temperature,
            max_tokens=max_tokens,
            streaming=False
        )

def update_context(session_id: str, user_input: str, answer: str):
    """Speichert den Gesprächskontext als strukturierte Liste."""
    with conversation_lock:
        context = conversation_contexts.get(session_id, [])
        context.append({"role": "user", "content": user_input})
        context.append({"role": "assistant", "content": answer})
        # Reduzierte Kontextgröße für bessere Performance
        if len(context) > 6:  # 3 Austausche statt 5
            context = context[-6:]
        conversation_contexts[session_id] = context

def build_context_string(session_id: str, in_english=False) -> str:
    """Optimierte Kontext-Erstellung."""
    context = conversation_contexts.get(session_id, [])
    if not context:
        return ""
    
    lines = []
    user_label = "User" if in_english else "Nutzer"
    assistant_label = "Assistant" if in_english else "Assistent"
    
    # Nur die letzten 2 relevanten Nachrichten verwenden
    recent_context = context[-4:] if len(context) > 4 else context
    
    for msg in recent_context:
        content = msg["content"]
        if in_english and not msg.get("translated"):
            content = translate_to_english(content)
            
        if msg["role"] == "user":
            lines.append(f"{user_label}: {content}")
        elif msg["role"] == "assistant":
            lines.append(f"{assistant_label}: {content}")
            
    return "\n".join(lines)

def clear_context_if_off_topic(session_id: str, user_input: str, threshold: float = 0.4):
    """Optimierte Themen-Prüfung."""
    # Schnelle Regex-Prüfung zuerst
    if re.search(r"\b(ihn|ihm|seine|seiner|ihr|ihre|er|sie|es|damit|das|was|wann|wo|wie|warum)\b", user_input, re.IGNORECASE):
        return

    context = conversation_contexts.get(session_id, [])
    if not context:
        return
        
    # Nur die letzte Nachricht prüfen für bessere Performance
    if context:
        last_msg = context[-1]["content"]
        
        # Embedding-Berechnung optimiert
        with torch.no_grad():
            embeddings = embedding_model.encode([last_msg, user_input], convert_to_tensor=True)
            similarity = float(util.pytorch_cos_sim(embeddings[0], embeddings[1]))
            
        if similarity < threshold:
            conversation_contexts[session_id] = []
            print(f"Kontext zurückgesetzt (Ähnlichkeit: {similarity:.2f}).")

def post_process_answer(answer: str) -> str:
    """Optimierte Nachbearbeitung."""
    # Schnelle Regex-Operationen
    if not answer:
        return ""
    
    # Vereinfachte Patterns
    patterns_to_remove = [
        r"\?.*$",  # Alles nach dem ersten Fragezeichen
        r"\n[A-Z][^\.]*:",  # Dialog-Pattern
        r"Ich kann dir[^\.]*\.",
        r"I can help you[^\.]*\."
    ]
    
    for pattern in patterns_to_remove:
        answer = re.sub(pattern, "", answer, flags=re.IGNORECASE | re.MULTILINE)
    
    # Schnelle Satz-Trennung
    sentences = re.split(r'(?<=[.!?])\s+', answer)
    complete_sentences = [s.strip() for s in sentences if s.strip() and re.search(r'[.!?]$', s)]
    
    # Max. 3 Sätze für konzise Antworten
    result = ' '.join(complete_sentences[:3])
    return re.sub(r'\s+', ' ', result).strip()

@app.route("/", methods=["GET"])
def health_check():
    return jsonify({"status": "Server is running"})

@app.route("/", methods=["POST"])
def chat():
    data = request.json
    session_id = data.get("session_id", "default_user")
    user_input = data.get("input", "").strip()
    explain_flag = data.get("explain", False)

    # Erweiterte Cache-Strategie
    cached_key = f"{session_id}:{hash(user_input)}:{explain_flag}"
    cached_response = cache.get(cached_key)
    if cached_response:
        return jsonify(cached_response)

    # Prüfe, ob der Kontext thematisch passt
    clear_context_if_off_topic(session_id, user_input)

    # Parallele Übersetzung und Kontext-Aufbau
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as local_executor:
        # Übersetze die Nutzereingabe ins Englische
        translation_future = local_executor.submit(translate_to_english, user_input)
        
        # Baue Kontext auf
        context_str = build_context_string(session_id, in_english=True)
        
        # Warte auf Übersetzung
        translated_input = translation_future.result()

    # Verkürzter, optimierter Prompt
    prompt = (
        "Your task: Provide clear, direct answers for autistic users.\n\n"
        "Key guidelines:\n"
        "- Use literal, concrete language\n"
        "- Avoid metaphors and idioms\n"
        "- Give step-by-step explanations\n"
        "- Be precise and factual\n"
        "- Keep answers focused and concise\n\n"
        f"Context: {context_str}\n\n"
        f"Question: {translated_input}\n\n"
        "Your answer:"
    )

    try:
        # Parallele Antwortgenerierung und Post-Processing
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as local_executor:
            # Generiere Antwort mit reduzierter Token-Anzahl
            response_future = local_executor.submit(
                generate_response, 
                prompt, 
                temperature=0.2,
                max_tokens=200  # Reduziert für schnellere Generation
            )
            
            # Warte auf Antwort
            raw_response = response_future.result()
            
            # Post-Processing der englischen Antwort
            processed_en_response = post_process_answer(raw_response)
            
            # Übersetze zurück ins Deutsche
            german_response = translate_to_german(processed_en_response)
            
            # Finale Nachbearbeitung
            answer = post_process_answer(german_response)
        
    except Exception as e:
        return jsonify({"error": str(e)})

    # Kontext aktualisieren
    update_context(session_id, user_input, answer)

    explanation_text = ""
    if explain_flag:
        explanation_text = generate_explanation(answer, user_input)

    response_data = {"response": answer, "explanation": explanation_text}
    # Längeres Caching
    cache.set(cached_key, response_data, timeout=3600)

    return jsonify(response_data)

def generate_explanation(answer, user_input):
    """Optimierte Erklärungsgenerierung."""
    # Einfache Fallback-Erklärung für bessere Performance
    if len(answer) < 50:
        return "Kurze Antwort basierend auf Ihrer Frage."
    
    # Übersetze für die Erklärung ins Englische
    en_answer = translate_to_english(answer)
    en_input = translate_to_english(user_input)
    
    # Verkürzter Erklärungsprompt
    explanation_prompt = (
        "Explain briefly why this answer fits the question.\n\n"
        f"Question: {en_input}\n"
        f"Answer: {en_answer}\n\n"
        "Explanation:"
    )
    
    try:
        # Reduzierte Token-Anzahl für Erklärung
        raw_explanation = model.generate(
            explanation_prompt, 
            temp=0.2,
            max_tokens=100
        )
        
        processed_en_explanation = post_process_answer(raw_explanation)
        german_explanation = translate_to_german(processed_en_explanation)
        
        return post_process_answer(german_explanation)
        
    except Exception as e:
        return f"Erklärung nicht verfügbar: {str(e)}"

if __name__ == "__main__":
    # Optimierte Initialisierung
    load_model()
    with app.app_context():
        warm_up_model()
        # Pre-cache häufig verwendete Übersetzungen
        translate_to_english("Hallo")
        translate_to_german("Hello")
    
    # Produktionsserver-Konfiguration
    app.run(
        ssl_context=("cert.pem", "key.pem"), 
        host="0.0.0.0", 
        port=5000, 
        debug=False, 
        use_reloader=False,
        threaded=True  # Aktiviert Threading für Flask
    )