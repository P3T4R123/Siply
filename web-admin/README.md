# Siply Web Admin

Web admin je mali desktop panel za uređivanje artikala, cijena i skladišta iz istog Firebase kafića koji koristi Android aplikacija.

## Kako koristiti

1. U Android aplikaciji otvori `Settings`.
2. U online dijelu klikni `Generiraj web admin kod`.
3. Otvori web admin stranicu na kompjuteru.
4. Zalijepi web admin kod i klikni `Spoji web admin`.
5. Uredi artikle, cijene i stanje robe iz tablice.

## GitHub Pages

Ako je uključen GitHub Pages preko Actions workflowa, stranica ce biti dostupna na:

```text
https://p3t4r123.github.io/Siply/
```

Ako Firebase javi `PERMISSION_DENIED`, u Firebase Console treba objaviti pravila iz `firestore.rules`.
