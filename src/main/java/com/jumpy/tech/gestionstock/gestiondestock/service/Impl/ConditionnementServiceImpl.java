package com.jumpy.tech.gestionstock.gestiondestock.service.Impl;

import com.jumpy.tech.gestionstock.gestiondestock.conditionnement.CodeEan;
import com.jumpy.tech.gestionstock.gestiondestock.config.security.Cloisonnement;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ArticleDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.CodeBarresDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ConditionnementDto;
import com.jumpy.tech.gestionstock.gestiondestock.dto.ResultatScanDto;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Article;
import com.jumpy.tech.gestionstock.gestiondestock.entities.CodeBarres;
import com.jumpy.tech.gestionstock.gestiondestock.entities.Conditionnement;
import com.jumpy.tech.gestionstock.gestiondestock.entities.TypeCodeBarres;
import com.jumpy.tech.gestionstock.gestiondestock.entities.UniteMesure;
import com.jumpy.tech.gestionstock.gestiondestock.exception.EntityNotFoundException;
import com.jumpy.tech.gestionstock.gestiondestock.exception.ErrorCodes;
import com.jumpy.tech.gestionstock.gestiondestock.exception.InvalidEntityException;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ArticleRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.CodeBarresRepository;
import com.jumpy.tech.gestionstock.gestiondestock.repository.ConditionnementRepository;
import com.jumpy.tech.gestionstock.gestiondestock.service.ConditionnementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConditionnementServiceImpl implements ConditionnementService {

    /** Au-dela, un tirage au hasard qui ne trouve pas de place libre signale un probleme, pas une malchance. */
    private static final int TIRAGES_MAX = 20;

    private final ConditionnementRepository conditionnementRepository;
    private final CodeBarresRepository codeBarresRepository;
    private final ArticleRepository articleRepository;
    private final Cloisonnement cloisonnement;

    public ConditionnementServiceImpl(ConditionnementRepository conditionnementRepository,
                                      CodeBarresRepository codeBarresRepository,
                                      ArticleRepository articleRepository,
                                      Cloisonnement cloisonnement) {
        this.conditionnementRepository = conditionnementRepository;
        this.codeBarresRepository = codeBarresRepository;
        this.articleRepository = articleRepository;
        this.cloisonnement = cloisonnement;
    }

    @Override
    public List<ConditionnementDto> conditionnements(Long idArticle) {
        article(idArticle);
        return conditionnementRepository.findAllByArticleIdOrderByQuantiteUnitesAsc(idArticle).stream()
                .map(ConditionnementDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConditionnementDto ajouter(Long idArticle, ConditionnementDto dto) {
        Article article = article(idArticle);
        Conditionnement conditionnement = new Conditionnement();
        conditionnement.setArticle(article);
        conditionnement.setIdEntreprise(article.getIdEntreprise());
        appliquer(conditionnement, dto, article);
        return ConditionnementDto.fromEntity(conditionnementRepository.save(conditionnement));
    }

    @Override
    @Transactional
    public ConditionnementDto modifier(Long idArticle, Long idConditionnement, ConditionnementDto dto) {
        Article article = article(idArticle);
        Conditionnement conditionnement = conditionnement(article, idConditionnement);
        if (!conditionnement.isActif()) {
            throw new InvalidEntityException("Un conditionnement retiré ne se modifie plus",
                    ErrorCodes.CONDITIONNEMENT_NOT_VALID,
                    List.of("Créez-en un nouveau"));
        }
        // La contenance peut changer : les lignes deja saisies ont fige la leur, et le stock
        // qu'elles ont fait bouger ne bouge plus.
        appliquer(conditionnement, dto, article);
        return ConditionnementDto.fromEntity(conditionnementRepository.save(conditionnement));
    }

    @Override
    @Transactional
    public void retirer(Long idArticle, Long idConditionnement) {
        Article article = article(idArticle);
        Conditionnement conditionnement = conditionnement(article, idConditionnement);
        conditionnement.setActif(false);
        conditionnementRepository.save(conditionnement);
        // Ses codes sont liberes : un carton qu'on ne vend plus ne doit plus se scanner, et son
        // code peut servir au conditionnement qui le remplace.
        codeBarresRepository.deleteAll(codeBarresRepository.findAllByConditionnementId(idConditionnement));
    }

    /**
     * Ecrit sur le conditionnement ce que dit la demande, apres l'avoir verifie en entier : un
     * conditionnement a moitie valide ne s'enregistre pas.
     */
    private void appliquer(Conditionnement conditionnement, ConditionnementDto dto, Article article) {
        List<String> erreurs = new ArrayList<>();
        if (dto == null) {
            throw new InvalidEntityException("Le conditionnement n'est pas valide",
                    ErrorCodes.CONDITIONNEMENT_NOT_VALID, List.of("Aucun conditionnement fourni"));
        }
        String libelle = dto.getLibelle() == null ? null : dto.getLibelle().trim();
        if (!StringUtils.hasText(libelle)) {
            erreurs.add("Le libellé est obligatoire, par exemple « Carton de 24 »");
        } else if (libelle.length() > 60) {
            erreurs.add("Le libellé ne dépasse pas 60 caractères");
        } else if (libelleDejaPris(article, libelle, conditionnement.getId())) {
            erreurs.add("L'article a déjà un conditionnement « " + libelle + " »");
        }

        BigDecimal quantite = dto.getQuantiteUnites();
        UniteMesure unite = article.getUniteBase() == null ? UniteMesure.PIECE : article.getUniteBase();
        if (quantite == null || quantite.signum() <= 0) {
            erreurs.add("La contenance doit être strictement positive");
        } else if (!unite.isFractionnable() && quantite.stripTrailingZeros().scale() > 0) {
            erreurs.add("L'article se compte à la pièce : un conditionnement en contient un nombre entier");
        } else if (quantite.compareTo(BigDecimal.ONE) == 0) {
            // Un « conditionnement de 1 », c'est l'article lui-meme : son code se porte a l'unite,
            // et un second prix pour la meme chose ne ferait que des erreurs de caisse.
            erreurs.add("Un conditionnement contient plus d'une unité ; l'unité est l'article lui-même");
        }

        boolean vendable = dto.getVendable() == null || dto.getVendable();
        boolean achetable = dto.getAchetable() == null || dto.getAchetable();
        BigDecimal prix = dto.getPrixVenteHt();
        if (prix != null && prix.signum() < 0) {
            erreurs.add("Le prix de vente ne peut pas être négatif");
        }
        if (vendable && prix == null) {
            erreurs.add("Un conditionnement vendable a un prix de vente");
        }
        if (!vendable && !achetable) {
            erreurs.add("Un conditionnement sert à vendre, à acheter, ou aux deux");
        }

        if (!erreurs.isEmpty()) {
            throw new InvalidEntityException("Le conditionnement n'est pas valide",
                    ErrorCodes.CONDITIONNEMENT_NOT_VALID, erreurs);
        }
        conditionnement.setLibelle(libelle);
        conditionnement.setQuantiteUnites(quantite);
        conditionnement.setPrixVenteHt(prix);
        conditionnement.setVendable(vendable);
        conditionnement.setAchetable(achetable);
    }

    private boolean libelleDejaPris(Article article, String libelle, Long idCourant) {
        return conditionnementRepository.findAllByArticleIdOrderByQuantiteUnitesAsc(article.getId()).stream()
                .filter(Conditionnement::isActif)
                .filter(c -> !Objects.equals(c.getId(), idCourant))
                .anyMatch(c -> c.getLibelle().equalsIgnoreCase(libelle));
    }

    @Override
    public List<CodeBarresDto> codes(Long idArticle) {
        article(idArticle);
        return codeBarresRepository.findAllByArticleIdOrderByIdAsc(idArticle).stream()
                .map(CodeBarresDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CodeBarresDto ajouterCode(Long idArticle, CodeBarresDto dto) {
        Article article = article(idArticle);
        if (dto == null || !StringUtils.hasText(dto.getCode())) {
            throw new InvalidEntityException("Le code-barres est obligatoire", ErrorCodes.CODE_BARRES_NOT_VALID);
        }
        // Les espaces ne font jamais partie d'un code : ils viennent d'un copier-coller, ou
        // d'une saisie qui recopie l'etiquette par groupes de chiffres.
        String code = dto.getCode().replaceAll("\\s", "");
        TypeCodeBarres type = dto.getType() == null ? CodeEan.deviner(code) : dto.getType();
        verifierForme(code, type);
        Conditionnement conditionnement = dto.getIdConditionnement() == null
                ? null : conditionnement(article, dto.getIdConditionnement());
        if (conditionnement != null && !conditionnement.isActif()) {
            throw new InvalidEntityException("Un conditionnement retiré ne reçoit plus de code",
                    ErrorCodes.CODE_BARRES_NOT_VALID);
        }
        verifierLibre(code, article);
        return CodeBarresDto.fromEntity(enregistrerCode(article, conditionnement, code, type));
    }

    @Override
    @Transactional
    public CodeBarresDto genererCodeInterne(Long idArticle, Long idConditionnement) {
        Article article = article(idArticle);
        Conditionnement conditionnement = idConditionnement == null
                ? null : conditionnement(article, idConditionnement);
        for (int tirage = 0; tirage < TIRAGES_MAX; tirage++) {
            String code = CodeEan.interneAuHasard();
            if (!codeBarresRepository.existsByCodeAndIdEntreprise(code, article.getIdEntreprise())
                    && articleQuiPorteLeCode(code, article.getIdEntreprise()).isEmpty()) {
                return CodeBarresDto.fromEntity(enregistrerCode(article, conditionnement, code, TypeCodeBarres.INTERNE));
            }
        }
        throw new IllegalStateException("Aucun code interne libre après " + TIRAGES_MAX + " tirages");
    }

    private CodeBarres enregistrerCode(Article article, Conditionnement conditionnement, String code,
                                       TypeCodeBarres type) {
        CodeBarres codeBarres = new CodeBarres();
        codeBarres.setArticle(article);
        codeBarres.setConditionnement(conditionnement);
        codeBarres.setIdEntreprise(article.getIdEntreprise());
        codeBarres.setCode(code);
        codeBarres.setType(type);
        return codeBarresRepository.save(codeBarres);
    }

    private void verifierForme(String code, TypeCodeBarres type) {
        if (code.length() > 64) {
            throw new InvalidEntityException("Un code-barres ne dépasse pas 64 caractères",
                    ErrorCodes.CODE_BARRES_NOT_VALID);
        }
        int longueur = CodeEan.longueur(type);
        if (longueur == 0) {
            return;
        }
        if (code.length() != longueur || !CodeEan.cleValide(code)) {
            throw new InvalidEntityException(
                    "Le code " + code + " n'est pas un " + type + " valide",
                    ErrorCodes.CODE_BARRES_NOT_VALID,
                    List.of(longueur + " chiffres attendus, dont la clé de contrôle en dernier"));
        }
    }

    /**
     * Un code ne designe qu'une chose dans le magasin. Le code d'article compte aussi : le scan le
     * reconnait toujours, et un EAN egal au code d'un autre article rendrait la douchette
     * ambigue.
     */
    private void verifierLibre(String code, Article article) {
        if (codeBarresRepository.existsByCodeAndIdEntreprise(code, article.getIdEntreprise())) {
            throw new InvalidEntityException("Le code " + code + " est déjà attribué",
                    ErrorCodes.CODE_BARRES_NOT_VALID);
        }
        articleQuiPorteLeCode(code, article.getIdEntreprise())
                .filter(autre -> !autre.getId().equals(article.getId()))
                .ifPresent(autre -> {
                    throw new InvalidEntityException(
                            "Le code " + code + " est le code de l'article " + autre.getDesignation(),
                            ErrorCodes.CODE_BARRES_NOT_VALID);
                });
    }

    private Optional<Article> articleQuiPorteLeCode(String code, Long idEntreprise) {
        return articleRepository.findArticleByCodeArticleAndIdEntreprise(code, idEntreprise);
    }

    @Override
    @Transactional
    public void retirerCode(Long idArticle, Long idCode) {
        Article article = article(idArticle);
        CodeBarres code = codeBarresRepository.findById(idCode)
                .filter(c -> c.getArticle().getId().equals(article.getId()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun code-barres " + idCode + " sur l'article " + idArticle,
                        ErrorCodes.CODE_BARRES_NOT_FOUND));
        codeBarresRepository.delete(code);
    }

    @Override
    public ResultatScanDto scanner(String lu) {
        if (!StringUtils.hasText(lu)) {
            throw new InvalidEntityException("Aucun code lu", ErrorCodes.CODE_BARRES_NOT_VALID);
        }
        String code = lu.trim();
        Long idEntreprise = cloisonnement.entrepriseCourante();
        Optional<CodeBarres> trouve = idEntreprise == null
                ? codeBarresRepository.findByCodeAndIdEntrepriseIsNull(code)
                : codeBarresRepository.findByCodeAndIdEntreprise(code, idEntreprise);
        if (trouve.isPresent()) {
            CodeBarres codeBarres = trouve.get();
            return ResultatScanDto.builder()
                    .article(completer(ArticleDto.fromEntity(codeBarres.getArticle())))
                    .conditionnement(ConditionnementDto.fromEntity(codeBarres.getConditionnement()))
                    .build();
        }
        // Le code de l'article reste reconnu : c'est ce que scannaient les magasins avant qu'un
        // article ait plusieurs codes, et leurs etiquettes sont toujours collees.
        Optional<Article> parCodeArticle = idEntreprise == null
                ? articleRepository.findArticleByCodeArticle(code).filter(a -> a.getIdEntreprise() == null)
                : articleRepository.findArticleByCodeArticleAndIdEntreprise(code, idEntreprise);
        return parCodeArticle
                .map(article -> ResultatScanDto.builder().article(completer(ArticleDto.fromEntity(article))).build())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article ne porte le code " + code, ErrorCodes.CODE_BARRES_NOT_FOUND));
    }

    private ArticleDto completer(ArticleDto article) {
        return completer(List.of(article)).get(0);
    }

    @Override
    public List<ArticleDto> completer(List<ArticleDto> articles) {
        List<Long> ids = articles.stream().map(ArticleDto::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return articles;
        }
        Map<Long, List<ConditionnementDto>> conditionnements =
                conditionnementRepository.findAllByArticleIdInOrderByQuantiteUnitesAsc(ids).stream()
                        .filter(Conditionnement::isActif)
                        .map(ConditionnementDto::fromEntity)
                        .collect(Collectors.groupingBy(ConditionnementDto::getIdArticle));
        Map<Long, List<CodeBarresDto>> codes = codeBarresRepository.findAllByArticleIdInOrderByIdAsc(ids).stream()
                .map(CodeBarresDto::fromEntity)
                .collect(Collectors.groupingBy(CodeBarresDto::getIdArticle));
        articles.forEach(article -> {
            article.setConditionnements(conditionnements.getOrDefault(article.getId(), List.of()));
            article.setCodesBarres(codes.getOrDefault(article.getId(), List.of()));
        });
        return articles;
    }

    private Article article(Long idArticle) {
        if (idArticle == null) {
            throw new InvalidEntityException("Aucun article ne peut être cherché sans identifiant",
                    ErrorCodes.ARTICLE_NOT_VALID);
        }
        Article article = articleRepository.findById(idArticle)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun article avec l'identifiant " + idArticle + " n'a été trouvé",
                        ErrorCodes.ARTICLE_NOT_FOUND));
        cloisonnement.verifierAcces(article.getIdEntreprise(), "article", idArticle);
        return article;
    }

    private Conditionnement conditionnement(Article article, Long idConditionnement) {
        return conditionnementRepository.findById(idConditionnement)
                .filter(c -> c.getArticle().getId().equals(article.getId()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Aucun conditionnement " + idConditionnement + " sur l'article " + article.getId(),
                        ErrorCodes.CONDITIONNEMENT_NOT_FOUND));
    }
}
