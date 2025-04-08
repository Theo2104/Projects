import plotly.graph_objects as go
import qrcode
from PIL import Image

kriterien = [
    "Wörtliche Sprache", "Satzstruktur", "Präzision", "Kognitive Belastung",
    "Modus-Effektivität", "Prompt-Effektivität", "Informationsdichte",
    "Satzlänge", "Antwortlänge", "Visuelle Struktur",
    "Praxisrelevanz", "Vorhersagbarkeit", "Detailtiefe"
]

scores_precise = [5, 5, 5, 4, 5, 5, 4, 5, 5, 5, 5, 4, 4]
scores_default = [5, 4, 3, 3, 4, 2, 2, 4, 4, 1, 3, 3, 3]
scores_detailed = [5, 3, 4, 3, 4, 4, 3, 3, 5, 3, 4, 3, 4]

# === Radar Chart erstellen ===
fig = go.Figure()

fig.add_trace(go.Scatterpolar(
    r=scores_precise,
    theta=kriterien,
    fill='toself',
    name='Precise'
))
fig.add_trace(go.Scatterpolar(
    r=scores_default,
    theta=kriterien,
    fill='toself',
    name='Default'
))
fig.add_trace(go.Scatterpolar(
    r=scores_detailed,
    theta=kriterien,
    fill='toself',
    name='Detailed'
))

fig.update_layout(
    polar=dict(radialaxis=dict(visible=True, range=[0, 5])),
    showlegend=True,
    title='Szenario 5 – Vergleich der Modi'
)

# === HTML-Datei exportieren ===
html_filename = "szenario_5_radar.html"
fig.write_html(html_filename)
print(f"✅ HTML gespeichert als {html_filename}")

# === QR-Code erzeugen ===
qr_link = "https://scenario5-radar.netlify.app"
qr_img = qrcode.make(qr_link)
qr_img_path = "qr_szenario_5.png"
qr_img.save(qr_img_path)
print(f"✅ QR-Code gespeichert als {qr_img_path}")

# Optional: Statisches Bild speichern (für PDF-Einbettung)
fig.write_image("szenario_5_radar.png")
print("✅ Statisches PNG gespeichert für LaTeX oder Bericht")