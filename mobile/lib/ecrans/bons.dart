import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../api/modeles.dart';
import '../design/composants.dart';
import '../design/jetons.dart';
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
          return ListView(
            padding: const EdgeInsets.all(16),
            physics: const NeverScrollableScrollPhysics(),
            children: const [Squelette(hauteur: 104), Squelette(hauteur: 104)],
          );
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
                      const Etiquette('Utilisés ou expirés'),
                      for (final bon in passes) _CarteBon(bon: bon),
                    ],
                  ],
                ),
        );
      },
    );
  }
}

/// Un bon, dessine comme un coupon : le montant sur le petrole a gauche, le magasin a droite.
class _CarteBon extends StatelessWidget {
  const _CarteBon({required this.bon});

  final Bon bon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final j = context.jetons;
    final statut = switch (bon.statut) {
      StatutBon.utilise => Statut(ton: Ton.neutre, icone: Icons.check, libelle: 'Utilisé le ${jour(bon.utilisation)}'),
      StatutBon.expire => Statut(ton: Ton.neutre, icone: Icons.event_busy, libelle: 'Expiré le ${jour(bon.expiration)}'),
      StatutBon.actif => Statut(ton: Ton.ok, icone: Icons.event_available, libelle: 'Jusqu’au ${jour(bon.expiration)}'),
    };
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: bon.utilisable
            ? () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => _BonEnGrand(bon: bon)))
            : null,
        child: IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Container(
                width: 112,
                color: bon.utilisable ? Jetons.petroleProfond : j.surface2,
                alignment: Alignment.center,
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 16),
                child: FittedBox(
                  child: Text(
                    francs(bon.montant),
                    style: TextStyle(
                      fontFamily: Polices.titre,
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                      color: bon.utilisable ? Colors.white : j.encre3,
                    ),
                  ),
                ),
              ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(14, 12, 8, 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text('Bon d’achat', style: theme.textTheme.bodySmall?.copyWith(color: j.encre3)),
                      Text(bon.nomMagasin,
                          style: theme.textTheme.titleMedium, maxLines: 1, overflow: TextOverflow.ellipsis),
                      const SizedBox(height: 6),
                      statut,
                    ],
                  ),
                ),
              ),
              if (bon.utilisable)
                Padding(
                  padding: const EdgeInsets.only(right: 12),
                  child: Icon(Icons.qr_code_2, size: 32, color: j.encre2),
                ),
            ],
          ),
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
      appBar: AppBar(title: Text(bon.nomMagasin, overflow: TextOverflow.ellipsis)),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              children: [
                Text('Bon d’achat', style: theme.textTheme.titleMedium?.copyWith(color: context.jetons.encre2)),
                Text(francs(bon.montant), style: theme.textTheme.displayMedium?.copyWith(color: context.jetons.petrole)),
                const SizedBox(height: 24),
                // Fond blanc, quel que soit le theme : une douchette lit mal un QR sur fond sombre.
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)),
                  child: QrImageView(data: bon.code, size: 240, backgroundColor: Colors.white),
                ),
                const SizedBox(height: 16),
                SelectableText(bon.code,
                    style: theme.textTheme.headlineSmall?.copyWith(
                        letterSpacing: 2, fontFamily: Polices.code, fontWeight: FontWeight.w500)),
                const SizedBox(height: 8),
                Text('Valable jusqu’au ${jour(bon.expiration)}', style: theme.textTheme.bodyMedium),
                const SizedBox(height: 24),
                Text(
                  'Montrez ce bon au caissier avant de payer : il le déduit de votre total. '
                  'Il ne rend pas la monnaie.',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodySmall?.copyWith(color: context.jetons.encre2, height: 1.45),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
