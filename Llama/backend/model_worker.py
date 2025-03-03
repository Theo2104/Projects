"""
Separater Model-Worker Prozess für die Ausführung des GPT4All-Modells.
Dieser Worker läuft in einem eigenen Prozess, um Speicherzugriffsprobleme zu isolieren.
"""

import os
import sys
import time
import logging
import traceback
import multiprocessing
from gpt4all import GPT4All

# Konfiguriere Logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("model_worker.log"),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger('model-worker')

def model_worker(model_path, request_queue, response_queue):
    """
    Führt das Sprachmodell in einem separaten Prozess aus.
    
    Args:
        model_path: Pfad zur Modelldatei
        request_queue: Queue für eingehende Anfragen
        response_queue: Queue für ausgehende Antworten
    """
    try:
        logger.info(f"Model-Worker-Prozess gestartet (PID: {os.getpid()})")
        
        # Stelle sicher, dass das Modell existiert
        if not os.path.exists(model_path):
            logger.error(f"KRITISCHER FEHLER: Modell nicht gefunden unter: {model_path}")
            response_queue.put(("ERROR", "Modelldatei nicht gefunden"))
            return
        
        # Lade das Modell
        try:
            logger.info(f"Lade Modell aus: {model_path}")
            model = GPT4All(model_path, allow_download=False)
            logger.info("Modell erfolgreich geladen")
            
            # Modell aufwärmen
            logger.info("Wärme das Modell auf...")
            _ = model.generate("Test", max_tokens=5)
            logger.info("Modell erfolgreich aufgewärmt")
            
        except Exception as e:
            logger.error(f"Fehler beim Laden des Modells: {e}")
            logger.error(traceback.format_exc())
            response_queue.put(("ERROR", f"Fehler beim Laden des Modells: {str(e)}"))
            return
        
        # Hauptschleife für die Anfrageverarbeitung
        logger.info("Worker bereit für Anfragen")
        while True:
            try:
                # Warte auf eine Anfrage
                request_id, prompt = request_queue.get()
                
                # Prüfe auf Beendigungssignal
                if request_id == "TERMINATE":
                    logger.info("Beendigungssignal erhalten, beende Worker...")
                    break
                
                logger.info(f"Verarbeite Anfrage: {request_id[:8]}...")
                
                try:
                    # Generiere die Antwort
                    raw_response = model.generate(
                        prompt,
                        max_tokens=50,
                        top_p=0.7,
                        temp=0.7,
                        repeat_penalty=1.2,
                        repeat_last_n=64
                    )
                    
                    # Sende die Antwort zurück
                    response_queue.put((request_id, raw_response))
                    logger.info(f"Anfrage {request_id[:8]} erfolgreich verarbeitet")
                    
                except Exception as gen_err:
                    logger.error(f"Fehler bei der Generierung für {request_id[:8]}: {gen_err}")
                    logger.error(traceback.format_exc())
                    response_queue.put((request_id, f"ERROR: {str(gen_err)}"))
                
            except Exception as loop_err:
                logger.error(f"Fehler in der Worker-Hauptschleife: {loop_err}")
                logger.error(traceback.format_exc())
            
    except Exception as e:
        logger.error(f"Kritischer Fehler im Worker-Prozess: {e}")
        logger.error(traceback.format_exc())
        
        try:
            response_queue.put(("ERROR", f"Kritischer Worker-Fehler: {str(e)}"))
        except:
            pass
    
    finally:
        logger.info("Worker-Prozess wird beendet")

if __name__ == "__main__":
    """
    Direkter Start des Workers als separater Prozess.
    Argumente: model_path
    """
    if len(sys.argv) < 2:
        print("Verwendung: python model_worker.py <model_path>")
        sys.exit(1)
    
    model_path = sys.argv[1]
    
    # Erstelle Kommunikationsqueues
    request_queue = multiprocessing.Queue()
    response_queue = multiprocessing.Queue()
    
    # Starte den Worker
    model_worker(model_path, request_queue, response_queue)
