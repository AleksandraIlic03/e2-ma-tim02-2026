Slagalica - Mobilne aplikacije (Tim 02, 2025/26)

Mobilna Android aplikacija po ugledu na kviz "Slagalica". Aplikacija omogucava takmicenje igraca jedan na jedan kroz sest igara, rangiranje, sistem liga, cet po regionima i upravljanje notifikacijama.

Tim

Student 1: Registracija i logovanje, Korak po korak, Moj broj
Student 2: Prikaz profila, Ko zna zna, Spojnice
Student 3: Notifikacije, Asocijacije, Skocko

Tehnologije

Java, Android API 29+
Firebase Authentication
Firebase Firestore
Material Design 3

Preduslovi

Android Studio Ladybug ili novija verzija
JDK 11
Android uredjaj ili emulator sa API level 29+ (Android 10+)
Internet konekcija

Pokretanje projekta

1. Klonirati repozitorijum:
   git clone https://github.com/AleksandraIlic03/e2-ma-tim02-2026.git
2. Otvoriti projekat u Android Studio-u: File > Open, izabrati folder projekta
3. Sacekati da Gradle sinhronizuje zavisnosti
4. Pokrenuti aplikaciju klikom na Run

Napomena: google-services.json je ukljucen u projekat, Firebase nije potrebno dodatno podesavati.

Pokretanje na fizickom uredjaju

1. Na telefonu: Podesavanja > O telefonu > Broj verzije, kliknuti 7 puta
2. Podesavanja > Sistem > Opcije za programere > USB debagovanje, ukljuciti
3. Povezati telefon sa racunarom putem USB kabla
4. U Android Studio-u izabrati uredjaj i kliknuti Run

Pokretanje na emulatoru (AVD)

1. Tools > Device Manager > Create Virtual Device
2. Izabrati uredjaj (npr. Pixel 6) i system image sa API 29+
3. Preuzeti system image ako nije instaliran, kliknuti Finish
4. Pokrenuti emulator i kliknuti Run

Napomena: Za testiranje popup notifikacija preporucuje se emulator sa API 33+.

Igre

Jedna partija se sastoji od svih sest igara koje se igraju jedna za drugom:

Ko zna zna (25s, max 50 bodova)
Spojnice (2 x 30s, max 20 bodova)
Asocijacije (2 x 2 min, max 60 bodova)
Skocko (2 x 30s, max 40 bodova)
Korak po korak (2 x 70s, max 40 bodova)
Moj broj (2 x 1 min, max 20 bodova)

Notifikacije

Aplikacija koristi 4 kanala: cet, rangiranje, nagrade i ostalo. Istorija notifikacija dostupna je klikom na zvono u zaglavlju. Notifikacije se mogu oznaciti kao procitane i filtrirati.

Baza podataka

Podaci za igre nalaze se u Firebase Firestore bazi. Aplikacija automatski popunjava potrebne kolekcije pri prvom pokretanju ukoliko su prazne.
