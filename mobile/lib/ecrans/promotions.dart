import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api.dart';
import '../api/modeles.dart';
import '../outils.dart';
import '../session.dart';

/// Les campagnes en cours, tous magasins : ce qu'on vient voir en ouvrant l'application.
///
/// Ouvert sans compte. Ce qu'on cherche d'un coup d'oeil, c'est le prix promotionnel et ce qu'il
/// fait gagner : il est le plus gros de la carte, a cote du prix normal barre.
class EcranPromotions extends StatefulWidget {
  const EcranPromotions({super.key});

  @override
  State<EcranPromotions> createState() => _EcranPromotionsState();
}

class _EcranPromotionsState extends State<EcranPromotions> {
  late Future<List<Campagne>> _campagnes;

  @override
  void initState() {
    super.initState();
    _campagnes = context.read<Session>().api.campagnes();
  }

  Future<void> _recharger() async {
    final suivantes = context.read<Session>().api.campagnes();
    setState(() => _campagnes = suivantes);
    await suivantes.catchError((_) => <Campagne>[]);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<Campagne>>(
      future: _campagnes,
      builder: (context, etat) {
        if (etat.connectionState != ConnectionState.done) {
          return const _Squelette();
        }
        if (etat.hasError) {
          return EtatErreur(
            message: etat.error is ErreurApi ? etat.error.toString() : 'Les promotions n’ont pas pu être lues.',
            reessayer: _recharger,
          );
        }
        final campagnes = etat.data!;
        return RefreshIndicator(
          onRefresh: _recharger,
          child: campagnes.isEmpty
              ? ListView(children: const [
                  SizedBox(height: 80),
                  EtatVide(
                    icone: Icons.local_offer_outlined,
                    titre: 'Pas de promotion en ce moment',
                    texte: 'Les magasins partenaires publient ici leurs campagnes. Revenez bientôt !',
                  ),
                ])
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                  itemCount: campagnes.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 16),
                  itemBuilder: (_, i) => _CarteCampagne(campagne: campagnes[i]),
                ),
        );
      },
    );
  }
}

class _CarteCampagne extends StatelessWidget {
  const _CarteCampagne({required this.campagne});

  final Campagne campagne;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final couleurs = theme.colorScheme;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (campagne.image != null)
            AspectRatio(aspectRatio: 16 / 9, child: imageDuServeur(campagne.image)),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 4),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 18,
                  backgroundColor: couleurs.surfaceContainerHighest,
                  child: ClipOval(
                    child: SizedBox.square(
                      dimension: 36,
                      child: imageDuServeur(campagne.logoMagasin,
                          fit: BoxFit.contain, sinon: Icon(Icons.storefront, color: couleurs.primary)),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(campagne.nomMagasin, style: theme.textTheme.labelLarge),
                      if (campagne.villeMagasin != null)
                        Text(campagne.villeMagasin!,
                            style: theme.textTheme.bodySmall?.copyWith(color: couleurs.onSurfaceVariant)),
                    ],
                  ),
                ),
                Chip(
                  label: Text(resteJusquA(campagne.dateFin)),
                  avatar: const Icon(Icons.schedule, size: 16),
                  visualDensity: VisualDensity.compact,
                  backgroundColor: couleurs.tertiaryContainer,
                  labelStyle: TextStyle(color: couleurs.onTertiaryContainer),
                  side: BorderSide.none,
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Text(campagne.titre, style: theme.textTheme.titleLarge),
          ),
          if (campagne.message != null && campagne.message!.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
              child: Text(campagne.message!, style: theme.textTheme.bodyMedium),
            ),
          if (campagne.articles.isNotEmpty) ...[
            const SizedBox(height: 8),
            for (final article in campagne.articles) _LigneArticle(article: article),
          ],
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 14),
            child: Row(
              children: [
                Icon(Icons.stars, size: 18, color: couleurs.primary),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    'Vos achats rapportent des points jusqu’au ${jour(campagne.dateFin)}. Scannez votre ticket !',
                    style: theme.textTheme.bodySmall?.copyWith(color: couleurs.primary),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _LigneArticle extends StatelessWidget {
  const _LigneArticle({required this.article});

  final ArticlePromo article;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final couleurs = theme.colorScheme;
    return ListTile(
      leading: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: couleurs.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(8),
        ),
        clipBehavior: Clip.antiAlias,
        child: imageDuServeur(article.photo, sinon: Icon(Icons.inventory_2_outlined, color: couleurs.outline)),
      ),
      title: Text(article.designation, maxLines: 2, overflow: TextOverflow.ellipsis),
      subtitle: Text(
        francs(article.prixNormalTtc),
        style: TextStyle(decoration: TextDecoration.lineThrough, color: couleurs.onSurfaceVariant),
      ),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text(francs(article.prixPromoTtc),
              style: theme.textTheme.titleMedium?.copyWith(color: couleurs.primary, fontWeight: FontWeight.w600)),
          if (article.remisePourcent > 0)
            Container(
              margin: const EdgeInsets.only(top: 2),
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              decoration: BoxDecoration(color: couleurs.primary, borderRadius: BorderRadius.circular(4)),
              child: Text('−${article.remisePourcent} %',
                  style: theme.textTheme.labelSmall?.copyWith(color: couleurs.onPrimary)),
            ),
        ],
      ),
    );
  }
}

/// La forme des cartes, le temps qu'elles arrivent : la page ne saute pas a l'arrivee.
class _Squelette extends StatelessWidget {
  const _Squelette();

  @override
  Widget build(BuildContext context) {
    final fond = Theme.of(context).colorScheme.surfaceContainerHighest;
    return ListView(
      padding: const EdgeInsets.all(16),
      physics: const NeverScrollableScrollPhysics(),
      children: [
        for (var i = 0; i < 3; i++)
          Container(
            height: 220,
            margin: const EdgeInsets.only(bottom: 16),
            decoration: BoxDecoration(color: fond, borderRadius: BorderRadius.circular(12)),
          ),
      ],
    );
  }
}
