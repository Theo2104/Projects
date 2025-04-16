import plotly.graph_objects as go
import numpy as np
import qrcode
from PIL import Image

# Dimensionen und Szenarien definieren
dimensionen = [
    "Direktheit", "Metaphernverwendung", "Satzklarheit", "Begriffskonsistenz", "Faktendichte",
    "Logische Struktur", "Hierarchie", "Visuelle Struktur", "Textdichte",
    "Formatkonsistenz", "Granularität", "Vollständigkeit", "Schrittklarheit",
    "Beispiele", "Mentaler Aufwand", "Verarbeitungszeit", "Terminologie", "Anpassungsfähigkeit",
    "Kommunikationsstil"
]

szenarien = [
    "Szenario 1 (Photosynthese)",
    "Szenario 2 (Toaster)",
    "Szenario 3 (Arzt)",
    "Szenario 4 (Computer)",
    "Szenario 5 (Überforderung)",
    "Szenario 6 (Dialog)"
]

# Daten für die Heatmap
daten = np.array([
    #Szenario 1 (Photosynthese)
    [3, 5, 4, 3, 4, 3, 4, 2, 3, 5, 2, 3, 3, 1, 4, 3, 3, 2, 4],
    # Szenario 2 (Toaster)
    [3, 4, 2, 4, 4, 4, 4, 2, 4, 5, 3, 2, 3, 1, 4, 3, 3, 1, 4],
    # Szenario 3 (Arzt)
    [5, 5, 4, 4, 4, 2, 4, 3, 4, 5, 5, 4, 3, 3, 3, 3, 2, 2, 2],
    # Szenario 4 (Computer)
    [5, 5, 5, 3, 5, 4, 4, 4, 4, 4, 4, 3, 4, 1, 4, 2, 3, 1, 4],
    # Szenario 5 (Überforderung)
    [5, 5, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 1, 5, 4, 3, 2, 4],
    # Szenario 6 (Dialog)
    [4, 5, 4, 5, 4, 5, 5, 5, 4, 5, 5, 5, 5, 3, 4, 4, 3, 3, 3]
])


daten_angepasst = daten[:, :len(dimensionen)]
daten_transponiert = daten_angepasst.T

# Heatmap erstellen
fig = go.Figure(data=go.Heatmap(
    z=daten_transponiert,
    x=szenarien,
    y=dimensionen,
    colorscale=[
        [0.0, 'rgb(255,180,180)'],  # Hell-Rot für Wert 1
        [0.25, 'rgb(255,220,180)'], # Hell-Orange für Wert 2
        [0.5, 'rgb(255,255,180)'],  # Hell-Gelb für Wert 3
        [0.75, 'rgb(180,255,180)'], # Hell-Grün für Wert 4
        [1.0, 'rgb(20,180,20)']     # Dunkel-Grün für Wert 5
    ],
    colorbar=dict(
        title="Bewertung",
        tickvals=[1, 2, 3, 4, 5],
        ticktext=["1", "2", "3", "4", "5"]
    ),
    hoverongaps=False,
    text=daten_transponiert,
    texttemplate="%{text}",
    showscale=True
))

# Layout anpassen
fig.update_layout(
    title="Cognitive Walkthrough Evaluation - Heatmap",
    xaxis=dict(
        title="Szenarien",
        tickfont=dict(size=12)
    ),
    yaxis=dict(
        title="Bewertungsdimensionen",
        tickfont=dict(size=10)
    ),
    height=800, 
    width=1000,
    margin=dict(l=200, r=50, b=100, t=80)
)

# HTML-Datei exportieren
html_filename = "evaluation_heatmap.html"
fig.write_html(html_filename, include_plotlyjs='cdn')
print(f"✅ HTML gespeichert als {html_filename}")

# Statisches Bild speichern (für PDF-Einbettung)
fig.write_image("evaluation_heatmap.png", width=1200, height=800)
print("✅ Statisches PNG gespeichert für LaTeX oder Bericht")

# Optional: QR-Code für Webhosting erzeugen
qr_link = "https://evaluation-heatmap.netlify.app"  # Hier den Link zu Ihrer gehosteten Version einfügen
qr_img = qrcode.make(qr_link)
qr_img_path = "qr_evaluation_heatmap.png"
qr_img.save(qr_img_path)
print(f"✅ QR-Code gespeichert als {qr_img_path}")


fig.show()