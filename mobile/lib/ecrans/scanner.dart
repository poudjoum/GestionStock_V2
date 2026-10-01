import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:provider/provider.dart';

import '../api/api.dart';
import '../api/modeles.dart';
import '../outils.dart';
import '../session.dart';

/// Scanner un ticket de caisse pour en recevoir les points.
///
/// La camera d'abord : c'est le geste. Le code se tape aussi, pour un QR tache ou une camera qui
/// refuse de faire le point — il est imprime en clair sous le QR.
class EcranScanner extends StatefulWidget {
  const EcranScanner({super.key});

  @override
  State<EcranScanner> createState() => _EcranScannerState();
}

class _EcranScannerState extends State<EcranScanner> {
  final _camera = MobileScannerController(formats: const [BarcodeFormat.qrCode]);
  bool _envoi = false;
  /// Le dernier code envoye : la camera le relit plusieurs fois par seconde.
  String? _dernierCode;

  @override
  void dispose() {
    _camera.dispose();
    super.dispose();
  }

  void _surDetection(BarcodeCapture capture) {
    for (final lu in capture.barcodes) {
      final code = lu.rawValue == null ? null : codeDeTicket(lu.rawValue!);
      if (code != null && code != _dernierCode) {
        _reclamer(code);
        return;
      }
    }
  }

  Future<void> _saisirLeCode() async {
    final code = await showDialog<String>(context: context, builder: (_) => const _SaisieDuCode());
    if (code != null) {
      await _reclamer(code);
    }
  }

  Future<void> _reclamer(String code) async {
    if (_envoi) {
      return;
    }
    setState(() {
      _envoi = true;
      _dernierCode = code;
    });
    final session = context.read<Session>();
    try {
      final resultat = await session.api.scanner(code);
      await session.rafraichir().catchError((_) {});
      if (mounted) {
        await _montrer(succes: resultat);
      }
    } on ErreurApi catch (e) {
      if (mounted) {
        await _montrer(erreur: e.message);
      }
    } finally {
      if (mounted) {
        setState(() => _envoi = false);
      }
    }
  }

  Future<void> _montrer({ResultatScan? succes, String? erreur}) {
    return showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (context) => _Resultat(succes: succes, erreur: erreur),
    ).whenComplete(() => _dernierCode = null);
  }

  @override
  Widget build(BuildContext context) {
    final couleurs = Theme.of(context).colorScheme;
    return Stack(
      fit: StackFit.expand,
      children: [
        MobileScanner(
          controller: _camera,
          onDetect: _surDetection,
          errorBuilder: (context, erreur) => EtatVide(
            icone: Icons.no_photography_outlined,
            titre: 'La caméra n’est pas disponible',
            texte: 'Autorisez l’accès à la caméra dans les réglages, ou tapez le code imprimé sous le QR.',
            action: FilledButton.icon(
              onPressed: _saisirLeCode,
              icon: const Icon(Icons.keyboard),
              label: const Text('Taper le code'),
            ),
          ),
        ),
        // Le cadre de visee : on sait ou placer le ticket sans lire de consigne.
        IgnorePointer(
          child: Center(
            child: Container(
              width: 240,
              height: 240,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.white, width: 3),
                borderRadius: BorderRadius.circular(20),
              ),
            ),
          ),
        ),
        Positioned(
          left: 16,
          right: 16,
          top: 16,
          child: Material(
            color: Colors.black54,
            borderRadius: BorderRadius.circular(12),
            child: const Padding(
              padding: EdgeInsets.all(12),
              child: Text(
                'Visez le QR imprimé en bas de votre ticket de caisse',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.white),
              ),
            ),
          ),
        ),
        Positioned(
          left: 16,
          right: 16,
          bottom: 24,
          child: _envoi
              ? Center(child: CircularProgressIndicator(color: couleurs.onPrimary))
              : FilledButton.tonalIcon(
                  onPressed: _saisirLeCode,
                  style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                  icon: const Icon(Icons.keyboard),
                  label: const Text('Taper le code du ticket'),
                ),
        ),
      ],
    );
  }
}

class _SaisieDuCode extends StatefulWidget {
  const _SaisieDuCode();

  @override
  State<_SaisieDuCode> createState() => _SaisieDuCodeState();
}

class _SaisieDuCodeState extends State<_SaisieDuCode> {
  final _champ = TextEditingController();
  String? _erreur;

  @override
  void dispose() {
    _champ.dispose();
    super.dispose();
  }

  void _valider() {
    final code = codeDeTicket(_champ.text);
    if (code == null) {
      setState(() => _erreur = 'Douze caractères, comme « 7K3M-9P2Q-A4TZ »');
      return;
    }
    Navigator.pop(context, code);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Code du ticket'),
      content: TextField(
        controller: _champ,
        autofocus: true,
        textCapitalization: TextCapitalization.characters,
        decoration: InputDecoration(
          hintText: 'XXXX-XXXX-XXXX',
          errorText: _erreur,
          border: const OutlineInputBorder(),
        ),
        onSubmitted: (_) => _valider(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuler')),
        FilledButton(onPressed: _valider, child: const Text('Valider')),
      ],
    );
  }
}

class _Resultat extends StatelessWidget {
  const _Resultat({this.succes, this.erreur});

  final ResultatScan? succes;
  final String? erreur;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final couleurs = theme.colorScheme;
    final ok = succes != null;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(ok ? Icons.celebration : Icons.info_outline, size: 48, color: ok ? couleurs.primary : couleurs.error),
            const SizedBox(height: 12),
            if (ok) ...[
              Text('+${succes!.pointsGagnes} points',
                  style: theme.textTheme.displaySmall?.copyWith(color: couleurs.primary, fontWeight: FontWeight.w600)),
              const SizedBox(height: 4),
              Text('chez ${succes!.nomMagasin}', style: theme.textTheme.titleMedium),
              const SizedBox(height: 8),
              Text('Achat de ${francs(succes!.montant)} · solde dans ce magasin : ${nombre(succes!.soldeMagasin)} points',
                  textAlign: TextAlign.center, style: theme.textTheme.bodyMedium),
            ] else
              Text(erreur ?? 'Ce ticket n’a pas pu être enregistré.',
                  textAlign: TextAlign.center, style: theme.textTheme.titleMedium),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: () => Navigator.pop(context),
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
              child: Text(ok ? 'Super !' : 'Compris'),
            ),
          ],
        ),
      ),
    );
  }
}
