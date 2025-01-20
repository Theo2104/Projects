import gpt4all

model_path = "D:/Programme/gpt4all/Llama-3.2-3B-Instruct-Q4_0.gguf"

try:
    model = gpt4all.GPT4All(model_path)
    response = model.generate("Hello, how are you?")
    print("Response:", response)
except Exception as e:
    print("Error:", str(e))