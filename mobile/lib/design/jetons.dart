import 'package:flutter/material.dart';

/// Les jetons de GestionStock, les memes que ceux du back-office (`frontend/src/design/jetons.css`).
///
/// Le client qui voit son ticket imprime au comptoir puis ouvre l'application doit reconnaitre le
/// meme magasin : memes verts, memes polices, meme sens des couleurs. Changer une teinte, c'est la
/// changer ici et dans le CSS — et nulle part ailleurs.
///
/// Le vert est la marque ; le bleu-petrole est la couleur de ce qui parle au client — points,
/// promotions, bons. Dans cette application, c'est donc lui qui porte les chiffres qu'on vient
/// chercher. Le vert reste aux boutons et a la navigation.
@immutable
class Jetons extends ThemeExtension<Jetons> {
  const Jetons({
    required this.fond,
    required this.surface,
    required this.surface2,
    required this.trait,
    required this.encre,
    required this.encre2,
    required this.encre3,
    required this.accent,
    required this.surAccent,
    required this.accentDoux,
    required this.surAccentDoux,
    required this.petrole,
    required this.petroleDoux,
    required this.succes,
    required this.succesFond,
    required this.alerte,
    required this.alerteFond,
    required this.danger,
    required this.dangerFond,
  });

  final Color fond;
  final Color surface;
  final Color surface2;
  final Color trait;
  final Color encre;
  final Color encre2;
  final Color encre3;
  final Color accent;
  final Color surAccent;
  final Color accentDoux;
  final Color surAccentDoux;
  final Color petrole;
  final Color petroleDoux;
  final Color succes;
  final Color succesFond;
  final Color alerte;
  final Color alerteFond;
  final Color danger;
  final Color dangerFond;

  static const clair = Jetons(
    fond: Color(0xFFF6F7F3),
    surface: Color(0xFFFFFFFF),
    surface2: Color(0xFFEEF1EC),
    trait: Color(0xFFD9DFD9),
    encre: Color(0xFF17211B),
    encre2: Color(0xFF47544C),
    encre3: Color(0xFF66736B),
    accent: Color(0xFF1D6B43),
    surAccent: Color(0xFFFFFFFF),
    accentDoux: Color(0xFFDDEFE3),
    surAccentDoux: Color(0xFF0F3D26),
    petrole: Color(0xFF0B6A80),
    petroleDoux: Color(0xFFD7EEF3),
    succes: Color(0xFF1E7A45),
    succesFond: Color(0xFFE3F3E8),
    alerte: Color(0xFF9A5B06),
    alerteFond: Color(0xFFFDF1DC),
    danger: Color(0xFFB42318),
    dangerFond: Color(0xFFFDECEB),
  );

  static const sombre = Jetons(
    fond: Color(0xFF0F1512),
    surface: Color(0xFF161E19),
    surface2: Color(0xFF1D2721),
    trait: Color(0xFF2C3830),
    encre: Color(0xFFE6ECE7),
    encre2: Color(0xFFB3C0B7),
    encre3: Color(0xFF8F9D94),
    accent: Color(0xFF5CC489),
    surAccent: Color(0xFF08200F),
    accentDoux: Color(0xFF173A27),
    surAccentDoux: Color(0xFFC9ECD6),
    petrole: Color(0xFF58C4D8),
    petroleDoux: Color(0xFF123A44),
    succes: Color(0xFF6FD29A),
    succesFond: Color(0xFF133523),
    alerte: Color(0xFFF2B45A),
    alerteFond: Color(0xFF3A2A10),
    danger: Color(0xFFFF8A7D),
    dangerFond: Color(0xFF3D1714),
  );

  /// Le petrole toujours sombre, pour les aplats ou le texte est blanc dans les deux themes.
  static const petroleProfond = Color(0xFF0B6A80);

  @override
  Jetons copyWith() => this;

  @override
  Jetons lerp(ThemeExtension<Jetons>? autre, double t) => t < 0.5 ? this : (autre as Jetons? ?? this);
}

/// Les polices, embarquees avec l'application : un client sans reseau garde les siennes.
abstract final class Polices {
  static const titre = 'Bricolage Grotesque';
  static const texte = 'Public Sans';
  static const code = 'JetBrains Mono';
}

extension JetonsDuTheme on BuildContext {
  Jetons get jetons => Theme.of(this).extension<Jetons>()!;
}

/// Rayons et espacements : la grille de 4 du back-office.
abstract final class Rayons {
  static const champ = 8.0;
  static const carte = 12.0;
  static const grand = 20.0;
}

/// Le theme Material, construit a partir des jetons plutot que d'une couleur graine : une graine
/// recalcule ses propres teintes, et l'application n'aurait plus tout a fait le vert du magasin.
ThemeData themeGestionStock(Brightness luminosite) {
  final j = luminosite == Brightness.light ? Jetons.clair : Jetons.sombre;
  final schema = ColorScheme(
    brightness: luminosite,
    primary: j.accent,
    onPrimary: j.surAccent,
    primaryContainer: j.accentDoux,
    onPrimaryContainer: j.surAccentDoux,
    secondary: j.accent,
    onSecondary: j.surAccent,
    secondaryContainer: j.accentDoux,
    onSecondaryContainer: j.surAccentDoux,
    tertiary: j.petrole,
    onTertiary: luminosite == Brightness.light ? Colors.white : const Color(0xFF04232B),
    tertiaryContainer: j.petroleDoux,
    onTertiaryContainer: j.encre,
    error: j.danger,
    onError: luminosite == Brightness.light ? Colors.white : const Color(0xFF3D1714),
    errorContainer: j.dangerFond,
    onErrorContainer: j.danger,
    surface: j.fond,
    onSurface: j.encre,
    onSurfaceVariant: j.encre2,
    surfaceContainerLowest: j.surface,
    surfaceContainerLow: j.surface,
    surfaceContainer: j.surface,
    surfaceContainerHigh: j.surface2,
    surfaceContainerHighest: j.surface2,
    outline: j.encre3,
    outlineVariant: j.trait,
  );

  final base = ThemeData(useMaterial3: true, colorScheme: schema, fontFamily: Polices.texte);
  final texte = base.textTheme;
  TextStyle? titre(TextStyle? s, {FontWeight poids = FontWeight.w700}) =>
      s?.copyWith(fontFamily: Polices.titre, fontWeight: poids, letterSpacing: -0.2, color: j.encre);

  return base.copyWith(
    scaffoldBackgroundColor: j.fond,
    extensions: [j],
    textTheme: texte.copyWith(
      displayLarge: titre(texte.displayLarge, poids: FontWeight.w800),
      displayMedium: titre(texte.displayMedium, poids: FontWeight.w800),
      displaySmall: titre(texte.displaySmall, poids: FontWeight.w800),
      headlineLarge: titre(texte.headlineLarge),
      headlineMedium: titre(texte.headlineMedium),
      headlineSmall: titre(texte.headlineSmall),
      titleLarge: titre(texte.titleLarge),
      titleMedium: texte.titleMedium?.copyWith(fontWeight: FontWeight.w600),
      labelLarge: texte.labelLarge?.copyWith(fontWeight: FontWeight.w600),
    ),
    appBarTheme: AppBarTheme(
      backgroundColor: j.fond,
      foregroundColor: j.encre,
      surfaceTintColor: Colors.transparent,
      scrolledUnderElevation: 0,
      centerTitle: false,
      titleTextStyle: TextStyle(fontFamily: Polices.titre, fontSize: 22, fontWeight: FontWeight.w700, color: j.encre),
    ),
    cardTheme: CardThemeData(
      color: j.surface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Rayons.carte),
        side: BorderSide(color: j.trait),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(64, 48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Rayons.champ)),
        textStyle: const TextStyle(fontFamily: Polices.texte, fontWeight: FontWeight.w600, fontSize: 15),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(64, 48),
        side: BorderSide(color: j.trait),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Rayons.champ)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(textStyle: const TextStyle(fontFamily: Polices.texte, fontWeight: FontWeight.w600)),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: j.surface,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(Rayons.champ)),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Rayons.champ),
        borderSide: BorderSide(color: j.trait),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Rayons.champ),
        borderSide: BorderSide(color: j.accent, width: 2),
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: j.surface,
      surfaceTintColor: Colors.transparent,
      indicatorColor: j.accentDoux,
      elevation: 0,
      height: 68,
      iconTheme: WidgetStateProperty.resolveWith(
        (etats) => IconThemeData(color: etats.contains(WidgetState.selected) ? j.surAccentDoux : j.encre2),
      ),
      labelTextStyle: WidgetStateProperty.resolveWith(
        (etats) => TextStyle(
          fontFamily: Polices.texte,
          fontSize: 12,
          fontWeight: etats.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500,
          color: etats.contains(WidgetState.selected) ? j.encre : j.encre2,
        ),
      ),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: j.surface,
      surfaceTintColor: Colors.transparent,
      showDragHandle: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(Rayons.grand))),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: j.surface,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Rayons.grand)),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Rayons.champ)),
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(color: j.accent, linearTrackColor: j.surface2),
    sliderTheme: SliderThemeData(activeTrackColor: j.petrole, thumbColor: j.petrole, inactiveTrackColor: j.surface2),
    dividerTheme: DividerThemeData(color: j.trait, space: 1),
  );
}
