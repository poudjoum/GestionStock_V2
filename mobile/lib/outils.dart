import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

final _francs = NumberFormat.decimalPattern('fr');

/// « 4 770 F » : le franc CFA s'ecrit sans decimales.
String francs(num montant) => '${_francs.format(montant.round())} F';

String nombre(num valeur) => _francs.format(valeur);

String jour(DateTime? date) => date == null ? '—' : DateFormat('d MMM y', 'fr').format(date);

/// Combien de jours restent avant une date, dit comme on le dit.
String resteJusquA(DateTime? fin) {
  if (fin == null) {
    return '';
  }
  final aujourdhui = DateUtils.dateOnly(DateTime.now());
  final jours = DateUtils.dateOnly(fin).difference(aujourdhui).inDays;
  if (jours < 0) {
    return 'Terminée';
  }
  if (jours == 0) {
    return 'Dernier jour';
  }
  return jours == 1 ? 'Encore 1 jour' : 'Encore $jours jours';
}

/// Une image venue du serveur : encodee dans la reponse (logo, affiche) ou a une adresse.
Widget imageDuServeur(String? source, {BoxFit fit = BoxFit.cover, Widget? sinon}) {
  final vide = sinon ?? const SizedBox.shrink();
  if (source == null || source.isEmpty) {
    return vide;
  }
  if (source.startsWith('data:')) {
    final virgule = source.indexOf(',');
    try {
      return Image.memory(base64Decode(source.substring(virgule + 1)),
          fit: fit, gaplessPlayback: true, errorBuilder: (_, _, _) => vide);
    } catch (_) {
      return vide;
    }
  }
  if (source.startsWith('http')) {
    return Image.network(source, fit: fit, errorBuilder: (_, _, _) => vide);
  }
  return vide;
}
