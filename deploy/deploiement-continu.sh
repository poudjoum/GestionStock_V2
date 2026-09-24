#!/usr/bin/env bash
#
# Deploie master des que ses tests sont verts.
#
# Tourne sur le serveur, appele chaque minute par un timer systemd. Il ne recoit rien de
# l'exterieur : c'est lui qui va voir. Le serveur n'ouvre donc aucun port, n'enregistre aucun
# runner, et ne detient aucun jeton — le depot etant public, tout ce qu'il lit est public.
#
# Trois etats possibles a chaque reveil :
#   - master n'a pas bouge depuis le dernier deploiement : on ne fait rien ;
#   - master a bouge mais ses tests sont en cours ou echoues : on ne fait rien, on redira ;
#   - master a bouge et ses tests sont verts : on tire, on construit, on relance.
#
# La conclusion des tests vient de GitHub, pas d'ici : le serveur n'a ni JDK ni Maven, et rien
# ne serait gagne a lui faire refaire ce que la machine d'integration vient de faire.
#
# Il ne deploie qu'aux heures ou le magasin est ferme. Un deploiement remplace le conteneur de
# l'API : pendant les quelques minutes de la reconstruction, la caisse ne repond plus. C'est
# arrive en plein service, et un caissier est reste devant un bouton qui n'aboutissait pas.
#
#     ./deploiement-continu.sh [--maintenant]
#
# --maintenant : deploie master tout de suite, sans regarder ni les tests ni l'heure. Pour le jour
# ou GitHub est en panne et ou il faut quand meme livrer un correctif. A n'employer que la.
set -euo pipefail

DEPOT=${DEPOT:-poudjoum/GestionStock_V2}
BRANCHE=${BRANCHE:-master}
RACINE=${RACINE:-$HOME/apps/gestionstock}
SOURCE="$RACINE/source"
# La derniere version reellement deployee. Sans elle, le script ne saurait pas distinguer
# « rien n'a bouge » de « je viens de demarrer ».
TEMOIN="$RACINE/.deployee"
VERROU="$RACINE/.deploiement.lock"

# La plage ou le deploiement est permis : de 22 h a 6 h, magasin ferme. Elle passe minuit, d'ou
# le « ou » dans le test plus bas plutot qu'un encadrement.
PLAGE_DEBUT=${PLAGE_DEBUT:-22}
PLAGE_FIN=${PLAGE_FIN:-6}

# Le fuseau du magasin, nomme plutot que deduit de l'horloge du serveur.
#
# Une machine reglee en UTC decalerait la plage d'une heure sans que rien ne le signale : le
# deploiement partirait a 23 h locales, et le magasin qui ferme a 22 h 30 y passerait encore.
FUSEAU=${FUSEAU:-Africa/Douala}

force=${1:-}

journal() { printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

# Deux reveils qui se chevauchent construiraient la meme image en meme temps : le second attend
# son tour, et trouvera le travail fait.
exec 9>"$VERROU"
flock -n 9 || { journal "Un deploiement est deja en cours, on laisse faire."; exit 0; }

command -v jq >/dev/null || {
    journal "ERREUR : jq est absent. « sudo apt install jq » et ce script repart."
    exit 1
}

cd "$SOURCE"
git fetch --quiet origin "$BRANCHE"
attendue=$(git rev-parse "origin/$BRANCHE")
deployee=$(cat "$TEMOIN" 2>/dev/null || echo "")

if [ "$attendue" = "$deployee" ]; then
    exit 0
fi

if [ "$force" != "--maintenant" ]; then
    # Les verifications de ce commit, telles que GitHub les rend. Le depot etant public, aucune
    # authentification n'est necessaire ; l'appel n'a lieu que lorsque la branche a bouge, donc
    # rarement, et reste loin de la limite horaire des appels anonymes.
    verifications=$(curl -fsS \
        -H 'Accept: application/vnd.github+json' \
        "https://api.github.com/repos/$DEPOT/commits/$attendue/check-runs" 2>/dev/null) || {
        journal "GitHub injoignable : on redira au prochain reveil."
        exit 0
    }

    total=$(jq -r '.total_count' <<<"$verifications")
    if [ "$total" = "0" ]; then
        journal "${attendue:0:8} : les tests n'ont pas encore demarre."
        exit 0
    fi

    # « En cours » se teste AVANT « en echec », et l'ordre n'est pas indifferent : une
    # verification qui tourne encore n'a pas de conclusion, donc le filtre des echecs la compte.
    # Intervertir ces deux blocs ferait passer « les tests tournent » pour « les tests ont
    # echoue », et plus rien ne se deploierait sans qu'on comprenne pourquoi.
    encours=$(jq -r '[.check_runs[] | select(.status != "completed")] | length' <<<"$verifications")
    if [ "$encours" != "0" ]; then
        journal "${attendue:0:8} : $encours verification(s) en cours."
        exit 0
    fi

    echoues=$(jq -r '[.check_runs[] | select(.conclusion != "success" and .conclusion != "skipped" and .conclusion != "neutral")] | length' <<<"$verifications")
    if [ "$echoues" != "0" ]; then
        # On ne redit pas ce refus a chaque minute : le temoin ne bouge pas, mais le journal si.
        # D'ou cette trace unique, posee a cote, qui evite d'inonder syslog.
        trace="$RACINE/.refusee"
        if [ "$(cat "$trace" 2>/dev/null || echo)" != "$attendue" ]; then
            journal "${attendue:0:8} REFUSE : $echoues verification(s) en echec. Rien n'est deploye."
            echo "$attendue" >"$trace"
        fi
        exit 0
    fi
fi

# L'heure, apres les tests et avant tout le reste.
#
# `%-H` et non `%H` : sans lui, huit heures du matin s'ecrit « 08 ». La comparaison de `[ ]` s'en
# accommode, mais l'arithmetique du shell y lit de l'octal — et « 08 » n'en est pas un valide.
# Retirer le zero ici evite que ce nombre ne devienne un piege le jour ou quelqu'un le calcule,
# et il se lit mieux dans le journal.
heure=$(TZ="$FUSEAU" date +%-H)
if [ "$force" != "--maintenant" ] \
    && [ "$heure" -lt "$PLAGE_DEBUT" ] && [ "$heure" -ge "$PLAGE_FIN" ]; then
    # Une seule ligne, et non une par minute : hors plage, ce script se reveille des centaines de
    # fois avant que l'heure ne vienne, et syslog en garderait la trace de chacune.
    trace="$RACINE/.differee"
    if [ "$(cat "$trace" 2>/dev/null || echo)" != "$attendue" ]; then
        journal "${attendue:0:8} : tests verts, mais il est ${heure} h — deploiement differe a ${PLAGE_DEBUT} h."
        echo "$attendue" >"$trace"
    fi
    exit 0
fi

journal "${attendue:0:8} : tests verts, deploiement."

git pull --ff-only --quiet origin "$BRANCHE"

# `--build` plutot qu'un `docker build` a part : le compose decrit deja comment construire l'API
# et le front, et deux facons de construire la meme chose finissent par diverger.
docker compose -p gestionstock --env-file "$RACINE/.env" \
    -f "$SOURCE/docker-compose.prod.yml" up -d --build

# Le temoin n'est ecrit qu'apres coup : un deploiement interrompu sera repris au reveil suivant
# plutot qu'oublie.
echo "$attendue" >"$TEMOIN"
journal "${attendue:0:8} deploye."

# La preuve que c'est bien cette version qui repond, et non celle d'avant que compose aurait
# laissee en place. Un conteneur en bonne sante ne prouve que la sante de ce qui tournait deja.
sleep 20
sante=$(curl -fsS -o /dev/null -w '%{http_code}' \
    "http://localhost:${PORT_API:-9092}/gestiondestock/v1/articles/all" || echo 000)
case "$sante" in
    401 | 200) journal "L'API repond ($sante)." ;;
    *) journal "ATTENTION : l'API repond $sante — a regarder." ;;
esac
