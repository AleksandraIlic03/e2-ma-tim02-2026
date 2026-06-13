# Slagalica — Mobilne aplikacije (Tim 02, 2025/26)

Mobilna Android aplikacija po ugledu na kviz "Slagalica". Aplikacija omogućava takmičenje igrača jedan na jedan kroz šest igara, rangiranje, sistem liga, čet po regionima i upravljanje notifikacijama.

---

## Tim

| Student | Funkcionalnosti |
|---|---|
| Student 1 | Registracija i logovanje, Korak po korak, Moj broj |
| Student 2 | Prikaz profila, Ko zna zna, Spojnice |
| Student 3 | Notifikacije, Asocijacije, Skočko |

---

## Tehnologije

- **Java** — Android aplikacija
- **Firebase Authentication** — registracija i logovanje
- **Firebase Firestore** — baza podataka u realnom vremenu
- **Android API 29+** (Android 10+)
- **Material Design 3** komponente

---

## Pokretanje projekta


### Koraci

1. Klonirati repozitorijum:
   ```
   git clone https://github.com/AleksandraIlic03/e2-ma-tim02-2026.git
   ```
2. Otvoriti projekat u Android Studio-u: **File > Open** — izabrati folder projekta
3. Sačekati da Gradle automatski sinhronizuje zavisnosti
4. Pokrenuti aplikaciju klikom na **Run ▶**

> `google-services.json` je uključen u projekat — Firebase je već podešen i nije potrebna dodatna konfiguracija.

---

## Pokretanje na fizičkom uređaju

1. Na telefonu otvoriti: **Podešavanja > O telefonu > Broj verzije** — kliknuti 7 puta zaredom
2. Otvoriti: **Podešavanja > Sistem > Opcije za programere > USB debagovanje** — uključiti
3. Povezati telefon sa računarom putem USB kabla
4. U Android Studio-u izabrati uređaj iz padajuće liste i kliknuti **Run**

---

## Pokretanje na emulatoru (AVD)

1. U Android Studio-u otvoriti: **Tools > Device Manager > Create Virtual Device**
2. Izabrati uređaj (npr. Pixel 6) i system image sa **API 29+**
3. Preuzeti system image ako nije instaliran, pa kliknuti **Finish**
4. Pokrenuti emulator, zatim kliknuti **Run**

> Za testiranje heads-up popup notifikacija preporučuje se emulator sa **API 33+**.

---

## Igre

| Igra | Trajanje | Maks. bodovi |
|---|---|---|
| Ko zna zna | 25s | 50 |
| Spojnice | 2 × 30s | 20 |
| Asocijacije | 2 × 2 min | 60 |
| Skočko | 2 × 30s | 40 |
| Korak po korak | 2 × 70s | 40 |
| Moj broj | 2 × 1 min | 20 |

Jedna partija se sastoji od svih šest igara koje se igraju jedna za drugom.

---

## Notifikacije

Aplikacija koristi 4 notifikaciona kanala:

- **Čet** — poruke iz regionalnog četa
- **Rangiranje** — obaveštenja o plasmanu na rang listama
- **Nagrade** — obaveštenja o nagradama i prelasku u ligu
- **Ostalo** — ostala obaveštenja (npr. Skočko steal šansa)

Istorija notifikacija dostupna je klikom na ikonicu zvona u zaglavlju. Notifikacije se mogu označiti kao pročitane i filtrirati po statusu (pročitane / nepročitane) ili po kanalu.

---

## Baza podataka

Podaci za igre (pitanja za Ko zna zna, spojnice, asocijacije, korak po korak) nalaze se u Firebase Firestore bazi. Aplikacija automatski popunjava potrebne kolekcije pri prvom pokretanju ukoliko su prazne.
