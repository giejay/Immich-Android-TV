# Privacy Policy / Datenschutzerklärung

Immich TV Next (Unofficial) · Application id `io.github.daniii88.immichandroidtvnext`
Last updated / Stand: 2026-11-09

## English

**Immich TV Next** is an unofficial community client for the [Immich](https://immich.app) photo server on Android TV. It is not made, endorsed or operated by the Immich project.

**Who is responsible.** The developer, daniii88 (contact: <https://github.com/daniii88/Immich-Android-TV-Next/issues>). The developer operates no server for this app (the optional phone sign-in runs locally, see below).

**What the app does with data.**
- The app connects to the Immich server that you enter. Your photos, videos, albums, people and metadata are loaded from that server and shown on your TV. They go nowhere else.
- **Phone sign-in.** If you sign in with your phone instead of typing the API key, the TV starts a small web server in your home network for as long as the QR screen is open (at most three minutes). You scan the QR code (or open the address) with your phone and enter your server URL and API key on a page served by the TV itself. The page is reachable only with a random one-time token, the data goes directly from your phone to your TV within your local network, and nothing is sent to the internet or to any third party. Because the connection is plain HTTP, other devices on the same network could in theory read the key in transit; use this only in a network you trust, or enter the data on the TV directly.
- **Demo mode.** The demo mode shows sample photos that are bundled with the app. It makes no network connection at all.
- The server URL, your settings and the API key are stored on your device only. The API key is encrypted with a key held in the Android Keystore. If your server sits behind a reverse proxy that asks for HTTP basic authentication, you can enter a proxy user and password; the password is stored and encrypted the same way and is only sent to your server.
- Images are cached on the device to speed up browsing. Clearing the app data removes the cache and all settings.
- If you enable home screen channels, the app writes entries (titles and image links from your server) to the Android TV home screen on your device.

**What the app does not do.** No analytics, no tracking, no advertising, no advertising id, no crash-reporting service, no third-party SDKs that collect data. The developer receives no data from your use of the app. Nothing is sent to third parties.

**Network security.** Your own server may be reachable through plain HTTP, a self-signed or private certificate, or with certificate checks turned off in the settings. These options exist for home servers; you are responsible for securing your network. HTTPS with a valid certificate is recommended.

**Backups.** The server URL, API key and settings are excluded from Android backups.

**Your rights.** Because the developer holds no data about you, there is nothing to access, correct or delete on the developer's side. Your data lives on your own Immich server and TV; remove it by clearing the app data or uninstalling the app. Questions can be sent through the contact above.

**Changes.** Changes to this policy are published in this file in the project repository.

## Deutsch

**Immich TV Next** ist ein inoffizieller Community-Client für den [Immich](https://immich.app)-Fotoserver auf Android TV. Die App wird nicht vom Immich-Projekt entwickelt, empfohlen oder betrieben.

**Verantwortlich.** Der Entwickler, daniii88 (Kontakt: <https://github.com/daniii88/Immich-Android-TV-Next/issues>). Der Entwickler betreibt keinen Server für diese App (die optionale Anmeldung per Smartphone läuft lokal, siehe unten).

**Was die App mit Daten macht.**
- Die App verbindet sich mit dem Immich-Server, den du angibst. Fotos, Videos, Alben, Personen und Metadaten werden von diesem Server geladen und auf deinem Fernseher angezeigt. Sie gehen nirgendwo anders hin.
- **Anmeldung per Smartphone.** Meldest du dich per Smartphone statt mit manueller Eingabe an, startet der Fernseher so lange der QR-Bildschirm offen ist (höchstens drei Minuten) einen kleinen Webserver in deinem Heimnetz. Du scannst den QR-Code (oder öffnest die Adresse) mit dem Smartphone und gibst Server-URL und API-Key auf einer Seite ein, die der Fernseher selbst ausliefert. Die Seite ist nur mit einem zufälligen Einmal-Token erreichbar, die Daten gehen direkt vom Smartphone zum Fernseher in deinem lokalen Netz und nichts wird ins Internet oder an Dritte gesendet. Da die Verbindung unverschlüsseltes HTTP ist, könnten andere Geräte im selben Netz den Schlüssel theoretisch mitlesen; nutze das nur in einem Netz, dem du vertraust, oder gib die Daten direkt am Fernseher ein.
- **Demo-Modus.** Der Demo-Modus zeigt Beispielfotos, die in der App mitgeliefert werden. Er baut keine Netzwerkverbindung auf.
- Server-URL, Einstellungen und API-Key werden nur auf deinem Gerät gespeichert. Der API-Key wird mit einem Schlüssel aus dem Android Keystore verschlüsselt. Steht dein Server hinter einem Reverse-Proxy mit HTTP-Basic-Authentifizierung, kannst du Proxy-Benutzer und -Passwort eingeben; das Passwort wird genauso gespeichert und verschlüsselt und nur an deinen Server gesendet.
- Bilder werden zur Beschleunigung auf dem Gerät zwischengespeichert. Das Löschen der App-Daten entfernt den Cache und alle Einstellungen.
- Wenn du Startbildschirm-Kanäle aktivierst, schreibt die App Einträge (Titel und Bildlinks von deinem Server) auf den Android-TV-Startbildschirm deines Geräts.

**Was die App nicht macht.** Keine Analyse, kein Tracking, keine Werbung, keine Werbe-ID, kein Absturzberichtsdienst und keine Drittanbieter-SDKs, die Daten sammeln. Der Entwickler erhält keine Daten aus deiner Nutzung. Es werden keine Daten an Dritte gesendet.

**Netzwerksicherheit.** Dein Server kann über unverschlüsseltes HTTP, ein selbstsigniertes oder privates Zertifikat oder mit abgeschalteter Zertifikatsprüfung (Einstellung) erreichbar sein. Diese Optionen sind für Heimserver gedacht; für die Sicherheit deines Netzwerks bist du selbst verantwortlich. Empfohlen ist HTTPS mit gültigem Zertifikat.

**Backups.** Server-URL, API-Key und Einstellungen sind von Android-Backups ausgeschlossen.

**Deine Rechte.** Da der Entwickler keine Daten über dich speichert, gibt es dort nichts auszukunften, zu berichtigen oder zu löschen. Deine Daten liegen auf deinem eigenen Immich-Server und Fernseher; du entfernst sie, indem du die App-Daten löschst oder die App deinstallierst. Fragen kannst du über den obigen Kontakt stellen.

**Änderungen.** Änderungen dieser Erklärung werden in dieser Datei im Projekt-Repository veröffentlicht.
