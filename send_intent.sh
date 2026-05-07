#!/bin/bash
# Génère un code aléatoire à 4 chiffres
ID=$((1000 + RANDOM % 9000))
TAG="[#${ID}#]"

echo "🎯 Préparation du Sniper..."
echo "Code généré : $ID"
echo "Tag copié dans le presse-papier : $TAG"

# On met le tag dans le presse-papier Termux
echo "$TAG" | termux-clipboard-set

# Envoi du signal au service
am broadcast -a com.example.gemwithbut.START_WATCHING \
  -p com.example.gemwithbut \
  --user 0 \
  --es target_id "$ID" \
  --el timestamp $(date +%s)

termux-tts-speak "Sniper ready for $ID"
