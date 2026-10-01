import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api.dart';
import '../api/modeles.dart';
import '../outils.dart';
import '../session.dart';

/// Mes points, magasin par magasin, et l'echange contre un bon.
///
/// Les points d'un magasin ne se depensent que chez lui : le total general dit combien on a, mais
/// c'est la carte du magasin qui dit ce qu'on peut en faire.
class EcranPoints extends StatefulWidget {
  const EcranPoints({super.key, required this.versMesBons});

  /// Ou aller une fois un bon obtenu : on veut le voir tout de suite.
  final VoidCallback versMesBons;

  @override
  State<EcranPoints> createState() => _EcranPointsState();
}

class _EcranPointsState extends State<EcranPoints> {
  late Future<List<TicketScanne>> _tickets;
  String? _erreur;

  @override
  void initState() {
    super.initState();
    _charger();
  }

  void _charger() {
    final session = context.read<Session>();
    _tickets = session.api.tickets();
    session.rafraichir().then((_) {
      if (mounted) setState(() => _erreur = null);
    }, onError: (Object e) {
      if (mounted) setState(() => _erreur = e.toString());
    });
  }

  Future<void> _recharger() async {
    setState(_charger);
    await _tickets.catchError((_) => <TicketScanne>[]);
  }

  Future<void> _echanger(Solde solde) async {
    final points = await showModalBottomSheet<int>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _Echange(solde: solde),
    );
    if (points == null || !mounted) {
      return;
    }
    final session = context.read<Session>();
    final messager = ScaffoldMessenger.of(context);
    try {
      final bon = await session.api.echanger(idMagasin: solde.idMagasin, points: points);
      await session.rafraichir().catchError((_) {});
      messager.showSnackBar(SnackBar(content: Text('Bon de ${francs(bon.montant)} créé — montrez-le en caisse.')));
      widget.versMesBons();
    } on ErreurApi catch (e) {
      messager.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final couleurs = theme.colorScheme;
    final profil = context.watch<Session>().profil;

    if (profil == null) {
      return _erreur != null
          ? EtatErreur(message: _erreur!, reessayer: _recharger)
          : const Center(child: CircularProgressIndicator());
    }

    return RefreshIndicator(
      onRefresh: _recharger,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          // Le chiffre qu'on vient chercher.
          Card(
            color: couleurs.primaryContainer,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
              child: Column(
                children: [
                  Text(
                    profil.prenom != null ? 'Bonjour ${profil.prenom} !' : 'Mes points',
                    style: theme.textTheme.titleMedium?.copyWith(color: couleurs.onPrimaryContainer),
                  ),
                  const SizedBox(height: 4),
                  Text(nombre(profil.totalPoints),
                      style: theme.textTheme.displayMedium
                          ?.copyWith(color: couleurs.onPrimaryContainer, fontWeight: FontWeight.w600)),
                  Text('points au total', style: TextStyle(color: couleurs.onPrimaryContainer)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          if (profil.soldes.isEmpty)
            const EtatVide(
              icone: Icons.qr_code_scanner,
              titre: 'Pas encore de points',
              texte: 'Pendant une campagne, scannez le QR de votre ticket de caisse : vos points s’ajoutent ici.',
            )
          else ...[
            Text('PAR MAGASIN', style: theme.textTheme.labelSmall?.copyWith(color: couleurs.onSurfaceVariant)),
            const SizedBox(height: 8),
            for (final solde in profil.soldes) _CarteSolde(solde: solde, echanger: () => _echanger(solde)),
          ],
          const SizedBox(height: 16),
          FutureBuilder<List<TicketScanne>>(
            future: _tickets,
            builder: (context, etat) {
              final tickets = etat.data ?? const [];
              if (tickets.isEmpty) {
                return const SizedBox.shrink();
              }
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('MES TICKETS', style: theme.textTheme.labelSmall?.copyWith(color: couleurs.onSurfaceVariant)),
                  for (final t in tickets.take(20))
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.receipt_long),
                      title: Text(t.nomMagasin),
                      subtitle: Text('${jour(t.date)} · ${francs(t.montant)}'),
                      trailing: Text('+${t.points}',
                          style: theme.textTheme.titleMedium?.copyWith(color: couleurs.primary)),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _CarteSolde extends StatelessWidget {
  const _CarteSolde({required this.solde, required this.echanger});

  final Solde solde;
  final VoidCallback echanger;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final couleurs = theme.colorScheme;
    final manque = solde.minimum - solde.points;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                CircleAvatar(
                  backgroundColor: couleurs.surfaceContainerHighest,
                  child: ClipOval(
                    child: SizedBox.square(
                      dimension: 40,
                      child: imageDuServeur(solde.logo, fit: BoxFit.contain, sinon: const Icon(Icons.storefront)),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(solde.nomMagasin, style: theme.textTheme.titleMedium),
                      Text('${nombre(solde.points)} points · ${francs(solde.valeurDe(solde.points))}',
                          style: theme.textTheme.bodyMedium?.copyWith(color: couleurs.onSurfaceVariant)),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (solde.minimum > 0) ...[
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: (solde.points / solde.minimum).clamp(0, 1).toDouble(),
                  minHeight: 8,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                solde.echangeable
                    ? 'Vous pouvez demander un bon d’achat.'
                    : 'Encore ${nombre(manque)} points pour un premier bon (${nombre(solde.minimum)} minimum).',
                style: theme.textTheme.bodySmall,
              ),
            ],
            const SizedBox(height: 8),
            FilledButton.icon(
              onPressed: solde.echangeable ? echanger : null,
              icon: const Icon(Icons.redeem),
              label: const Text('Échanger contre un bon'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Combien de points echanger : tous par defaut, au moins le minimum du magasin.
class _Echange extends StatefulWidget {
  const _Echange({required this.solde});

  final Solde solde;

  @override
  State<_Echange> createState() => _EchangeState();
}

class _EchangeState extends State<_Echange> {
  late double _points = widget.solde.points.toDouble();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final solde = widget.solde;
    final points = _points.round();
    final peutChoisir = solde.points > solde.minimum;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Bon d’achat chez ${solde.nomMagasin}', style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            Text(francs(solde.valeurDe(points)),
                textAlign: TextAlign.center,
                style: theme.textTheme.displaySmall
                    ?.copyWith(color: theme.colorScheme.primary, fontWeight: FontWeight.w600)),
            Text('pour ${nombre(points)} points', textAlign: TextAlign.center),
            if (peutChoisir)
              Slider(
                value: _points,
                min: solde.minimum.toDouble(),
                max: solde.points.toDouble(),
                onChanged: (v) => setState(() => _points = v),
              ),
            const SizedBox(height: 8),
            Text(
              'Le bon ne rend pas la monnaie : utilisez-le pour un achat au moins égal à sa valeur.',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodySmall,
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: () => Navigator.pop(context, points),
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
              child: const Text('Créer mon bon'),
            ),
          ],
        ),
      ),
    );
  }
}
