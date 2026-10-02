import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../outils.dart';
import 'jetons.dart';

/// Le logo de l'application : la caisse de GestionStock, une etoile sur le couvercle, sur le
/// bleu-petrole. Dessine plutot qu'importe : il reste net a toutes les tailles, sans fichier.
///
/// Le meme trace que `frontend/public/logo.svg` (viewBox 32), et que l'icone du lanceur.
class Logo extends StatelessWidget {
  const Logo({super.key, this.taille = 40});

  final double taille;

  @override
  Widget build(BuildContext context) =>
      SizedBox.square(dimension: taille, child: CustomPaint(painter: _PeintreLogo()));
}

class _PeintreLogo extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final e = size.width / 32;
    Offset p(double x, double y) => Offset(x * e, y * e);
    Path face(List<Offset> points) => Path()..addPolygon(points, true);

    canvas.drawRRect(
      RRect.fromRectAndRadius(Offset.zero & size, Radius.circular(8 * e)),
      Paint()..color = Jetons.petroleProfond,
    );
    canvas.drawPath(face([p(16, 6.5), p(25.5, 11.25), p(16, 16), p(6.5, 11.25)]), Paint()..color = Colors.white);
    canvas.drawPath(face([p(6.5, 12.75), p(15.25, 17.1), p(15.25, 26.5), p(6.5, 22.1)]),
        Paint()..color = Colors.white.withValues(alpha: 0.82));
    canvas.drawPath(face([p(25.5, 12.75), p(16.75, 17.1), p(16.75, 26.5), p(25.5, 22.1)]),
        Paint()..color = Colors.white.withValues(alpha: 0.6));

    // L'etoile, posee a plat sur le couvercle : ecrasee de moitie en hauteur.
    final etoile = <Offset>[];
    for (var i = 0; i < 10; i++) {
      final r = (i.isEven ? 4.2 : 4.2 * 0.45);
      final a = -math.pi / 2 + i * math.pi / 5;
      etoile.add(p(16 + r * math.cos(a), 11.25 + r * math.sin(a) * 0.5));
    }
    canvas.drawPath(face(etoile), Paint()..color = Jetons.petroleProfond);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Ce que dit un statut, comme `<gs-statut>` du back-office : une icone et un mot, sur un fond
/// doux. Jamais la couleur seule : elle ne dit rien a un daltonien.
enum Ton { ok, alerte, danger, promo, neutre }

class Statut extends StatelessWidget {
  const Statut({super.key, required this.ton, required this.libelle, this.icone});

  final Ton ton;
  final String libelle;
  final IconData? icone;

  @override
  Widget build(BuildContext context) {
    final j = context.jetons;
    final (fond, encre) = switch (ton) {
      Ton.ok => (j.succesFond, j.succes),
      Ton.alerte => (j.alerteFond, j.alerte),
      Ton.danger => (j.dangerFond, j.danger),
      Ton.promo => (j.petroleDoux, j.petrole),
      Ton.neutre => (j.surface2, j.encre2),
    };
    return Container(
      padding: EdgeInsets.fromLTRB(icone == null ? 8 : 6, 3, 8, 3),
      decoration: BoxDecoration(color: fond, borderRadius: BorderRadius.circular(999)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icone != null) ...[Icon(icone, size: 14, color: encre), const SizedBox(width: 4)],
          Text(libelle,
              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: encre, height: 1.2)),
        ],
      ),
    );
  }
}

/// L'etiquette d'une section : « PAR MAGASIN », « MES TICKETS ». Elle nomme, elle ne crie pas.
class Etiquette extends StatelessWidget {
  const Etiquette(this.texte, {super.key});

  final String texte;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text(
          texte.toUpperCase(),
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.8,
            color: context.jetons.encre3,
          ),
        ),
      );
}

/// Le logo d'un magasin dans une pastille, ou une vitrine s'il n'en a pas.
class AvatarMagasin extends StatelessWidget {
  const AvatarMagasin({super.key, this.logo, this.taille = 40});

  final String? logo;
  final double taille;

  @override
  Widget build(BuildContext context) {
    final j = context.jetons;
    return Container(
      width: taille,
      height: taille,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(taille / 4),
        border: Border.all(color: j.trait),
      ),
      clipBehavior: Clip.antiAlias,
      padding: const EdgeInsets.all(3),
      child: imageDuServeur(logo,
          fit: BoxFit.contain,
          sinon: Icon(Icons.storefront, size: taille * 0.55, color: Jetons.clair.accent)),
    );
  }
}

/// Un etat vide qui dit quoi faire, et non « aucune donnée ».
class EtatVide extends StatelessWidget {
  const EtatVide({super.key, required this.icone, required this.titre, this.texte, this.action});

  final IconData icone;
  final String titre;
  final String? texte;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final j = context.jetons;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(color: j.surface2, shape: BoxShape.circle),
              child: Icon(icone, size: 34, color: j.encre3),
            ),
            const SizedBox(height: 16),
            Text(titre, style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
            if (texte != null) ...[
              const SizedBox(height: 6),
              Text(texte!,
                  style: theme.textTheme.bodyMedium?.copyWith(color: j.encre2, height: 1.45),
                  textAlign: TextAlign.center),
            ],
            if (action != null) ...[const SizedBox(height: 20), action!],
          ],
        ),
      ),
    );
  }
}

/// Le message du serveur, tel quel, avec de quoi reessayer.
class EtatErreur extends StatelessWidget {
  const EtatErreur({super.key, required this.message, required this.reessayer});

  final String message;
  final VoidCallback reessayer;

  @override
  Widget build(BuildContext context) => EtatVide(
        icone: Icons.cloud_off,
        titre: 'Rien à afficher pour l’instant',
        texte: message,
        action: OutlinedButton.icon(onPressed: reessayer, icon: const Icon(Icons.refresh), label: const Text('Réessayer')),
      );
}

/// Un message d'erreur dans un formulaire : celui du serveur, avec une icone.
class MessageErreur extends StatelessWidget {
  const MessageErreur(this.message, {super.key});

  final String message;

  @override
  Widget build(BuildContext context) {
    final j = context.jetons;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(color: j.dangerFond, borderRadius: BorderRadius.circular(Rayons.champ)),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.error_outline, size: 18, color: j.danger),
          const SizedBox(width: 8),
          Expanded(child: Text(message, style: TextStyle(color: j.danger, fontSize: 14, height: 1.4))),
        ],
      ),
    );
  }
}

/// Un bloc gris a la forme du contenu, le temps qu'il arrive : la page ne saute pas.
class Squelette extends StatelessWidget {
  const Squelette({super.key, required this.hauteur, this.marge = 12});

  final double hauteur;
  final double marge;

  @override
  Widget build(BuildContext context) => Container(
        height: hauteur,
        margin: EdgeInsets.only(bottom: marge),
        decoration: BoxDecoration(color: context.jetons.surface2, borderRadius: BorderRadius.circular(Rayons.carte)),
      );
}
