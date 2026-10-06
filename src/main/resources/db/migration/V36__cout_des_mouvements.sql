-- La demarque : ce que le magasin perd sans le vendre — casse, vol, peremption, ecarts
-- d'inventaire —, valorise a ce que la marchandise avait coute.
--
-- Comme pour la marge (V35), le cout est fige sur le mouvement au moment ou il a lieu : une casse
-- de mars ne se revalorise pas a la livraison d'octobre. Nul pour les mouvements d'avant : leur
-- valeur n'est qu'estimee au cout moyen actuel.

alter table mvt_stk add column cout_unitaire numeric(19, 4);

-- Les rapports lisent les mouvements d'une entreprise par motif et par periode.
create index if not exists ix_mvt_stk_entreprise_motif_date on mvt_stk (id_entreprise, motif, date_mvt);
