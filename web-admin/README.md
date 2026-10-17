# Siply Web Admin

Web admin je desktop panel za isti Firebase kafić koji koristi Android aplikacija. Na kompjuteru možeš raditi dashboard, POS račune, pregled/export/reset računa, uređivanje artikala, import/export cjenika i unos robe u skladište.

## Kako koristiti

1. U Android aplikaciji otvori `Settings`.
2. U online dijelu klikni `Generiraj web admin kod`.
3. Otvori web admin stranicu na kompjuteru.
4. Zalijepi web admin kod i klikni `Spoji web admin`.
5. Koristi tabove `Dashboard`, `POS`, `Računi`, `Artikli` i `Settings`.

## Spajanje konobara

1. Admin u web postavkama generira waiter QR.
2. Konobar skenira QR kamerom mobitela i otvori poveznicu.
3. Upiše ime i klikne `Spoji se`. E-mail, Google račun i lozinka nisu potrebni.
4. U Android aplikaciji može prvo upisati ime pa kliknuti `Skeniraj waiter QR`; nakon skeniranja spaja se automatski.

Svaki uređaj zadržava vlastiti identitet konobara i dodijeljene ovlasti. Postojeći konobari ostaju povezani.

## GitHub Pages

Ako je uključen GitHub Pages preko Actions workflowa, stranica ce biti dostupna na:

```text
https://p3t4r123.github.io/Siply/
```

Ako Firebase javi `PERMISSION_DENIED`, u Firebase Console treba objaviti pravila iz `firestore.rules`.
