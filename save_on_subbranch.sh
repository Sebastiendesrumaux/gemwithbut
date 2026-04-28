#!/bin/bash
BRANCHE=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCHE" == "main" ]; then
  echo "⚠️ Tu es sur MAIN. Utilise ton 'save.sh' habituel."
  exit 1
fi
git add -A
git commit -m "Exploration sur $BRANCHE : $(date +%H:%M)"
git push origin "$BRANCHE"
echo "💾 Sauvegardé sur la sous-branche distante : $BRANCHE"
