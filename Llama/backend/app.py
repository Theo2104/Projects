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
conversation_context = ""

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
    global conversation_context
    data = request.json
    user_input = data.get("input", "").strip()
    explain_flag = data.get("explain", False)  # Optionaler Parameter, um xAI zu aktivieren

    # Optional: Prüfe, ob es bereits eine zwischengespeicherte Antwort gibt
    cached_response = cache.get(user_input)
    if cached_response:
        # Falls xAI gewünscht wird, generiere Erklärung auch bei gecachten Antworten
        explanation = ""
        if explain_flag:
            explanation = generate_explanation(cached_response)
        return jsonify({"response": cached_response, "explanation": explanation})

    # Erstelle einen Prompt, der alle gewünschten Anforderungen enthält.
    prompt = (
        "Du bist ein sprachgesteuerter Assistent für autistische Nutzer, der immer in klaren, kurzen und einfachen Sätzen antwortet. "
        "Vermeide Mehrdeutigkeiten, indirekte Formulierungen, komplizierte Metaphern und Redewendungen. "
        "Erkenne den Kontext hinter den Aussagen und reagiere angemessen. "
        "Filtere automatisch irrelevante Informationen heraus und liefere nur die für den Nutzer relevanten Inhalte.\n\n"
        "Gesprächskontext:\n" + conversation_context + "\n\n"
        "Frage: " + user_input
    )

    try:
        raw_response = model.generate(
            prompt,
            max_tokens=50,  # Maximale Länge für die Antwort
            top_p=0.8,
        )
    except Exception as e:
        return jsonify({"error": str(e)})

    response = process_response(raw_response)
    
    # Aktualisiere den Gesprächskontext: füge die Nutzerfrage und die Antwort hinzu.
    conversation_context += "\nNutzer: " + user_input + "\nAssistent: " + response
    
    # Speichere die Antwort im Cache (z.B. für 5 Minuten)
    cache.set(user_input, response, timeout=300)
    
    # Falls gewünscht, generiere zusätzlich eine Erklärung (xAI-Funktion)
    explanation = ""
    if explain_flag:
        explanation = generate_explanation(response)
    
    return jsonify({"response": response, "explanation": explanation})

def process_response(response):
    """Kürzt die Antwort auf den ersten vollständigen Satz und entfernt unerwünschte Präfixe und irrelevante Ergänzungen."""
    response = response.strip()
    # Entferne ein isoliertes 'antwort:' oder fehlerhafte Varianten am Anfang (ohne 'Assistent Antwort:' des Frontends zu beeinflussen)
    for prefix in ["antwort:", "antowrt:"]:
        if response.lower().startswith(prefix):
            response = response[len(prefix):].strip()
    first_sentence = response.split(".")[0] + "."
    unhelpful_phrases = [
        "Ich kann also die Frage richtig beantworten",
        "Daher lautet meine Antwort",
        "Als KI-Modell"
    ]
    for phrase in unhelpful_phrases:
        first_sentence = first_sentence.replace(phrase, "")
    return first_sentence.strip()

def generate_explanation(answer):
    """Generiert eine Erklärung für die gegebene Antwort mittels xAI-Mechanismen."""
    explanation_prompt = (
        "Erkläre in kurzen, einfachen Sätzen, welche Entscheidungsprozesse zu der folgenden Antwort geführt haben. "
        "Nutze klare, nachvollziehbare Sprache und beschränke dich auf das Wesentliche.\n\n"
        "Antwort: " + answer
    )
    try:
        raw_explanation = model.generate(
            explanation_prompt,
            max_tokens=100,
            top_p=0.8,
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
