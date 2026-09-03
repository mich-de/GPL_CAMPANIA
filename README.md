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
| 🟢 **Pin colorati per fascia** | verde/blu/rosso ricavati dai prezzi realmente in lista, non da soglie fisse |
| 📍 **Ordinamento per distanza** | dal fix GPS del device o da una posizione impostata a mano |
| ⭐ **Preferiti** | sopravvivono agli aggiornamenti, anche a un cambio di identificativi |
| 🏙️ **Filtro per comune** | costruito dinamicamente sui dati realmente presenti |
| 🧮 **Calcolatore** | quanto spendi per un pieno, confronto tra distributori |
| ✍️ **Segnalazione prezzo** | se trovi un prezzo diverso da quello pubblicato |
| ➕ **Aggiunta distributore** | con geocoding reale dell'indirizzo che inserisci |
| 📥 **Import POI** | file myLPG.eu in formato CSV o KML |
| 🇮🇹 **GPL in Italia** | medie nazionali e classifica regionale calcolate dagli open data |
| 📰 **Notizie carburanti** | dalla sala stampa del MIMIT, filtrate per argomento |
| ⏳ **Scadenza serbatoio** | conto alla rovescia dalla data del tuo libretto, tutto sul device |
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

Non c'è nessun server da tenere acceso: acquisizione dati e geocoding girano sul telefono.

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

C'è inoltre un intent verso Google Maps per avviare la navigazione, che non è una chiamata dati dell'app.

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

## La mappa

**A tutto schermo.** In modalità mappa spariscono barra del titolo, ricerca, filtri e i due FAB:
occupavano circa un terzo dello schermo, e i FAB stavano nell'angolo in basso a destra, proprio dove
finisce il pollice quando si pizzica per ingrandire. La mappa disegna anche sotto le barre di
sistema; i suoi comandi restano dentro i margini di sistema per non finirci sotto. Si torna
all'elenco con il pulsante in alto a sinistra o con il tasto indietro (che, se una scheda è aperta,
prima chiude quella).

Tile di OpenStreetMap tramite osmdroid, scalate alla densità reale dello schermo (senza, su un
display 3×, le etichette delle strade risultano illeggibili). Ogni distributore con coordinate è un
pin; l'attribuzione OSM e la barra della scala sono disegnate sulla mappa, come richiede la tile
usage policy, alzate quanto basta a non finire sotto la barra di navigazione.

**Il colore del pin dice una cosa sola, e verificabile.** Verde, blu e rosso sono il terzo più
economico, quello centrale e quello più caro **dei distributori attualmente in lista** — filtri
compresi. Le soglie non sono scritte nel codice: sono i terzili dei prezzi reali, e la legenda in
alto a sinistra mostra i due valori esatti in €/L, così il colore si può controllare a occhio. Una
soglia fissa avrebbe un'unica evoluzione possibile: dopo qualche mese di rincari dipinge di rosso
tutta la regione senza che nulla sia davvero cambiato. Sotto i **6 prezzi** — o quando sono tutti
uguali — non viene dichiarata nessuna fascia: i pin restano grigi e la legenda lo dice, perché
"il terzo più economico" fra tre distributori è una statistica inventata.

Toccando un pin si apre la **scheda in fondo alla mappa** (marca, indirizzo, distanza reale,
prezzo con la sua fascia), non la finestra di dettaglio: quella copriva la mappa appena toccata e
rendeva la scheda irraggiungibile. Da lì si sceglie *Indicazioni* o *Dettagli*; un tocco sulla
mappa vuota toglie l'evidenza.

La camera è dell'utente. La mappa si ricentra **una volta sola**, quando arriva la prima posizione
reale (il fix GPS è asincrono, la mappa nasce prima sull'ultimo punto noto). Da lì in poi ogni
aggiornamento GPS sposta solo il pallino blu: il tasto dedicato resta il modo per tornare sulla
propria posizione, e ⛶ inquadra tutti i distributori mostrati.

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

100 test unitari (Robolectric) su: scomposizione degli indirizzi reali, filtro GPL sulle risposte reali dell'API, mappatura verso Room, unione delle pompe iscritte due volte, migrazione dei preferiti, import POI, deduplica, logica di filtro/ordinamento, freschezza dei prezzi comunicati, parametri di diagnostica, tabella province → regioni, aggregazione nazionale, andamento sullo storico, parsing del feed RSS con il suo filtro per argomento, conti sulla scadenza del serbatoio e soglie di colore dei pin sulla mappa.

## Struttura

```
app/src/main/java/com/example/
├── data/
│   ├── remote/
│   │   ├── CampaniaGplDataSource.kt       orchestratore: API → CSV → errore
│   │   ├── OsservaprezziApiClient.kt      5 POST in parallelo, filtro GPL
│   │   ├── MimitCsvFallback.kt            CSV in streaming, If-Modified-Since
│   │   ├── MimitCsvRows.kt                lettura riga per riga, condivisa dai due usi del CSV
│   │   ├── MimitAddressParser.kt          scomposizione dell'indirizzo
│   │   ├── DuplicatePlantMerger.kt        unione delle pompe iscritte due volte
│   │   ├── PriceFreshness.kt              da quanti giorni i prezzi sono stati comunicati
│   │   ├── NationalGplStats.kt            medie nazionali e classifica regionale
│   │   ├── ItalianRegions.kt              107 sigle di provincia → 20 regioni
│   │   ├── MimitNewsFeed.kt               RSS della sala stampa, filtro per argomento
│   │   └── RemoteGplStation.kt            modello comune alle due fonti
│   ├── util/
│   │   └── GeoDistance.kt                 distanza in metri fra due coordinate
│   ├── geocoding/
│   │   ├── GeocodingEngine.kt             cascata a 7 tier + fallback comune
│   │   ├── NominatimClient.kt             OkHttp, rate limit 1,1 s, backoff su 429
│   │   ├── AddressCleaning.kt             normalizzazione indirizzi italiani
│   │   └── GeocodeDao.kt / GeocodeCacheEntity.kt
│   ├── local/       Room: GplDatabase, GplDao, BackendPreferences, Monitoring,
│   │                NationalGplSnapshot (storico + andamento), NewsItem, TankRevision
│   ├── model/       GplStation, UserPriceReport
│   └── repository/
│       ├── GplRepository.kt               refresh, migrazione preferiti, import POI
│       └── GplItaliaRepository.kt         numeri nazionali, notizie, scadenza serbatoio
└── ui/
    ├── components/
    │   ├── InteractiveMapView.kt          mappa osmdroid, pin, legenda, scheda
    │   ├── MapPriceTiers.kt               terzili dei prezzi in lista → colore del pin
    │   └── …                              dialoghi, calcolatore, GPL in Italia
    ├── screens/     HomeScreen
    └── viewmodel/   GplViewModel
```

## GPL in Italia

Il mappamondo nella barra in alto apre tre cose che rispondono a domande che la lista dei
distributori non può soddisfare. Tutte e tre rispettano la stessa regola del resto dell'app: **niente
parte da solo e niente è stimato.**

### I numeri

Media e mediana nazionali del GPL, classifica delle 20 regioni dalla più economica alla più cara, e
la posizione della Campania nel confronto. Sono calcolate dagli **stessi due CSV open data** già usati
come fallback: la lettura riusa lo stesso codice di streaming (`MimitCsvRows.kt`), ma tiene l'Italia
intera invece delle sole cinque province.

Costa ~7,5 MB non comprimibili, quindi **non parte mai da sola**: si scarica quando lo chiedi, e non
si riscarica se la pubblicazione di oggi è già sul device — la fonte esce una volta la mattina, verso
le 06:45 UTC.

Gli impianti con un prezzo GPL valido ma con la colonna `Provincia` scritta fuori formato
nell'anagrafica ufficiale (nella pubblicazione del 10/08/2026 sono 19 su 4.599) **non vengono
attribuiti a una regione a caso**: restano fuori dalle medie e il loro numero è mostrato in chiaro.

### L'andamento

La fonte pubblica solo la situazione di stamattina: non esiste una serie storica da scaricare.
L'unico modo onesto di dire "−1,2% in una settimana" è **aver salvato la fotografia di una settimana
fa**, ed è ciò che l'app fa — una riga per giorno di pubblicazione reale nella tabella
`national_gpl_snapshots`, mai un giorno interpolato per riempire un buco.

Finché non esiste una lettura abbastanza vecchia, l'andamento **non viene mostrato**. Quando esiste,
il confronto usa la lettura più recente fra quelle abbastanza lontane e porta con sé i giorni
effettivi di distanza: se manca il giorno esatto di sette giorni fa, l'app scrive "9 giorni fa" invece
di arrotondare a "una settimana".

### Notizie

Il feed RSS della [sala stampa del MIMIT](https://www.mimit.gov.it/it/notizie-stampa) (~9 KB),
filtrato sulle parole che identificano una notizia sui carburanti. È la sala stampa **generale** del
ministero, dove la gran parte delle voci parla d'altro: è quindi normale che per settimane non ci sia
niente da mostrare, e in quel caso l'app **lo dice** invece di riempire lo spazio.

Titoli e sommari sono quelli del ministero, non riassunti né riscritti; toccare una notizia apre la
pagina originale.

### La scadenza del serbatoio

Un serbatoio GPL per autotrazione vale **dieci anni** (Regolamento UNECE n. 67) e, scaduto, non fa
passare la revisione periodica.

La data la inserisci tu, leggendola dal libretto o dalla punzonatura sul serbatoio: **l'app non la
ricava dalla targa e non la indovina**, e senza data non mostra nessun conto alla rovescia. Se
inserisci la data di collaudo o di prima immatricolazione, l'app *propone* la scadenza a +10 anni —
una proposta modificabile, perché in caso di discordanza vince quello che è scritto sul libretto. Il
preavviso è di 90 giorni, perché le officine autorizzate alla sostituzione hanno spesso settimane di
attesa.

Il dato resta in `SharedPreferences` sul telefono e non viene inviato da nessuna parte.

## Diagnostica

*Impostazioni → Diagnostica* apre un pannello che dice **come sta funzionando l'app**, non cosa
contiene: esito e durata dell'ultimo tentativo di aggiornamento, quale delle due fonti ufficiali ha
risposto, stato della cache di 15 minuti, conteggi letti da Room (totali, da fonte ufficiale,
aggiunti a mano, preferiti, segnalazioni, senza coordinate) e la qualità dell'ultimo scarico
(doppioni uniti, e da quanti giorni i gestori hanno comunicato i prezzi).

La diagnostica dell'ultimo tentativo è persistita, quindi un aggiornamento fallito ieri sera è
ancora leggibile stamattina. Un valore mai misurato si legge `—`, mai `0`. Le tre azioni disponibili
— riscaricare, far scadere la cache, copiare il rapporto — non cancellano nulla.

Convenzione sugli id, che determina cosa sopravvive a un refresh:

| prefisso | origine | cancellato dal refresh |
|---|---|---|
| `gpl_mimit_*` | fonte ufficiale MIMIT | sì, viene rigenerato |
| `user_*` | aggiunto dall'utente | **no** |
| `mylpg_*`, `ecomotori_*` | POI importati | **no** |

## Limiti noti

- **Serve Internet** per aggiornare. Senza connessione l'app mostra l'ultimo dato reale salvato. Verificato sul device con entrambe le fonti irraggiungibili: le 426 righe in Room restano identiche byte per byte, compare il messaggio d'errore esplicito e il timestamp dell'ultimo aggiornamento **non** viene toccato, così il tentativo si ripete al riavvio invece di aspettare i 15 minuti di cache.
- **`ospzApi` non è un'API con termini d'uso pubblicati**: è il backend del sito dell'Osservaprezzi e può cambiare senza preavviso. È esattamente la ragione del fallback sugli open data, che sono invece una pubblicazione ufficiale e documentata.
- **Segnalazioni prezzo e distributori aggiunti sono locali al device**, non condivisi con altri utenti.
- I prezzi sono quelli **comunicati dai gestori** al ministero, con la data di comunicazione mostrata in app. Un gestore che non aggiorna resta pubblicato con la sua data vecchia.
- L'app mostra **solo il GPL**, per scelta, anche se la fonte fornisce tutti i carburanti.
- **L'andamento nazionale non è disponibile subito**: si costruisce sulle fotografie che l'app stessa conserva, quindi il confronto a 7 giorni compare dopo una settimana di letture e quello a 30 dopo un mese. È una conseguenza della fonte, che pubblica solo la giornata corrente, e non si aggira con una stima.
- **Le notizie dipendono da cosa scrive il ministero**: la sala stampa è generale, e in molte settimane non contiene nulla sui carburanti.

## Attribuzioni e rispetto delle policy

- **Prezzi e anagrafica impianti**: [Osservaprezzi carburanti](https://carburanti.mise.gov.it/) del **MIMIT** — Ministero delle Imprese e del Made in Italy. Gli open data sono pubblicati fra i [dataset del ministero](https://www.mimit.gov.it/it/open-data) (`images/exportCSV/prezzo_alle_8.csv` e `images/exportCSV/anagrafica_impianti_attivi.csv`). Consultati alla frequenza di un utente umano: TTL 15 minuti, 5 richieste per aggiornamento, `User-Agent` identificativo con URL del progetto. Le medie nazionali rileggono gli stessi due CSV al massimo una volta al giorno e solo su richiesta esplicita.
- **Notizie**: feed RSS della sala stampa del MIMIT (`mimit.gov.it/it/notizie-stampa?format=feed&type=rss`), letto solo quando l'utente lo chiede. Titoli e sommari sono ripubblicati come sono, con il link alla pagina originale sempre in vista.
- **Dati geografici**: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, [ODbL](https://opendatacommons.org/licenses/odbl/).
- **Geocoding**: [Nominatim](https://nominatim.org/). L'app rispetta la [usage policy](https://operations.osmfoundation.org/policies/nominatim/): massimo 1 richiesta/secondo, `User-Agent` identificativo con URL del progetto, cache permanente per non ripetere richieste, backoff esplicito sull'HTTP 429.
- **Tile della mappa**: server tile di OSM tramite osmdroid, secondo la [tile usage policy](https://operations.osmfoundation.org/policies/tiles/): `User-Agent` identificativo e cache su disco per non riscaricare gli stessi tile.

Se usi questo codice, mantieni il rate limiting. È la condizione che tiene Nominatim gratuito per tutti.

## Licenza

[MIT](LICENSE).
