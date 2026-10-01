import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

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
    final couleurs = theme.colorScheme;
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
                Icon(Icons.stars_rounded, size: 56, color: couleurs.primary),
                const SizedBox(height: 12),
                Text(_inscription ? 'Créer mon compte' : 'Me connecter',
                    style: theme.textTheme.headlineSmall, textAlign: TextAlign.center),
                const SizedBox(height: 6),
                Text(
                  widget.raison ?? 'Gagnez des points à chaque achat dans les magasins partenaires.',
                  style: theme.textTheme.bodyMedium?.copyWith(color: couleurs.onSurfaceVariant),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                if (_inscription) ...[
                  TextFormField(
                    controller: _prenom,
                    textCapitalization: TextCapitalization.words,
                    decoration: const InputDecoration(labelText: 'Prénom (facultatif)', border: OutlineInputBorder()),
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
                    prefixIcon: Icon(Icons.phone),
                    border: OutlineInputBorder(),
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
                    border: const OutlineInputBorder(),
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
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: couleurs.errorContainer,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(_erreur!, style: TextStyle(color: couleurs.onErrorContainer)),
                  ),
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
