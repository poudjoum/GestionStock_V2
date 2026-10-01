import 'package:fidelite/api/api.dart';
import 'package:fidelite/api/modeles.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('codeDeTicket', () {
    test('lit l’adresse du QR imprimé sur le ticket', () {
      expect(codeDeTicket('https://stock.tontinepro.uk/t/7K3M9P2QA4TZ'), '7K3M9P2QA4TZ');
    });

    test('lit le code recopié à la main, en groupes et en minuscules', () {
      expect(codeDeTicket(' 7k3m-9p2q-a4tz '), '7K3M9P2QA4TZ');
    });

    test('confond O et 0, I et L avec 1, comme le serveur', () {
      expect(codeDeTicket('7K3M9P2QA4TO'), '7K3M9P2QA4T0');
      expect(codeDeTicket('7K3M9P2QA4TI'), '7K3M9P2QA4T1');
    });

    test('refuse ce qui n’est pas un code de ticket', () {
      expect(codeDeTicket('https://exemple.com'), isNull);
      expect(codeDeTicket('BON-5NRD-HPCB'), isNull);
      expect(codeDeTicket('123'), isNull);
    });
  });

  group('Solde', () {
    Solde solde(int points, {double valeur = 2.5, int minimum = 1000}) => Solde.depuis({
          'idEntreprise': 1,
          'nomMagasin': 'Quincaillerie',
          'soldePoints': points,
          'valeurPointFcfa': valeur,
          'pointsMinimumBon': minimum,
        });

    test('dit ce que valent les points, au franc inférieur comme le serveur', () {
      expect(solde(601).valeurDe(601), 1502);
    });

    test('n’est échangeable qu’à partir du minimum du magasin', () {
      expect(solde(999).echangeable, isFalse);
      expect(solde(1000).echangeable, isTrue);
    });
  });
}
