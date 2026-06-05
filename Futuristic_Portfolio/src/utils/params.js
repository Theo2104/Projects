// ------------------------------------------------------------------
//  Liest Personalisierungs-Parameter aus der URL (Pro-Firma-Links).
//
//  Beispiel-Link für eine Bewerbung:
//    ?for=Acme&highlight=matrix,commerce&tour=1
//
//  - for:        Firmenname für die persönliche Begrüßung
//  - highlight:  Komma-Liste von Projekt-IDs, die hervorgehoben und
//                im Menü/der Tour zuerst gezeigt werden
//  - tour|autoplay: startet die geführte Tour automatisch
// ------------------------------------------------------------------
export function getParams() {
  if (typeof window === 'undefined') {
    return { company: null, highlight: [], autoplay: false }
  }
  const sp = new URLSearchParams(window.location.search)
  const company = (sp.get('for') || '').trim() || null
  const highlight = (sp.get('highlight') || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
  const autoplay = sp.has('tour') || sp.has('autoplay')
  return { company, highlight, autoplay }
}
