# Toadstool

Android-App, in der du Aufgaben notierst und dir per Knopfdruck eine zufällige Aufgabe
ausspielen lässt, die zu deinem Ort und deiner verfügbaren Zeit passt.

## Installieren

1. Auf GitHub unter **Releases → „Produktivität – neueste Version“** die Datei
   `Produktivitaet.apk` auf dem Handy herunterladen.
2. Die Datei öffnen. Beim ersten Mal fragt Android, ob der Browser/„Eigene Dateien“
   Apps installieren darf – das erlauben.
3. Updates genauso installieren; deine Aufgaben bleiben dabei erhalten.

Die APK wird bei jedem Push automatisch von GitHub Actions gebaut
(`.github/workflows/build.yml`).

## Funktionen

- **Startseite:** großer roter Knopf „Produktivität“, darunter Ort (Zuhause, Öffis, Café, Stadt)
  und verfügbare Zeit in Minuten. Unten links die Übersicht, unten rechts das Plus.
- **Auslosen:** Es kommen nur Aufgaben in Frage, deren (aktueller) Schritt höchstens so lange
  dauert wie die angegebene Zeit und am gewählten Ort machbar ist. Passt nichts, heißt es:
  „Es gibt keine Aufgaben, die du im Moment machen kannst. Entspann dich.“
- **Aufgabenfenster:** Erledigt / Wiederholen / Verwerfen. Erledigt und Verwerfen entfernen
  den aktuellen Schritt (bzw. die Aufgabe), Wiederholen lässt alles, wie es ist.
- **Schritte:** Eine Aufgabe kann mehrere Schritte mit eigener Dauer und eigenen Orten haben.
  Ausgelost wird immer nur der aktuelle Schritt; ist er erledigt, rückt der nächste nach.
- **Hinzufügen:** neue Aufgabe erstellen oder eine Vorlage als Ausgangspunkt nehmen.
  „Als Vorlage speichern“ legt die Aufgabe zusätzlich als Vorlage ab.
- **Übersicht:** Reiter „Aufgaben“ und „Vorlagen“, jeweils bearbeiten und löschen.
  Noch ausstehende Schritte sind ausgegraut.

## Technik

Kotlin + Jetpack Compose, Daten liegen als JSON im App-Speicher (`daten.json`).
Der Signaturschlüssel `app/produktivitaet.jks` liegt bewusst im Repository, damit jede
gebaute Version als Update über die vorherige installiert werden kann.

## Sprites (Pilze und Pilz-Wesen)

Alle Sprites liegen im Ordner `app/src/main/assets/sprites/` (PNG mit transparentem Hintergrund).

- **Pilze:** `Name_Lateinischer Name.png`, z. B. `Fliegenpilz_Amanita muscaria.png`
- **Pilz-Wesen:** `Name.png` – ohne Unterstrich
- Durchsichtige Ränder werden automatisch abgeschnitten; die Größe richtet sich nach der längsten Seite
- Ideal: höchstens ca. 1500 px an der längsten Seite
- Bereits erspielte Pilze einer entfernten Art bekommen beim nächsten Start zufällig eine vorhandene Art
