import 'dart:convert';

import 'package:http/http.dart' as http;

import 'modeles.dart';

/// L'adresse du serveur, fixee a la compilation :
/// `flutter run --dart-define=API_URL=http://10.0.2.2:9092` sur l'emulateur, la production sinon.
const String adresseApi = String.fromEnvironment('API_URL', defaultValue: 'https://stock.tontinepro.uk');

const String _racine = '$adresseApi/gestiondestock/v1';

/// Un refus du serveur, avec le message qu'il a ecrit pour etre lu.
///
/// L'API dit « Ce ticket a déjà été enregistré » ou « Il faut échanger au moins 1000 points » :
/// c'est ce que le client doit lire, et non « Erreur 400 ».
class ErreurApi implements Exception {
  ErreurApi(this.message, {this.statut = 0});

  final String message;
  final int statut;

  bool get sessionExpiree => statut == 401;

  @override
  String toString() => message;
}

class Api {
  Api({http.Client? client}) : _http = client ?? http.Client();

  final http.Client _http;

  /// Le jeton du client connecte. Nul : seules la vitrine et la connexion sont ouvertes.
  String? jeton;

  /// Appele quand le serveur ne reconnait plus le jeton : la session est close.
  void Function()? surSessionExpiree;

  Future<List<Campagne>> campagnes() async {
    final liste = await _get('/fidelite/campagnes') as List;
    return [for (final c in liste) Campagne.depuis(c as Map<String, dynamic>)];
  }

  Future<String> inscrire({
    required String telephone,
    required String motDePasse,
    String? prenom,
    String? nom,
  }) async {
    final reponse = await _post('/fidelite/auth/inscription', {
      'telephone': telephone,
      'motDePasse': motDePasse,
      'prenom': prenom,
      'nom': nom,
    });
    return (reponse as Map<String, dynamic>)['jeton'] as String;
  }

  Future<String> connecter({required String telephone, required String motDePasse}) async {
    final reponse = await _post('/fidelite/auth/connexion', {
      'telephone': telephone,
      'motDePasse': motDePasse,
    });
    return (reponse as Map<String, dynamic>)['jeton'] as String;
  }

  Future<Profil> profil() async =>
      Profil.depuis(await _get('/fidelite/profil') as Map<String, dynamic>);

  Future<ResultatScan> scanner(String codeTicket) async => ResultatScan.depuis(
        await _post('/fidelite/tickets/reclamer', {'codeTicket': codeTicket}) as Map<String, dynamic>,
      );

  Future<List<TicketScanne>> tickets() async {
    final liste = await _get('/fidelite/tickets/historique') as List;
    return [for (final t in liste) TicketScanne.depuis(t as Map<String, dynamic>)];
  }

  Future<Bon> echanger({required int idMagasin, required int points}) async => Bon.depuis(
        await _post('/fidelite/bons/convertir', {'idEntreprise': idMagasin, 'pointsAConvertir': points})
            as Map<String, dynamic>,
      );

  Future<List<Bon>> bons() async {
    final liste = await _get('/fidelite/bons') as List;
    return [for (final b in liste) Bon.depuis(b as Map<String, dynamic>)];
  }

  Future<Object?> _get(String chemin) =>
      _envoyer(() => _http.get(Uri.parse('$_racine$chemin'), headers: _entetes()));

  Future<Object?> _post(String chemin, Object corps) => _envoyer(
        () => _http.post(Uri.parse('$_racine$chemin'), headers: _entetes(), body: jsonEncode(corps)),
      );

  Map<String, String> _entetes() => {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        if (jeton != null) 'Authorization': 'Bearer $jeton',
      };

  Future<Object?> _envoyer(Future<http.Response> Function() appel) async {
    final http.Response reponse;
    try {
      reponse = await appel().timeout(const Duration(seconds: 20));
    } catch (_) {
      throw ErreurApi('Impossible de joindre le serveur. Vérifiez votre connexion.');
    }
    final texte = utf8.decode(reponse.bodyBytes);
    final corps = texte.isEmpty ? null : jsonDecode(texte);
    if (reponse.statusCode >= 200 && reponse.statusCode < 300) {
      return corps;
    }
    if (reponse.statusCode == 401 && jeton != null) {
      surSessionExpiree?.call();
      throw ErreurApi('Votre session a expiré. Reconnectez-vous.', statut: 401);
    }
    final message = corps is Map ? corps['message'] as String? : null;
    throw ErreurApi(message ?? 'Le serveur a refusé la demande (${reponse.statusCode}).',
        statut: reponse.statusCode);
  }
}

/// Le code de ticket contenu dans ce qu'on a scanne ou tape.
///
/// Le QR du ticket porte une adresse — `https://…/t/7K3M9P2QA4TZ` —, le papier imprime le code en
/// trois groupes : on accepte les deux, et la meme tolerance que le serveur pour la recopie a la
/// main (O lu comme 0, I et L comme 1). Nul si ce n'est pas un code de ticket.
String? codeDeTicket(String lu) {
  var texte = lu.trim();
  final indexAdresse = texte.lastIndexOf('/t/');
  if (indexAdresse >= 0) {
    texte = texte.substring(indexAdresse + 3);
  }
  final code = texte
      .toUpperCase()
      .replaceAll(RegExp(r'[\s\-]'), '')
      .replaceAll('O', '0')
      .replaceAll('I', '1')
      .replaceAll('L', '1');
  return RegExp(r'^[0-9A-HJKMNP-TV-Z]{12}$').hasMatch(code) ? code : null;
}
