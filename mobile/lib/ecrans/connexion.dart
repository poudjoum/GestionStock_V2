import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../design/composants.dart';
import '../design/jetons.dart';
import '../session.dart';

/// Se connecter ou creer son compte : un numero de telephone et un mot de passe.
///
/// Le meme ecran pour les deux, bascule par un lien : celui qui arrive pour scanner son premier
/// ticket ne doit pas chercher ou s'inscrire.
class EcranConnexion extends StatefulWidget {
  const EcranConnexion({super.key, this.raison});

  /// Pourquoi on demande de se connecter : « pour scanner votre ticket », « pour voir vos points ».
  final String? raison;

  @override
  State<EcranConnexion> createState() => _EcranConnexionState();
}

class _EcranConnexionState extends State<EcranConnexion> {
  final _formulaire = GlobalKey<FormState>();
  final _telephone = TextEditingController();
  final _motDePasse = TextEditingController();
  final _prenom = TextEditingController();
  bool _inscription = false;
  bool _envoi = false;
  bool _masque = true;
  String? _erreur;

  @override
  void dispose() {
    _telephone.dispose();
    _motDePasse.dispose();
    _prenom.dispose();
    super.dispose();
  }

  Future<void> _valider() async {
    if (_envoi || !_formulaire.currentState!.validate()) {
      return;
    }
    setState(() {
      _envoi = true;
      _erreur = null;
    });
    final session = context.read<Session>();
    try {
      if (_inscription) {
        await session.inscrire(_telephone.text, _motDePasse.text,
            prenom: _prenom.text.trim().isEmpty ? null : _prenom.text.trim());
      } else {
        await session.connecter(_telephone.text, _motDePasse.text);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _erreur = e.toString());
      }
    } finally {
      if (mounted) {
        setState(() => _envoi = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final j = context.jetons;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Form(
            key: _formulaire,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Center(child: Logo(taille: 64)),
                const SizedBox(height: 16),
                Text(_inscription ? 'Créer mon compte' : 'Me connecter',
                    style: theme.textTheme.headlineMedium, textAlign: TextAlign.center),
                const SizedBox(height: 6),
                Text(
                  widget.raison ?? 'Gagnez des points à chaque achat dans les magasins partenaires.',
                  style: theme.textTheme.bodyMedium?.copyWith(color: j.encre2, height: 1.45),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                if (_inscription) ...[
                  TextFormField(
                    controller: _prenom,
                    textCapitalization: TextCapitalization.words,
                    decoration: const InputDecoration(labelText: 'Prénom (facultatif)', prefixIcon: Icon(Icons.person_outline)),
                  ),
                  const SizedBox(height: 12),
                ],
                TextFormField(
                  controller: _telephone,
                  keyboardType: TextInputType.phone,
                  autofillHints: const [AutofillHints.telephoneNumber],
                  decoration: const InputDecoration(
                    labelText: 'Numéro de téléphone',
                    hintText: '6 90 12 34 56',
                    prefixIcon: Icon(Icons.phone_outlined),
                  ),
                  validator: (v) {
                    final chiffres = (v ?? '').replaceAll(RegExp(r'[^0-9]'), '');
                    return chiffres.length < 8 ? 'Au moins 8 chiffres' : null;
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _motDePasse,
                  obscureText: _masque,
                  autofillHints: [_inscription ? AutofillHints.newPassword : AutofillHints.password],
                  decoration: InputDecoration(
                    labelText: 'Mot de passe',
                    prefixIcon: const Icon(Icons.lock_outline),
                    helperText: _inscription ? 'Au moins 6 caractères' : null,
                    suffixIcon: IconButton(
                      tooltip: _masque ? 'Afficher' : 'Masquer',
                      icon: Icon(_masque ? Icons.visibility : Icons.visibility_off),
                      onPressed: () => setState(() => _masque = !_masque),
                    ),
                  ),
                  validator: (v) => (v ?? '').length < 6 ? 'Au moins 6 caractères' : null,
                  onFieldSubmitted: (_) => _valider(),
                ),
                if (_erreur != null) ...[
                  const SizedBox(height: 16),
                  MessageErreur(_erreur!),
                ],
                const SizedBox(height: 20),
                FilledButton(
                  onPressed: _envoi ? null : _valider,
                  style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                  child: _envoi
                      ? const SizedBox.square(dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                      : Text(_inscription ? 'Créer mon compte' : 'Me connecter'),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: _envoi
                      ? null
                      : () => setState(() {
                            _inscription = !_inscription;
                            _erreur = null;
                          }),
                  child: Text(_inscription ? 'J’ai déjà un compte' : 'Première visite ? Créer mon compte'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
