import gpt4all
from flask import Flask, request, jsonify
from flask_talisman import Talisman
from flask_caching import Cache

app = Flask(__name__)
Talisman(app)  # Aktiviert HTTPS und fügt Sicherheits-Header hinzu

# Flask-Caching konfigurieren (Simple Cache speichert Daten im RAM)
app.config['CACHE_TYPE'] = 'simple'
cache = Cache(app)

# Lade das Modell einmalig in den Speicher (Caching des Modells)
model_path = "D:/Programme/gpt4all/Llama-3.2-3B-Instruct-Q4_0.gguf"
model = None  # Wird später initialisiert

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
    data = request.json
    user_input = data.get("input", "").strip()

    # Überprüfe, ob es bereits eine zwischengespeicherte Antwort gibt.
    cached_response = cache.get(user_input)
    if cached_response:
        return jsonify({"response": cached_response})

    prompt = (
        f"Antworte klar und direkt auf die Frage: {user_input}. "
        "Verwende keine komplizierten Begriffe. "
        "Wenn eine Erklärung notwendig ist, halte sie kurz und einfach."
    )
    
    try:
        raw_response = model.generate(
            prompt,
            max_tokens=50,  # Begrenze die maximale Länge
            top_p=0.8,      # Verwende nur die wahrscheinlichsten Tokens
        )
    except Exception as e:
        return jsonify({"error": str(e)})
    
    response = process_response(raw_response)
    
    # Speichere die Antwort im Cache (z.B. für 5 Minuten)
    cache.set(user_input, response, timeout=300)
    
    return jsonify({"response": response})

def process_response(response):
    """Kürzt die Antwort auf den ersten vollständigen Satz und entfernt irrelevante Ergänzungen."""
    response = response.strip()
    first_sentence = response.split(".")[0] + "."
    
    unhelpful_phrases = [
        "Ich kann also die Frage richtig beantworten",
        "Daher lautet meine Antwort",
        "Als KI-Modell"
    ]
    for phrase in unhelpful_phrases:
        first_sentence = first_sentence.replace(phrase, "")
    
    return first_sentence.strip()

if __name__ == "__main__":
    load_model()  # Modell vor dem Serverstart laden
    with app.app_context():
        warm_up_model()  # Pre-Warming des Modells
    app.run(ssl_context=("cert.pem", "key.pem"), host="0.0.0.0", port=5000, debug=True)
