# Configurer la publication automatique sur Modrinth

Le workflow (`.github/workflows/release-please.yml`, job `publish`) publie déjà automatiquement chaque release sur Modrinth via `modrinth/minotaur@v3` — il ne manque qu'un secret GitHub pour que ça fonctionne. Tant que ce secret n'existe pas, l'étape échoue silencieusement (`continue-on-error: true`) sans bloquer le reste de la release.

## 1. Créer le projet sur Modrinth (si ce n'est pas déjà fait)

1. Va sur https://modrinth.com/plugin/create (connecté avec ton compte).
2. Crée un projet avec le slug **`youneedme`** — le workflow utilise `project-id: youneedme` (le slug de l'URL, pas le nom affiché). Si tu choisis un slug différent, il faudra changer cette ligne dans le workflow.
3. Remplis au minimum : description, catégories (`utility`/`economy`/`management` par exemple), et coche les bonnes versions/loaders (Paper, Purpur, Folia, Spigot — déjà configuré dans le workflow via `loaders: [paper, purpur, folia, spigot]`).

## 2. Créer un token API Modrinth

1. Va sur https://modrinth.com/settings/pats (Personal Access Tokens).
2. Clique **Create a PAT**.
3. Donne-lui un nom clair, ex. `YouNeedMe GitHub Actions`.
4. Coche au minimum la permission **`Create versions`** (et `Read projects` si demandé séparément) sur le projet `youneedme`.
5. Copie le token généré — **il ne sera plus jamais affiché après**, donc colle-le tout de suite à l'étape suivante.

## 3. Ajouter le token comme secret GitHub

Sur le dépôt `Mathildeuh/YouNeedMe` :

1. **Settings** → **Secrets and variables** → **Actions**.
2. **New repository secret**.
3. Nom : `MODRINTH_TOKEN` (exactement ce nom, c'est ce que le workflow lit).
4. Valeur : colle le token copié à l'étape précédente.
5. **Add secret**.

Ou en une commande si tu as `gh` configuré en local :

```bash
gh secret set MODRINTH_TOKEN
```
(elle te demandera de coller la valeur, ou utilise `--body "<token>"` / `< fichier` pour l'automatiser).

## 4. Vérifier que ça marche

La publication ne se déclenche qu'à la **création réelle d'une release** (donc quand tu merges la PR "chore(main): release X.Y.Z" que release-please ouvre automatiquement) — pas à chaque commit.

Pour vérifier sans attendre une vraie release :
1. Merge la prochaine PR de release-please dès qu'elle apparaît.
2. Va dans l'onglet **Actions** du dépôt, ouvre l'exécution de `release-please`, job **publish**, étape **Publish to Modrinth**.
3. Si le token est bon, la version apparaît sur https://modrinth.com/plugin/youneedme/versions dans la minute.
4. Si l'étape échoue quand même : vérifie que le token n'a pas expiré, qu'il a bien la permission sur CE projet précis, et que le slug `youneedme` correspond bien à l'URL réelle de ton projet Modrinth.

## Au passage : Hangar suit exactement le même principe

Le job `publish` a aussi une étape **Publish to Hangar** qui attend un secret `HANGAR_API_KEY` (généré depuis https://hangar.papermc.io, page de ton compte → API Keys, avec la permission de créer des versions sur le projet `YouNeedMe`). Même méthode : `gh secret set HANGAR_API_KEY`, ou via Settings → Secrets and variables → Actions.
