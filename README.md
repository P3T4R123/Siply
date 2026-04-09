# Siply

Android POS app za kafic s admin i waiter modom.

## APK build na GitHubu

Repo ima GitHub Actions workflow koji na svaki push gradi debug APK i sprema ga kao artifact.

### Jednokratni setup

1. Otvori `Settings > Secrets and variables > Actions` na GitHub repou.
2. Dodaj novi repository secret:
   `GOOGLE_SERVICES_JSON`
3. Kao vrijednost zalijepi cijeli sadržaj svog lokalnog `app/google-services.json`.

### Gdje skidati APK

1. Otvori `Actions` tab na GitHubu.
2. Uđi u zadnji `Build Android APK` run.
3. Na dnu skini artifact `siply-debug-apk`.
