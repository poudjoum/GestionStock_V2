import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api.dart';
import '../api/modeles.dart';
import '../design/composants.dart';
import '../design/jetons.dart';
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
                  separatorBuilder: (_, _) => const SizedBox(height: 20),
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
    final j = context.jetons;
    final reste = resteJusquA(campagne.dateFin);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (campagne.image != null)
            AspectRatio(aspectRatio: 16 / 9, child: imageDuServeur(campagne.image)),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
            child: Row(
              children: [
                AvatarMagasin(logo: campagne.logoMagasin, taille: 36),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(campagne.nomMagasin,
                          style: theme.textTheme.labelLarge, maxLines: 1, overflow: TextOverflow.ellipsis),
                      if (campagne.villeMagasin != null)
                        Text(campagne.villeMagasin!, style: theme.textTheme.bodySmall?.copyWith(color: j.encre3)),
                    ],
                  ),
                ),
                if (reste.isNotEmpty)
                  Statut(
                    ton: reste == 'Dernier jour' ? Ton.alerte : Ton.promo,
                    icone: Icons.schedule,
                    libelle: reste,
                  ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: Text(campagne.titre, style: theme.textTheme.headlineSmall),
          ),
          if (campagne.message != null && campagne.message!.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
              child: Text(campagne.message!, style: theme.textTheme.bodyMedium?.copyWith(color: j.encre2, height: 1.45)),
            ),
          if (campagne.articles.isNotEmpty) ...[
            const SizedBox(height: 8),
            for (final article in campagne.articles) _LigneArticle(article: article),
          ],
          // Ce que la campagne rapporte en plus : le geste a faire apres l'achat.
          Container(
            margin: const EdgeInsets.fromLTRB(12, 8, 12, 12),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(color: j.petroleDoux, borderRadius: BorderRadius.circular(Rayons.champ)),
            child: Row(
              children: [
                Icon(Icons.stars, size: 20, color: j.petrole),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Vos achats rapportent des points jusqu’au ${jour(campagne.dateFin)}. Scannez votre ticket !',
                    style: theme.textTheme.bodySmall?.copyWith(color: j.encre, fontWeight: FontWeight.w500),
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

/// Un article en promotion. Le prix promotionnel est le plus gros de la ligne, en petrole ; le
/// prix normal, barre, en dessous.
class _LigneArticle extends StatelessWidget {
  const _LigneArticle({required this.article});

  final ArticlePromo article;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final j = context.jetons;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: Row(
        children: [
          Container(
            width: 52,
            height: 52,
            decoration: BoxDecoration(color: j.surface2, borderRadius: BorderRadius.circular(Rayons.champ)),
            clipBehavior: Clip.antiAlias,
            child: imageDuServeur(article.photo, sinon: Icon(Icons.inventory_2_outlined, color: j.encre3)),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(article.designation,
                    maxLines: 2, overflow: TextOverflow.ellipsis, style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                const SizedBox(height: 2),
                Text(
                  francs(article.prixNormalTtc),
                  style: theme.textTheme.bodySmall?.copyWith(decoration: TextDecoration.lineThrough, color: j.encre3),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(francs(article.prixPromoTtc),
                  style: TextStyle(
                    fontFamily: Polices.titre,
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                    color: j.petrole,
                    fontFeatures: const [FontFeature.tabularFigures()],
                  )),
              if (article.remisePourcent > 0)
                Container(
                  margin: const EdgeInsets.only(top: 2),
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(color: Jetons.petroleProfond, borderRadius: BorderRadius.circular(4)),
                  child: Text('−${article.remisePourcent} %',
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Colors.white)),
                ),
            ],
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
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(16),
        physics: const NeverScrollableScrollPhysics(),
        children: const [Squelette(hauteur: 260, marge: 20), Squelette(hauteur: 260, marge: 20)],
      );
}
