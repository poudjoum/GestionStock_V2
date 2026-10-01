import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import 'api/api.dart';
import 'ecrans/bons.dart';
import 'ecrans/connexion.dart';
import 'ecrans/points.dart';
import 'ecrans/promotions.dart';
import 'ecrans/scanner.dart';
import 'session.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('fr');
  final session = Session(Api());
  runApp(ChangeNotifierProvider.value(value: session, child: const Fidelite()));
  await session.demarrer();
}

/// Le vert du back-office des magasins, et son orange en accent : une seule identite.
const _vert = Color(0xFF2E7D32);

class Fidelite extends StatelessWidget {
  const Fidelite({super.key});

  ThemeData _theme(Brightness luminosite) => ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: _vert,
          brightness: luminosite,
          tertiary: luminosite == Brightness.light ? const Color(0xFFB45309) : const Color(0xFFFFB86B),
        ),
        useMaterial3: true,
      );

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Fidélité',
      debugShowCheckedModeBanner: false,
      theme: _theme(Brightness.light),
      darkTheme: _theme(Brightness.dark),
      locale: const Locale('fr'),
      supportedLocales: const [Locale('fr')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      home: const Accueil(),
    );
  }
}

/// Quatre onglets : ce qu'il y a en promotion, scanner, mes points, mes bons.
///
/// Les promotions s'ouvrent sans compte ; les trois autres demandent de se connecter, et le disent
/// a la place de l'onglet plutot que de renvoyer ailleurs.
class Accueil extends StatefulWidget {
  const Accueil({super.key});

  @override
  State<Accueil> createState() => _AccueilState();
}

class _AccueilState extends State<Accueil> {
  int _onglet = 0;

  static const _titres = ['Promotions', 'Scanner un ticket', 'Mes points', 'Mes bons d’achat'];
  static const _raisons = [
    null,
    'Connectez-vous pour scanner votre ticket et recevoir vos points.',
    'Connectez-vous pour voir vos points.',
    'Connectez-vous pour voir vos bons d’achat.',
  ];

  @override
  Widget build(BuildContext context) {
    final session = context.watch<Session>();
    if (!session.prete) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    final Widget contenu;
    if (_onglet > 0 && !session.connecte) {
      contenu = EcranConnexion(raison: _raisons[_onglet]);
    } else {
      contenu = switch (_onglet) {
        1 => const EcranScanner(),
        2 => EcranPoints(versMesBons: () => setState(() => _onglet = 3)),
        3 => const EcranBons(),
        _ => const EcranPromotions(),
      };
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(_titres[_onglet]),
        actions: [
          if (session.connecte)
            PopupMenuButton<String>(
              tooltip: 'Mon compte',
              icon: const Icon(Icons.account_circle_outlined),
              onSelected: (_) => session.deconnecter(),
              itemBuilder: (_) => [
                PopupMenuItem<String>(
                  enabled: false,
                  child: Text(session.profil?.telephone ?? 'Mon compte'),
                ),
                const PopupMenuItem<String>(value: 'sortir', child: Text('Me déconnecter')),
              ],
            ),
        ],
      ),
      // Une cle par onglet et par compte : changer de compte recharge les ecrans.
      body: KeyedSubtree(key: ValueKey('$_onglet-${session.connecte}'), child: contenu),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _onglet,
        onDestinationSelected: (i) => setState(() => _onglet = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.local_offer_outlined), selectedIcon: Icon(Icons.local_offer), label: 'Promos'),
          NavigationDestination(icon: Icon(Icons.qr_code_scanner), label: 'Scanner'),
          NavigationDestination(icon: Icon(Icons.stars_outlined), selectedIcon: Icon(Icons.stars), label: 'Mes points'),
          NavigationDestination(icon: Icon(Icons.redeem_outlined), selectedIcon: Icon(Icons.redeem), label: 'Mes bons'),
        ],
      ),
    );
  }
}
