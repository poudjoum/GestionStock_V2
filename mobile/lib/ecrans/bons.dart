import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../api/modeles.dart';
import '../outils.dart';
import '../session.dart';

/// Mes bons d'achat. Celui qu'on touche s'ouvre en grand, avec son QR, pour le caissier.
class EcranBons extends StatefulWidget {
  const EcranBons({super.key});

  @override
  State<EcranBons> createState() => _EcranBonsState();
}

class _EcranBonsState extends State<EcranBons> {
  late Future<List<Bon>> _bons;

  @override
  void initState() {
    super.initState();
    _bons = context.read<Session>().api.bons();
  }

  Future<void> _recharger() async {
    final suivants = context.read<Session>().api.bons();
    setState(() => _bons = suivants);
    await suivants.catchError((_) => <Bon>[]);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<Bon>>(
      future: _bons,
      builder: (context, etat) {
        if (etat.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (etat.hasError) {
          return EtatErreur(message: etat.error.toString(), reessayer: _recharger);
        }
        final bons = etat.data!;
        final actifs = bons.where((b) => b.utilisable).toList();
        final passes = bons.where((b) => !b.utilisable).toList();
        return RefreshIndicator(
          onRefresh: _recharger,
          child: bons.isEmpty
              ? ListView(children: const [
                  SizedBox(height: 80),
                  EtatVide(
                    icone: Icons.redeem,
                    titre: 'Aucun bon d’achat',
                    texte: 'Échangez vos points dans « Mes points » : le bon apparaîtra ici, prêt à montrer en caisse.',
                  ),
                ])
              : ListView(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                  children: [
                    for (final bon in actifs) _CarteBon(bon: bon),
                    if (passes.isNotEmpty) ...[
                      const SizedBox(height: 16),
                      Text('UTILISÉS OU EXPIRÉS', style: Theme.of(context).textTheme.labelSmall),
                      const SizedBox(height: 8),
                      for (final bon in passes) _CarteBon(bon: bon),
                    ],
                  ],
                ),
        );
      },
    );
  }
}

class _CarteBon extends StatelessWidget {
  const _CarteBon({required this.bon});

  final Bon bon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final couleurs = theme.colorScheme;
    final etat = switch (bon.statut) {
      StatutBon.utilise => 'Utilisé le ${jour(bon.utilisation)}',
      StatutBon.expire => 'Expiré le ${jour(bon.expiration)}',
      StatutBon.actif => 'Valable jusqu’au ${jour(bon.expiration)}',
    };
    return Opacity(
      opacity: bon.utilisable ? 1 : 0.6,
      child: Card(
        margin: const EdgeInsets.only(bottom: 12),
        color: bon.utilisable ? couleurs.secondaryContainer : null,
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          leading: Icon(Icons.redeem, size: 32, color: bon.utilisable ? couleurs.primary : couleurs.outline),
          title: Text(francs(bon.montant), style: theme.textTheme.titleLarge),
          subtitle: Text('${bon.nomMagasin}\n$etat'),
          isThreeLine: true,
          trailing: bon.utilisable ? const Icon(Icons.qr_code_2, size: 32) : null,
          onTap: bon.utilisable
              ? () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => _BonEnGrand(bon: bon)))
              : null,
        ),
      ),
    );
  }
}

/// Le bon tel qu'on le tend au caissier : le QR, que sa douchette lit, et le code en clair.
class _BonEnGrand extends StatelessWidget {
  const _BonEnGrand({required this.bon});

  final Bon bon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(bon.nomMagasin)),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              children: [
                Text('Bon d’achat', style: theme.textTheme.titleMedium),
                Text(francs(bon.montant),
                    style: theme.textTheme.displayMedium
                        ?.copyWith(color: theme.colorScheme.primary, fontWeight: FontWeight.w600)),
                const SizedBox(height: 24),
                // Fond blanc, quel que soit le theme : une douchette lit mal un QR sur fond sombre.
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)),
                  child: QrImageView(data: bon.code, size: 240, backgroundColor: Colors.white),
                ),
                const SizedBox(height: 16),
                SelectableText(bon.code,
                    style: theme.textTheme.headlineSmall?.copyWith(letterSpacing: 2, fontFamily: 'monospace')),
                const SizedBox(height: 8),
                Text('Valable jusqu’au ${jour(bon.expiration)}', style: theme.textTheme.bodyMedium),
                const SizedBox(height: 24),
                Text(
                  'Montrez ce bon au caissier avant de payer : il le déduit de votre total. '
                  'Il ne rend pas la monnaie.',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
