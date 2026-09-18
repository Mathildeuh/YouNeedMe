# Commit et push sans assistance

Ce dépôt utilise **release-please** : les versions, tags et releases GitHub sont générés automatiquement à partir du *texte de tes commits*. Si tu ne respectes pas le format, ça ne casse rien immédiatement, mais tes changements n'apparaîtront jamais dans une release. C'est le seul point vraiment important à retenir.

## 1. Avant de commit

```bash
git status                 # vérifier ce qui a changé
./gradlew build             # vérifier que ça compile et que les tests passent
```

Ne commit jamais si `./gradlew build` échoue.

## 2. Le format du message de commit (important)

Le message doit commencer par un **type**, suivi de `:`, suivi d'une description courte à l'impératif, en anglais (comme le reste de l'historique du dépôt) :

| Type | Effet sur la version | Exemple |
|---|---|---|
| `fix:` | bump **patch** (1.3.1 → 1.3.2) | `fix: baltop always empty on fresh install` |
| `feat:` | bump **minor** (1.3.1 → 1.4.0) | `feat: add /kit GUI` |
| `feat!:` ou `fix!:` (avec `BREAKING CHANGE:` dans le corps) | bump **major** (1.x → 2.0.0) | `feat!: remove deprecated /home2 alias` |
| `chore:`, `docs:`, `refactor:`, `test:`, `style:`, `ci:` | **pas de bump** — inclus dans la prochaine release mais ne la déclenche pas seul | `docs: fix typo in README` |

Le corps du message (après une ligne vide) peut expliquer le **pourquoi**, pas juste le *quoi* — c'est ce qui finit dans le CHANGELOG généré automatiquement. Regarde `git log` sur ce dépôt pour des exemples concrets de style.

```bash
git add <fichiers précis>   # jamais `git add -A` sans relire `git status` d'abord
git commit -m "fix: description courte

Explication du pourquoi, sur 2-3 lignes si utile."
```

## 3. Push

```bash
git push
```

### Si le push est rejeté ("rejected... fetch first")

C'est normal et fréquent sur ce dépôt : **release-please** ou une PR fusionnée entre-temps a avancé `main` pendant que tu travaillais. Ne force jamais le push. À la place :

```bash
git fetch origin
git rebase origin/main
git push
```

Si le rebase signale un conflit, résous-le fichier par fichier (`git status` te dit lesquels), puis :
```bash
git add <fichier résolu>
git rebase --continue
git push
```

## 4. Comment sortent les releases (tu n'as rien à faire manuellement)

1. Tu push des commits `fix:`/`feat:` sur `main`.
2. Le workflow **release-please** (`.github/workflows/release-please.yml`) ouvre ou met à jour automatiquement une pull request "chore(main): release X.Y.Z" qui bump la version et met à jour `CHANGELOG.md`.
3. Quand tu **merges cette PR** (sur GitHub, bouton "Merge"), release-please crée le tag `vX.Y.Z` et la Release GitHub.
4. Le tag déclenche `.github/workflows/release.yml` qui build le jar (`build/libs/YouNeedMe.jar`, sans version dans le nom) et l'attache à la Release, plus Modrinth/Hangar si les secrets sont configurés.

Tu n'as jamais besoin de créer un tag, une release ou de bumper la version toi-même — merge juste la PR de release-please quand tu es prêt à sortir une version.

## 5. Erreurs à éviter

- `git push --force` sur `main` : jamais, sauf si tu sais exactement pourquoi (aucune raison légitime ne devrait se présenter en usage normal).
- `git add -A` sans relire `git status` avant : risque de commit un fichier local (`.env`, `test-server/`, etc.) — le `.gitignore` couvre les cas connus mais pas les nouveaux.
- Un message de commit sans type (`fix:`/`feat:`/...) : le commit existera mais **n'affectera jamais la version**, même s'il contient un vrai correctif.
- Amender un commit déjà pushé (`git commit --amend` puis `push --force`) : réécrit l'historique partagé, à éviter sauf travail strictement local pas encore poussé.
