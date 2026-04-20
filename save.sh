#!/data/data/com.termux/files/usr/bin/bash
set -e

# Se place dans le dossier où se trouve le script
cd "$(dirname "$0")" || exit 1

echo "[SAVE] Projet : $(pwd)"

# Déclarer ce dossier comme safe pour Git (spécial Termux / Android)
git config --global --add safe.directory "$(pwd)"

# Ajouter automatiquement tous les nouveaux fichiers (respecte .gitignore)
echo "[SAVE] Ajout automatique de tous les fichiers suivis ou nouveaux…"
git add -A

# Vérifier si quelque chose a changé
if git diff --cached --quiet; then
  echo "[SAVE] Aucun changement à sauvegarder."
  exit 0
fi

# Commit
msg="Save $(date '+%Y-%m-%d %H:%M:%S')"
echo "[SAVE] Commit : $msg"
git commit -m "$msg"

# Push
echo "[SAVE] Push vers origin/main…"
git push origin main

echo "[SAVE] ✔ Terminé."
