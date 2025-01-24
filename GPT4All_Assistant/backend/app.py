import gpt4all
from flask import Flask, request, jsonify
from flask_talisman import Talisman

app = Flask(__name__)
Talisman(app)  # Aktiviert HTTPS und fügt Sicherheits-Header hinzu

# Lade das Modell
model_path = "D:/Programme/gpt4all/Llama-3.2-3B-Instruct-Q4_0.gguf"
model = gpt4all.GPT4All(model_path)

@app.route("/", methods=["GET"])
def health_check():
    return jsonify({"status": "Server is running"})

@app.route("/", methods=["POST"])
def chat():
    data = request.json
    user_input = data.get("input", "").strip()
    

    prompt = (
        f"Antworte klar und direkt auf die Frage: {user_input}. "
        "Verwende keine komplizierten Begriffe. "
        "Wenn eine Erklärung notwendig ist, halte sie kurz und einfach."
    )
    
  
    try:
        raw_response = model.generate(
            prompt,
            max_tokens=50,  # Begrenze die maximale Länge
            top_p=0.8,  # Begrenze auf die wahrscheinlichsten Tokens
        )
    except Exception as e:
        return jsonify({"error": str(e)})
    
  
    response = process_response(raw_response)
    
    return jsonify({"response": response})

def process_response(response):
    """
    Kürzt die Antwort auf den ersten vollständigen Satz und entfernt irrelevante Ergänzungen.
    """
    response = response.strip()  # Entferne unnötige Leerzeichen
    # Nur den ersten Satz zurückgeben
    first_sentence = response.split(".")[0] + "."
    
    # Entferne bekannte irrelevante Floskeln oder unnötige Ergänzungen
    unhelpful_phrases = [
        "Ich kann also die Frage richtig beantworten",
        "Daher lautet meine Antwort",
        "Als KI-Modell"
    ]
    for phrase in unhelpful_phrases:
        first_sentence = first_sentence.replace(phrase, "")
    
    return first_sentence.strip()

if __name__ == "__main__":
    app.run(ssl_context=("cert.pem", "key.pem"), host="0.0.0.0", port=5000, debug=True)
