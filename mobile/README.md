# Fidélité — l'application des clients

L'application Android des clients des magasins abonnés : les promotions en cours, le scan du
ticket de caisse qui crédite les points, l'échange des points contre un bon d'achat, et le bon à
montrer en caisse.

Elle ne parle qu'à l'API `/gestiondestock/v1/fidelite/**` du serveur de gestion de stock.

## Lancer

```bash
cd mobile
flutter pub get

# Contre la production (par défaut : https://stock.tontinepro.uk)
flutter run

# Contre un serveur de développement, depuis l'émulateur Android
flutter run --dart-define=API_URL=http://10.0.2.2:9092
```

Le http en clair n'est autorisé qu'en debug (`android/app/src/debug/AndroidManifest.xml`) : la
version publiée ne parle qu'en https.

## Construire l'APK

```bash
flutter build apk --release --dart-define=API_URL=https://stock.tontinepro.uk
```

La signature de publication n'est pas encore configurée : l'APK de release est signé avec la clé
de debug.

## Ce qui reste à brancher

- **Notifications des campagnes** : elles passeront par Firebase Cloud Messaging, qui demande un
  projet Firebase et son `google-services.json` dans `android/app/`. En attendant, l'onglet
  « Promos » relit les campagnes à chaque ouverture.

## Tests

```bash
flutter analyze
flutter test
```
