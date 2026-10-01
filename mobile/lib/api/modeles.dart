/// Ce que l'API renvoie, lu une fois pour toutes.
///
/// Les montants arrivent tantot en entier, tantot en decimal : `_nombre` les ramene tous a un
/// `double`, pour que l'affichage n'ait pas a s'en soucier.
library;

double _nombre(Object? valeur) => valeur is num ? valeur.toDouble() : 0;

int _entier(Object? valeur) => valeur is num ? valeur.toInt() : 0;

DateTime? _date(Object? valeur) => valeur is String ? DateTime.tryParse(valeur)?.toLocal() : null;

/// Une campagne en cours, telle que la vitrine la montre.
class Campagne {
  Campagne.depuis(Map<String, dynamic> json)
      : id = _entier(json['id']),
        titre = json['titre'] as String? ?? '',
        message = json['message'] as String?,
        image = json['image'] as String?,
        dateDebut = _date(json['dateDebut']),
        dateFin = _date(json['dateFin']),
        idMagasin = _entier(json['idMagasin']),
        nomMagasin = json['nomMagasin'] as String? ?? '',
        villeMagasin = json['villeMagasin'] as String?,
        logoMagasin = json['logoMagasin'] as String?,
        articles = [
          for (final a in (json['articles'] as List? ?? const []))
            ArticlePromo.depuis(a as Map<String, dynamic>),
        ];

  final int id;
  final String titre;
  final String? message;
  final String? image;
  final DateTime? dateDebut;
  final DateTime? dateFin;
  final int idMagasin;
  final String nomMagasin;
  final String? villeMagasin;
  final String? logoMagasin;
  final List<ArticlePromo> articles;
}

class ArticlePromo {
  ArticlePromo.depuis(Map<String, dynamic> json)
      : designation = json['designation'] as String? ?? '',
        photo = json['photo'] as String?,
        prixNormalTtc = _nombre(json['prixNormalTtc']),
        prixPromoTtc = _nombre(json['prixPromoTtc']),
        remisePourcent = _entier(json['remisePourcent']);

  final String designation;
  final String? photo;
  final double prixNormalTtc;
  final double prixPromoTtc;
  final int remisePourcent;
}

/// Le client connecte : ses points, magasin par magasin.
class Profil {
  Profil.depuis(Map<String, dynamic> json)
      : telephone = json['telephone'] as String? ?? '',
        prenom = json['prenom'] as String?,
        nom = json['nom'] as String?,
        totalPoints = _entier(json['totalPoints']),
        soldes = [
          for (final s in (json['soldesParMagasin'] as List? ?? const []))
            Solde.depuis(s as Map<String, dynamic>),
        ];

  final String telephone;
  final String? prenom;
  final String? nom;
  final int totalPoints;
  final List<Solde> soldes;
}

/// Les points d'un client dans un magasin, et ce qu'ils valent la-bas.
class Solde {
  Solde.depuis(Map<String, dynamic> json)
      : idMagasin = _entier(json['idEntreprise']),
        nomMagasin = json['nomMagasin'] as String? ?? '',
        ville = json['ville'] as String?,
        logo = json['logo'] as String?,
        points = _entier(json['soldePoints']),
        valeurPoint = _nombre(json['valeurPointFcfa']),
        minimum = _entier(json['pointsMinimumBon']);

  final int idMagasin;
  final String nomMagasin;
  final String? ville;
  final String? logo;
  final int points;
  final double valeurPoint;
  final int minimum;

  /// Ce que vaudrait un bon pour ces points : arrondi au franc inferieur, comme le serveur.
  double valeurDe(int pointsEchanges) => (pointsEchanges * valeurPoint).floorToDouble();

  bool get echangeable => points >= minimum && minimum > 0;
}

enum StatutBon { actif, utilise, expire }

class Bon {
  Bon.depuis(Map<String, dynamic> json)
      : code = json['codeBon'] as String? ?? '',
        nomMagasin = json['nomMagasin'] as String? ?? '',
        montant = _nombre(json['montantFcfa']),
        points = _entier(json['pointsUtilises']),
        statut = switch (json['statut']) {
          'UTILISE' => StatutBon.utilise,
          'EXPIRE' => StatutBon.expire,
          _ => StatutBon.actif,
        },
        emission = _date(json['dateEmission']),
        expiration = _date(json['dateExpiration']),
        utilisation = _date(json['dateUtilisation']),
        utilisable = json['utilisable'] == true;

  final String code;
  final String nomMagasin;
  final double montant;
  final int points;
  final StatutBon statut;
  final DateTime? emission;
  final DateTime? expiration;
  final DateTime? utilisation;
  final bool utilisable;
}

class TicketScanne {
  TicketScanne.depuis(Map<String, dynamic> json)
      : nomMagasin = json['nomMagasin'] as String? ?? '',
        montant = _nombre(json['montantAchatTtc']),
        points = _entier(json['pointsAttribues']),
        date = _date(json['dateReclamation']);

  final String nomMagasin;
  final double montant;
  final int points;
  final DateTime? date;
}

/// Ce que rend le scan d'un ticket.
class ResultatScan {
  ResultatScan.depuis(Map<String, dynamic> json)
      : nomMagasin = json['nomMagasin'] as String? ?? '',
        pointsGagnes = _entier(json['pointsGagnes']),
        soldeMagasin = _entier(json['nouveauSoldeMagasin']),
        montant = _nombre(json['montantAchatTtc']);

  final String nomMagasin;
  final int pointsGagnes;
  final int soldeMagasin;
  final double montant;
}
