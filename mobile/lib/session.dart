import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'api/api.dart';
import 'api/modeles.dart';

/// Le client connecte, et ce qu'on sait de lui.
///
/// Le jeton est garde dans le coffre du telephone — le Keystore d'Android —, pas dans des
/// preferences en clair : il ouvre les points du client pendant trente jours.
class Session extends ChangeNotifier {
  Session(this.api, {FlutterSecureStorage? coffre}) : _coffre = coffre ?? const FlutterSecureStorage() {
    api.surSessionExpiree = deconnecter;
  }

  static const _cleJeton = 'jeton';

  final Api api;
  final FlutterSecureStorage _coffre;

  bool _prete = false;
  Profil? _profil;

  /// Faux tant que le coffre n'a pas ete relu au demarrage.
  bool get prete => _prete;
  bool get connecte => api.jeton != null;
  Profil? get profil => _profil;

  Future<void> demarrer() async {
    try {
      api.jeton = await _coffre.read(key: _cleJeton);
    } catch (_) {
      api.jeton = null;
    }
    _prete = true;
    notifyListeners();
    if (connecte) {
      await rafraichir().catchError((_) {});
    }
  }

  Future<void> connecter(String telephone, String motDePasse) async {
    await _ouvrir(await api.connecter(telephone: telephone, motDePasse: motDePasse));
  }

  Future<void> inscrire(String telephone, String motDePasse, {String? prenom, String? nom}) async {
    await _ouvrir(await api.inscrire(telephone: telephone, motDePasse: motDePasse, prenom: prenom, nom: nom));
  }

  /// Relit les points : apres un scan, un echange, ou quand on revient sur l'ecran.
  Future<void> rafraichir() async {
    if (!connecte) {
      return;
    }
    _profil = await api.profil();
    notifyListeners();
  }

  void deconnecter() {
    api.jeton = null;
    _profil = null;
    _coffre.delete(key: _cleJeton).catchError((_) {});
    notifyListeners();
  }

  Future<void> _ouvrir(String jeton) async {
    api.jeton = jeton;
    await _coffre.write(key: _cleJeton, value: jeton);
    notifyListeners();
    await rafraichir();
  }
}
