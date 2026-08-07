<div align="center">

# ⛽ GPL Campania

**App Android che trova i distributori GPL della Campania al prezzo più basso — senza server, senza account, senza API key.**

Tutto gira sul telefono: dati ufficiali del MIMIT, database locale, mappa OpenStreetMap.

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## Cos'è

GPL Campania mostra i prezzi del GPL delle **5 province campane** (Avellino, Benevento, Caserta, Napoli, Salerno) leggendoli dall'**Osservaprezzi carburanti del MIMIT** — gli stessi prezzi che i gestori sono obbligati a comunicare al ministero — li posiziona su mappa e ti dice quale distributore conviene davvero, considerando anche quanto è lontano.

L'ultima lettura della fonte ufficiale ha prodotto **426 distributori GPL, tutti con le coordinate ufficiali dell'impianto**.

## Caratteristiche

| | |
|---|---|
| 🔎 **Prezzi ufficiali** | quelli comunicati dai gestori al MIMIT, con la data reale di comunicazione |
| 🗺️ **Mappa OpenStreetMap** | via osmdroid, senza chiavi Google Maps |
| 📍 **Ordinamento per distanza** | dal fix GPS del device o da una posizione impostata a mano |
| ⭐ **Preferiti** | sopravvivono agli aggiornamenti, anche a un cambio di identificativi |
| 🏙️ **Filtro per comune** | costruito dinamicamente sui dati realmente presenti |
| 🧮 **Calcolatore** | quanto spendi per un pieno, confronto tra distributori |
| ✍️ **Segnalazione prezzo** | se trovi un prezzo diverso da quello pubblicato |
| ➕ **Aggiunta distributore** | con geocoding reale dell'indirizzo che inserisci |
| 📥 **Import POI** | file myLPG.eu in formato CSV o KML |
| ✈️ **Funziona offline** | mostra l'ultimo dato reale salvato, senza inventare nulla |

## Il principio che guida tutto: solo dati reali

Questo è il vincolo di progetto, non un dettaglio implementativo: **l'app non inventa mai un dato.**

- Le coordinate sono quelle **ufficiali dell'impianto**, non una stima. Se la fonte non le riporta, `latitude`/`longitude` restano `null`: la stazione resta in lista ma **sparisce dalla mappa**, senza mai ricevere un puntino plausibile-ma-finto né uno `0,0`.
- Se un distributore non comunica gli orari, il campo resta vuoto. Non viene riempito con un "8:00–20:00" verosimile.
- Se nessuna fonte risponde, il database **non viene toccato**: resta l'ultimo dato reale con un messaggio d'errore esplicito.

## Da dove arrivano i dati

L'app interroga due pubblicazioni dello stesso ministero, in cascata:

1. **API dell'Osservaprezzi** (`carburanti.mise.gov.it/ospzApi`) — una `POST` per provincia, ~172 KB compressi in tutto. È il backend del sito pubblico dell'Osservaprezzi.
2. **Open data MIMIT** (`mimit.gov.it/images/exportCSV`) — i due CSV ufficiali `prezzo_alle_8.csv` e `anagrafica_impianti_attivi.csv`, usati **solo se l'API non risponde**. Sono una pubblicazione documentata e stabile, ma coprono l'Italia intera (~7,5 MB, non comprimibili), quindi vengono letti in streaming trattenendo solo le righe GPL della Campania. Il `Last-Modified` viene rispedito come `If-Modified-Since`: se i prezzi non sono cambiati la risposta è un **304 da 0 byte** e Room resta com'è.

Se falliscono entrambe, l'app lo dice e conserva l'ultimo dato reale. Non esiste un terzo livello di fallback con dati finti.

### Perché non più lo scraping di alvolante.it

Fino alla v1.0.0 i prezzi venivano ricavati facendo scraping di 5 pagine HTML, che **non contengono le coordinate**: ogni indirizzo andava geocodificato via Nominatim, a ~1 richiesta al secondo. Il confronto, misurato su chiamate reali:

| | scraping HTML | fonte ufficiale MIMIT |
|---|---|---|
| distributori GPL in Campania | 419 | **426** (428 record, 2 doppioni uniti) |
| **con coordinate** | 395 (94,3%) | **426 (100%)** |
| richieste per refresh | 5 pagine + fino a ~400 Nominatim | **5** |
| dati sul filo | — | **172 KB** (gzip) |
| tempo per acquisire tutto | 2,42 s + geocoding in background | **2,33 s, completo** |

I 24 distributori che non comparivano sulla mappa non sono più un limite: erano indirizzi non geocodificabili, e ora la posizione arriva dalla fonte.

## Architettura: 100% on-device

Il progetto nasceva con un backend Python/FastAPI sul PC, raggiunto dal telefono via LAN. È stato **interamente eliminato**: acquisizione dati e geocoding girano sul device.

```
┌──────────────────────── ANDROID ─────────────────────────┐
│                                                          │
│   Compose UI  ◄──── StateFlow ──── GplViewModel          │
│                                          │               │
│                                    GplRepository         │
│                                    │            │        │
│                   ┌────────────────┘            └──────┐ │
│          CampaniaGplDataSource               GeocodingEngine
│            ├── OsservaprezziApiClient       (solo per i   │
│            └── MimitCsvFallback              distributori │
│                    │                         aggiunti a   │
│                    │                          mano)       │
│                    │        Room (SQLite)           │     │
│                    │   gpl_stations / price_reports │     │
│                    │        geocode_cache  ◄────────┘     │
└────────────────────┼───────────────────────────────────┬──┘
                     ▼                                   ▼
          carburanti.mise.gov.it              nominatim.openstreetmap.org
          mimit.gov.it (fallback)                   (geocoding)

                          tile.openstreetmap.org
                       (tile mappa, cache su disco)
```

Le **uniche** destinazioni di rete sono queste, tutte pubbliche e in HTTPS:

| host | quando |
|---|---|
| `carburanti.mise.gov.it` | a ogni refresh dei prezzi (5 richieste, TTL 15 minuti) |
| `mimit.gov.it` | solo se l'API non risponde (2 CSV, con `If-Modified-Since`) |
| `nominatim.openstreetmap.org` | **solo** per i distributori che aggiungi a mano |
| `tile.openstreetmap.org` | tile della mappa, con cache su disco in `cacheDir` |

C'è inoltre un intent verso Google Maps per avviare la navigazione, che non è una chiamata dati dell'app. Nessun localhost, nessun `adb reverse`, nessuna LAN, nessun server da avviare.

## Come funziona il refresh

Una sola fase, perché non c'è più niente da risolvere in background:

1. Le 5 province vengono richieste **in parallelo** (`async` + `awaitAll`): il costo è quello della più lenta, non la somma.
2. Si tengono i soli impianti con `fuelId == 4` (il GPL nel censimento carburanti). Il filtro è lato client: il server onora solo `province`.
3. Un impianto può pubblicare il GPL sia self sia servito: si tiene il prezzo più basso e, a parità, la modalità self.
4. Scrittura in Room → lista e mappa sono complete insieme.

I prezzi hanno un TTL di **15 minuti**: riaprire l'app prima non riscarica nulla.

### L'indirizzo, unico campo da scomporre

L'API restituisce l'indirizzo in un'unica stringa (`"Via delle Dune 5  81039 - VILLA LITERNO CE"`). `MimitAddressParser` prova due regex in ordine: con CAP, poi senza. Nella seconda la parte "via" è volutamente **greedy**, così lo split cade sull'ultimo `" - "` e non sul primo — senza questo accorgimento `"S.S. 7/IV - KM 2+650 - CELLOLE CE"` verrebbe troncato a metà del nome della strada.

Verificato sui 428 distributori GPL campani: **428 scomposti (100%)**, provincia sempre coerente con quella richiesta, comune uguale a quello dell'anagrafica ufficiale nel 99,1% dei casi. Nel fallback CSV il problema non si pone: comune, provincia e coordinate sono già colonne separate.

### Le pompe iscritte due volte

Capita che lo stesso distributore risulti attivo nell'anagrafica con due `idImpianto` diversi, tipicamente una re-iscrizione dopo un cambio di ragione sociale con la vecchia mai chiusa. In Campania succede due volte su 428: "Fratelli Longobardi" a Pompei (stesse coordinate al centimetro, `e C.` contro `& C.`) e la stazione Eni di via Pomigliano a Somma Vesuviana (11 m di distanza, una delle due ferma da un mese).

`mergeDuplicatePlants` le unisce con una regola volutamente stretta: **stesso comune e stessa via normalizzati _e_ distanza ≤ 50 m**. Il vincolo dell'indirizzo è la parte che protegge — a Mugnano di Napoli due civici della stessa via distano 15 m e sono impianti diversi, e restano due.

I dati dei due record non vengono mescolati: se ne **sceglie uno**, quello con la comunicazione di prezzo più recente (a parità, il prezzo più basso, poi l'iscrizione più recente). Quello che resta è sempre un record reale della fonte, mai un ibrido.

### I preferiti non si perdono

Gli id delle stazioni sono passati da `gpl_<provincia>_<hash>` (derivati dall'indirizzo scrappato) a `gpl_mimit_<idImpianto>` (l'identificativo nazionale dell'impianto, stabile e condiviso tra le due fonti). Perché un preferito salvato prima dell'aggiornamento non sparisse, il refresh riaccoppia le righe in cascata:

1. stesso id — il caso normale a regime, costo nullo;
2. comune + via normalizzati (sole lettere e cifre, per reggere punteggiatura e abbreviazioni diverse);
3. distanza inferiore a **150 m**, solo se entrambe le righe hanno coordinate reali.

Le segnalazioni di prezzo inserite dall'utente seguono la stazione sul nuovo id.

## Il geocoding, ora un dettaglio

`GeocodingEngine` resta nel progetto, ma **fuori dal percorso di refresh**: serve solo quando aggiungi a mano un distributore digitandone l'indirizzo. Prova fino a **7 strategie** su Nominatim, in ordine di precisione decrescente:

1. Ricerca strutturata con via + comune + provincia + CAP
2. Ricerca strutturata senza CAP
3. Testo libero sull'indirizzo ripulito
4. Testo libero sull'indirizzo originale
5. Ricerca strutturata senza iniziali puntate
6. Testo libero senza marcatori chilometrici e direzionali
7. Testo libero sul codice autostradale estratto (`A3`, `SS18`, …)

Se tutto fallisce, l'ultimo fallback è il **centro del comune**, marcato con `precision = "comune"` per essere onesti sulla sua approssimazione. La cache — comprese le voci "non trovato" — è permanente, così un indirizzo irrisolvibile non viene ritentato all'infinito.

## Costruire il progetto

Servono JDK 17+ e l'Android SDK (API 36). Il wrapper Gradle è incluso.

```bash
git clone https://github.com/mich-de/GPL_CAMPANIA.git
cd GPL_CAMPANIA
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Nessuna API key, nessun file di configurazione, nessun `.env`: il progetto compila appena clonato.

Per una build firmata, esporta le variabili d'ambiente e usa `assembleRelease`:

```bash
export KEYSTORE_PATH=/percorso/keystore.jks
export STORE_PASSWORD=...  KEY_ALIAS=upload  KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Senza keystore la configurazione di firma viene semplicemente saltata, così il progetto resta compilabile da chiunque.

### Test

```bash
./gradlew :app:testDebugUnitTest
```

42 test unitari (Robolectric) su: scomposizione degli indirizzi reali, filtro GPL sulle risposte reali dell'API, mappatura verso Room, unione delle pompe iscritte due volte, migrazione dei preferiti, import POI, deduplica e logica di filtro/ordinamento.

## Struttura

```
app/src/main/java/com/example/
├── data/
│   ├── remote/
│   │   ├── CampaniaGplDataSource.kt       orchestratore: API → CSV → errore
│   │   ├── OsservaprezziApiClient.kt      5 POST in parallelo, filtro GPL
│   │   ├── MimitCsvFallback.kt            CSV in streaming, If-Modified-Since
│   │   ├── MimitAddressParser.kt          scomposizione dell'indirizzo
│   │   ├── DuplicatePlantMerger.kt        unione delle pompe iscritte due volte
│   │   └── RemoteGplStation.kt            modello comune alle due fonti
│   ├── util/
│   │   └── GeoDistance.kt                 distanza in metri fra due coordinate
│   ├── geocoding/
│   │   ├── GeocodingEngine.kt             cascata a 7 tier + fallback comune
│   │   ├── NominatimClient.kt             OkHttp, rate limit 1,1 s, backoff su 429
│   │   ├── AddressCleaning.kt             normalizzazione indirizzi italiani
│   │   └── GeocodeDao.kt / GeocodeCacheEntity.kt
│   ├── local/       Room: GplDatabase, GplDao, BackendPreferences
│   ├── model/       GplStation, UserPriceReport
│   └── repository/  GplRepository — refresh, migrazione preferiti, import POI
└── ui/              Compose: HomeScreen, dialoghi, mappa, ViewModel
```

Convenzione sugli id, che determina cosa sopravvive a un refresh:

| prefisso | origine | cancellato dal refresh |
|---|---|---|
| `gpl_mimit_*` | fonte ufficiale MIMIT | sì, viene rigenerato |
| `user_*` | aggiunto dall'utente | **no** |
| `mylpg_*`, `ecomotori_*` | POI importati | **no** |

## Limiti noti

- **Serve Internet** per aggiornare. Senza connessione l'app mostra l'ultimo dato reale salvato: è indipendente dal PC, non dalla rete. Verificato sul device con entrambe le fonti irraggiungibili: le 426 righe in Room restano identiche byte per byte, compare il messaggio d'errore esplicito e il timestamp dell'ultimo aggiornamento **non** viene toccato, così il tentativo si ripete al riavvio invece di aspettare i 15 minuti di cache.
- **`ospzApi` non è un'API con termini d'uso pubblicati**: è il backend del sito dell'Osservaprezzi e può cambiare senza preavviso. È esattamente la ragione del fallback sugli open data, che sono invece una pubblicazione ufficiale e documentata.
- **Segnalazioni prezzo e distributori aggiunti sono locali al device**, non condivisi con altri utenti. Il backend centrale che li condivideva è stato rimosso.
- I prezzi sono quelli **comunicati dai gestori** al ministero, con la data di comunicazione mostrata in app. Un gestore che non aggiorna resta pubblicato con la sua data vecchia.
- L'app mostra **solo il GPL**, per scelta, anche se la fonte fornisce tutti i carburanti.

## Attribuzioni e rispetto delle policy

- **Prezzi e anagrafica impianti**: [Osservaprezzi carburanti](https://carburanti.mise.gov.it/) del **MIMIT** — Ministero delle Imprese e del Made in Italy. Gli open data sono pubblicati fra i [dataset del ministero](https://www.mimit.gov.it/it/open-data) (`images/exportCSV/prezzo_alle_8.csv` e `images/exportCSV/anagrafica_impianti_attivi.csv`). Consultati alla frequenza di un utente umano: TTL 15 minuti, 5 richieste per aggiornamento, `User-Agent` identificativo con URL del progetto.
- **Dati geografici**: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, [ODbL](https://opendatacommons.org/licenses/odbl/).
- **Geocoding**: [Nominatim](https://nominatim.org/). L'app rispetta la [usage policy](https://operations.osmfoundation.org/policies/nominatim/): massimo 1 richiesta/secondo, `User-Agent` identificativo con URL del progetto, cache permanente per non ripetere richieste, backoff esplicito sull'HTTP 429.
- **Tile della mappa**: server tile di OSM tramite osmdroid, secondo la [tile usage policy](https://operations.osmfoundation.org/policies/tiles/): `User-Agent` identificativo e cache su disco per non riscaricare gli stessi tile.

Se usi questo codice, mantieni il rate limiting. È la condizione che tiene Nominatim gratuito per tutti.

## Licenza

[MIT](LICENSE).
