#!/data/data/com.termux/files/usr/bin/bash
#juste préalablemznt créer le new repository sur le site de github
#private fonctionne
#supprime le repertoire .git 
#le .gitignore fait que ça va pusher que du source
#le password est dans les notes "ghp.."
#git config --global credential.helper store pour ne pas avoir à le retaper

echo "utilise un token classique"
set -e
export nom="gemwithbut"
echo "[INIT] Initialisation du dépôt Git pour le projet"

# Assurer que ce dossier est considéré comme safe par Git
git config --global --add safe.directory "$(pwd)"

# Initialisation si nécessaire
if [ ! -d .git ]; then
  echo "[INIT] git init"
  git init
fi

# Ajout de tous les fichiers
echo "[INIT] git add -A"
git add -A

# Premier commit
echo "[INIT] Premier commit…"
git commit -m "Initial commit"

# Définition du dépôt distant
echo "[INIT] Définition du remote GitHub…"
git remote remove origin 2>/dev/null || true
git remote add origin https://github.com/Sebastiendesrumaux/"$nom".git

# Push vers GitHub
echo "[INIT] Push vers GitHub…"
git branch -M main
git push -u origin main

echo "[INIT] ✔ Terminé. Le projet est maintenant lié à GitHub."

