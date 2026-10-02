import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api.dart';
import '../api/modeles.dart';
import '../design/composants.dart';
import '../design/jetons.dart';
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
    final j = context.jetons;
    final profil = context.watch<Session>().profil;

    if (profil == null) {
      return _erreur != null
          ? EtatErreur(message: _erreur!, reessayer: _recharger)
          : ListView(
              padding: const EdgeInsets.all(16),
              children: const [Squelette(hauteur: 150, marge: 20), Squelette(hauteur: 170), Squelette(hauteur: 170)],
            );
    }

    return RefreshIndicator(
      onRefresh: _recharger,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          // Le chiffre qu'on vient chercher, sur le petrole de la fidelite : la carte du client.
          Container(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 22),
            decoration: BoxDecoration(
              color: Jetons.petroleProfond,
              borderRadius: BorderRadius.circular(Rayons.grand),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        profil.prenom != null ? 'Bonjour ${profil.prenom} !' : 'Ma carte de fidélité',
                        style: theme.textTheme.titleMedium?.copyWith(color: Colors.white),
                      ),
                    ),
                    const Icon(Icons.stars, color: Colors.white70),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  nombre(profil.totalPoints),
                  style: const TextStyle(
                    fontFamily: Polices.titre,
                    fontSize: 56,
                    height: 1,
                    fontWeight: FontWeight.w800,
                    color: Colors.white,
                    fontFeatures: [FontFeature.tabularFigures()],
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  profil.soldes.length > 1
                      ? 'points au total, dans ${profil.soldes.length} magasins'
                      : 'points au total',
                  style: const TextStyle(color: Colors.white70),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          if (profil.soldes.isEmpty)
            const EtatVide(
              icone: Icons.qr_code_scanner,
              titre: 'Pas encore de points',
              texte: 'Pendant une campagne, scannez le QR de votre ticket de caisse : vos points s’ajoutent ici.',
            )
          else ...[
            const Etiquette('Par magasin'),
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
                  const SizedBox(height: 8),
                  const Etiquette('Mes tickets'),
                  Card(
                    child: Column(
                      children: [
                        for (final (i, t) in tickets.take(20).indexed) ...[
                          if (i > 0) const Divider(indent: 56),
                          ListTile(
                            leading: Icon(Icons.receipt_long, color: j.encre3),
                            title: Text(t.nomMagasin, style: const TextStyle(fontWeight: FontWeight.w600)),
                            subtitle: Text('${jour(t.date)} · ${francs(t.montant)}', style: TextStyle(color: j.encre3)),
                            trailing: Text('+${nombre(t.points)}',
                                style: TextStyle(
                                  fontFamily: Polices.titre,
                                  fontSize: 18,
                                  fontWeight: FontWeight.w800,
                                  color: j.petrole,
                                )),
                          ),
                        ],
                      ],
                    ),
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
    final j = context.jetons;
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
                AvatarMagasin(logo: solde.logo, taille: 44),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(solde.nomMagasin, style: theme.textTheme.titleMedium),
                      Text('vaut ${francs(solde.valeurDe(solde.points))} en bons',
                          style: theme.textTheme.bodySmall?.copyWith(color: j.encre3)),
                    ],
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(nombre(solde.points),
                        style: TextStyle(
                          fontFamily: Polices.titre,
                          fontSize: 24,
                          height: 1.1,
                          fontWeight: FontWeight.w800,
                          color: j.petrole,
                        )),
                    Text('points', style: theme.textTheme.bodySmall?.copyWith(color: j.encre3)),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 14),
            if (solde.minimum > 0) ...[
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: (solde.points / solde.minimum).clamp(0, 1).toDouble(),
                  minHeight: 8,
                  color: j.petrole,
                ),
              ),
              const SizedBox(height: 8),
              solde.echangeable
                  ? const Align(
                      alignment: Alignment.centerLeft,
                      child: Statut(ton: Ton.ok, icone: Icons.check, libelle: 'Un bon d’achat est possible'),
                    )
                  : Text(
                      'Encore ${nombre(manque)} points pour un premier bon (${nombre(solde.minimum)} minimum).',
                      style: theme.textTheme.bodySmall?.copyWith(color: j.encre2),
                    ),
            ],
            const SizedBox(height: 12),
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
                style: theme.textTheme.displaySmall?.copyWith(color: context.jetons.petrole)),
            Text('pour ${nombre(points)} points',
                textAlign: TextAlign.center, style: TextStyle(color: context.jetons.encre2)),
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
              style: theme.textTheme.bodySmall?.copyWith(color: context.jetons.encre2),
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
