#!/usr/bin/env bash
#
# Sauvegarde la base de GestionStock, et l'autorite de certification du Caddy.
#
#   ./sauvegarder.sh [--maintenant]
#
# Appele chaque heure par la crontab de jumpy. Il ne sauvegarde qu'une fois par jour civil du
# magasin : les autres reveils trouvent la sauvegarde du jour et repartent sans rien ecrire.
#
# Pourquoi chaque heure plutot qu'une fois la nuit : la machine vit sur l'electricite et la
# liaison du magasin. Une crontab a heure fixe rate simplement la nuit ou la machine etait
# eteinte — le journal de livraison, sauvegarde a 2 h, a des trous les 22, 26 et 28 septembre.
# Ici, la sauvegarde manquee se fait au premier reveil qui suit le retour du courant.
#
# --maintenant : sauvegarde meme si celle du jour existe deja. Avant une operation risquee.
#
# Deux artefacts par jour :
#   gestionstock-AAAAMMJJ-HHMMSS.dump       la base, au format personnalise de pg_dump (compresse)
#   caddy-AAAAMMJJ-HHMMSS.tar.gz            l'autorite de certification du reseau local
#
# La base ne contient plus seulement les donnees du magasin, mais celles de chaque commerce
# inscrit. Une sauvegarde qui ne quitte pas cette machine ne protege que de l'erreur logicielle,
# pas de la panne du disque ni du vol : voir la recopie hors du serveur dans le README.
set -euo pipefail
# `join` exige deux listes triees selon le meme ordre.
export LC_ALL=C

RACINE=${RACINE:-$HOME/apps/gestionstock}
DESTINATION=${DESTINATION:-$RACINE/sauvegardes}
CONTENEUR=${CONTENEUR:-gestionstock-postgres}
VOLUME_CADDY=${VOLUME_CADDY:-gestionstock-caddy-data}
# Trente jours de quotidiennes, et la sauvegarde du 1er de chaque mois gardee un an : une erreur
# de saisie decouverte a la cloture annuelle doit encore pouvoir se retrouver.
RETENTION_JOURS=${RETENTION_JOURS:-30}
RETENTION_MENSUELLE_JOURS=${RETENTION_MENSUELLE_JOURS:-366}
# Le jour civil du magasin, et non celui de l'horloge du serveur reglee en UTC.
FUSEAU=${FUSEAU:-Africa/Douala}
BASE_VERIFICATION=verification_sauvegarde

force=${1:-}

journal() { printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

mkdir -p "$DESTINATION"
# L'archive du Caddy contient la cle privee de l'autorite : qui la lit peut se faire passer
# pour le serveur aupres de chaque appareil du magasin.
chmod 700 "$DESTINATION"
umask 077

# Deux reveils qui se chevauchent — une sauvegarde lente et le reveil de l'heure suivante —
# ecriraient le meme fichier. Le second repart.
exec 9>"$DESTINATION/.sauvegarde.lock"
flock -n 9 || exit 0

JOUR=$(TZ="$FUSEAU" date +%Y%m%d)
if [[ "$force" != "--maintenant" ]] && compgen -G "$DESTINATION/gestionstock-$JOUR-*.dump" >/dev/null; then
    exit 0
fi

journal "Sauvegarde du $JOUR"

# Une sauvegarde interrompue faute de place laisse un fichier tronque, indetectable jusqu'au
# jour de la restauration. Mieux vaut refuser de commencer.
LIBRE_MO=$(df -Pm "$DESTINATION" | awk 'NR==2 {print $4}')
if (( LIBRE_MO < 500 )); then
    journal "ERREUR : moins de 500 Mo libres sur $DESTINATION ($LIBRE_MO Mo). Rien n'est sauvegarde."
    exit 1
fi

docker inspect -f '{{.State.Running}}' "$CONTENEUR" 2>/dev/null | grep -q true || {
    journal "ERREUR : le conteneur $CONTENEUR ne tourne pas. Rien n'est sauvegarde."
    exit 1
}

# L'utilisateur et la base se lisent dans le conteneur lui-meme : ce script n'a pas a connaitre
# le .env, et encore moins le mot de passe. La connexion par la socket locale du conteneur n'en
# demande pas.
UTILISATEUR=$(docker exec "$CONTENEUR" printenv POSTGRES_USER)
BASE=$(docker exec "$CONTENEUR" printenv POSTGRES_DB)

HORODATAGE="$(TZ="$FUSEAU" date +%Y%m%d-%H%M%S)"
FICHIER="$DESTINATION/gestionstock-$HORODATAGE.dump"
PARTIEL="$FICHIER.partiel"

nettoyer() { docker exec "$CONTENEUR" dropdb -U "$UTILISATEUR" --if-exists "$BASE_VERIFICATION" >/dev/null 2>&1 || true; }
trap nettoyer EXIT

# Le nombre de lignes de chaque table, sous la forme « table=nombre table=nombre ».
COMPTER="select string_agg(format('%s=%s', t.tablename,
            (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from public.%I', t.tablename), false, true, '')))[1]::text),
          ' ' order by t.tablename)
          from pg_tables t where t.schemaname = 'public'"
compter() { docker exec "$CONTENEUR" psql -U "$UTILISATEUR" -d "$1" -Atc "$COMPTER"; }

# Les tables de la base dont la copie restauree n'a pas exactement le meme nombre de lignes, ou
# qui y manquent. Vide quand tout concorde.
ecarts() {
    join <(tr ' ' '\n' <<<"$1" | tr '=' ' ' | sort) \
         <(tr ' ' '\n' <<<"$2" | tr '=' ' ' | sort) -a1 -e ABSENTE -o 0,1.2,2.2 \
    | awk '$2 != $3 {print $1" : base "$2", sauvegarde "$3}'
}

# pg_dump lit un instantane coherent sans bloquer personne : la caisse continue d'encaisser
# pendant qu'il tourne. Le format personnalise est deja compresse et se restaure table par table.
#
# Puis la preuve qu'elle se restaure, et non qu'elle existe : elle est rechargee dans une base
# jetable, et chaque table doit y retrouver exactement autant de lignes que dans la vraie.
#
# Exactement, et non « au moins » : une tolerance laisserait passer la table a moitie restauree,
# qui est justement ce qu'on cherche. Mais le comptage de la base se fait apres le dump, et une
# vente encaissee entre les deux suffit a creer un ecart legitime. D'ou un second essai : la
# sauvegarde tourne vers minuit, magasin ferme, et deux ventes coup sur coup a cette heure-la
# ne se rencontrent pas. Un ecart qui persiste n'est plus une coincidence.
for essai in 1 2; do
    docker exec "$CONTENEUR" pg_dump -U "$UTILISATEUR" -d "$BASE" -Fc --no-owner > "$PARTIEL"
    REFERENCE=$(compter "$BASE")

    nettoyer
    docker exec "$CONTENEUR" createdb -U "$UTILISATEUR" "$BASE_VERIFICATION"
    docker exec -i "$CONTENEUR" pg_restore -U "$UTILISATEUR" -d "$BASE_VERIFICATION" \
        --no-owner --exit-on-error < "$PARTIEL"
    RESTAUREE=$(compter "$BASE_VERIFICATION")

    ecart=$(ecarts "$REFERENCE" "$RESTAUREE")
    [[ -z "$ecart" ]] && break
    if (( essai == 2 )); then
        journal "ERREUR : la sauvegarde restauree ne correspond pas a la base, deux fois de suite. Elle est ecartee."
        printf '%s\n' "$ecart" | sed 's/^/    /'
        rm -f "$PARTIEL"
        exit 1
    fi
    journal "  ecart au premier essai, sans doute une vente pendant le dump — on recommence"
done

TABLES=$(wc -w <<<"$REFERENCE")
LIGNES=$(tr ' ' '\n' <<<"$RESTAUREE" | awk -F= '{s += $2} END {print s}')
mv "$PARTIEL" "$FICHIER"
journal "  $(basename "$FICHIER") ($(du -h "$FICHIER" | cut -f1)) — restauree et verifiee : $TABLES tables, $LIGNES lignes"

# L'autorite de certification du reseau local. La perdre obligerait a reinstaller le certificat
# racine sur chaque appareil du magasin. Le volume est lu par un conteneur jetable, sans arreter
# le Caddy.
ARCHIVE_CADDY="$DESTINATION/caddy-$HORODATAGE.tar.gz"
docker run --rm -v "$VOLUME_CADDY:/donnees:ro" alpine:3 tar czf - -C /donnees . > "$ARCHIVE_CADDY"
journal "  $(basename "$ARCHIVE_CADDY") ($(du -h "$ARCHIVE_CADDY" | cut -f1))"

# La purge. La sauvegarde du 1er du mois vit un an, les autres trente jours.
while IFS= read -r ancien; do
    nom=$(basename "$ancien")
    jour=$(grep -oE '[0-9]{8}' <<<"$nom" | head -1)
    age=$(( ( $(date +%s) - $(date -d "$jour" +%s) ) / 86400 ))
    if [[ "${jour:6:2}" == "01" ]]; then
        (( age > RETENTION_MENSUELLE_JOURS )) || continue
    else
        (( age > RETENTION_JOURS )) || continue
    fi
    rm -f "$ancien"
    journal "  purgee : $nom"
done < <(find "$DESTINATION" -maxdepth 1 -type f \( -name 'gestionstock-*.dump' -o -name 'caddy-*.tar.gz' \))

# Un .partiel oublie par une execution interrompue (coupure de courant pendant le dump).
find "$DESTINATION" -maxdepth 1 -name '*.partiel' -mmin +60 -delete

journal "  termine — $(ls "$DESTINATION"/gestionstock-*.dump | wc -l) sauvegardes de la base, $(df -Ph "$DESTINATION" | awk 'NR==2 {print $4}') libres"
