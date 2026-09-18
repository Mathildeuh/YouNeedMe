# Activer l'analyse de code Qodana

Le workflow (`.github/workflows/qodana.yml`) est en place mais ne fait rien tant que le secret `QODANA_TOKEN` n'existe pas — Qodana refuse de scanner sans connexion à Qodana Cloud depuis la version 2023.2 (contrairement à Modrinth/Hangar qui sont juste "best-effort"). En attendant, l'étape échoue silencieusement (`continue-on-error: true`) sans bloquer le reste du workflow.

## 1. Créer un projet sur Qodana Cloud

1. Va sur https://qodana.cloud et connecte-toi (peut se faire via ton compte GitHub).
2. Crée un nouveau projet, lie-le au dépôt `Mathildeuh/YouNeedMe`.
3. Le plan gratuit couvre largement un projet open-source de cette taille.

## 2. Récupérer le token du projet

1. Dans les réglages du projet sur qodana.cloud, trouve la section **Project token** (ou **CI/CD integration**).
2. Copie le token généré.

## 3. Ajouter le token comme secret GitHub

1. Sur le dépôt : **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
2. Nom : `QODANA_TOKEN` (exactement ce nom).
3. Valeur : colle le token.
4. **Add secret**.

Ou via `gh` :
```bash
gh secret set QODANA_TOKEN
```

## 4. Vérifier

Le workflow tourne sur chaque push vers `main`, chaque pull request, et manuellement (`Actions` → `Qodana` → `Run workflow`). Une fois le token en place :
- Les résultats apparaissent sur ton dashboard qodana.cloud.
- Les problèmes détectés remontent aussi dans l'onglet **Security → Code scanning** du dépôt GitHub (upload SARIF automatique), sans dépendre de qodana.cloud.

Si l'étape échoue encore après avoir ajouté le secret, vérifie que le nom est exactement `QODANA_TOKEN` et que le token n'a pas expiré côté qodana.cloud.
