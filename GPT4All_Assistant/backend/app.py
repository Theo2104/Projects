import gpt4all
from flask import Flask, request, jsonify

app = Flask(__name__)

# Lade das Modell
model_path = "D:/Programme/gpt4all/Llama-3.2-3B-Instruct-Q4_0.gguf"
model = gpt4all.GPT4All(model_path)

@app.route("/", methods=["POST"])
def chat():
    data = request.json
    user_input = data.get("input", "")
    response = model.generate(user_input)
    return jsonify({"response": response})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
