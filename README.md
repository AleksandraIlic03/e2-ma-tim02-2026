# Slagalica - Mobilne Aplikacije (Tim 02)

Ovaj projekat predstavlja mobilnu aplikaciju po ugledu na popularni kviz "Slagalica", razvijenu u okviru predmeta Mobilne aplikacije.

## Pokretanje projekta

### Neophodno:
*   Android Studio (verzija Ladybug ili novija)
*   Android uređaj (Fizički ili emulator, API level 30+, Android 11+)

## Aplikacija

*   Kada se otvori aplikacija, može se raditi registracija ili prijava.
*   Na glavnom ekranu (Home) se nalaze statistike (zvezde, tokeni, trofeji) i odatle se mogu zasebno pokrenuti igre:
    *   Asocijacije: Rešavanje kolona A, B, V, G radi otkrivanja konačnog rešenja.
    *   Skočko: Pogađanje kombinacije znakova u 6 pokušaja.
    *   Korak po korak: Otkrivanje skrivenog pojma kroz niz koraka.
    *   Spojnice: Povezivanje pojmova iz dve kolone.
    *   Moj broj: Dobijanje traženog broja pomoću ponuđenih cifara i operacija.
    *   Ko zna zna: Provera opšteg znanja kroz pitanja sa više ponuđenih odgovora.
*   Klikom na notifikacije (zvonce u zaglavlju) otvara se istorija obaveštenja gde se mogu pratiti nagrade, poruke iz četa ili obaveštenja o rangu.
*   Dugme za odjavu (Logout) nas vraća nazad na početni ekran za prijavu.

## Pokretanje aplikacije

Aplikaciju je moguće pokrenuti na fizičkom uređaju ili putem Android virtuelnog uređaja (AVD).

* Pokretanje na fizičkom uređaju
	1.	Na telefonu omogućiti Developer Options:
	*	Settings > About Phone > Build Number
	*	Kliknuti 7 puta na Build Number.
	2.	Uključiti USB Debugging:
	*	Settings > System > Developer Options > USB Debugging
	3.	Povezati uređaj sa računarom putem USB kabla.
	4.	U Android Studio okruženju izabrati uređaj i kliknuti Run.

* Pokretanje na virtuelnom uređaju (AVD)
	1.	U Android Studio otvoriti:
	*	Tools > Device Manager / AVD Manager
	2.	Kliknuti na Create Virtual Device.
	3.	Izabrati model uređaja i odgovarajuću Android verziju.
	4.	Preuzeti (Download) željeni System Image ukoliko nije instaliran.
	5.	Završiti konfiguraciju klikom na Finish.
	6.	Pokrenuti emulator i kliknuti Run za pokretanje aplikacije.