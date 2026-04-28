#!/bin/bash
SUB=$(git rev-parse --abbrev-ref HEAD)
if [ "$SUB" == "main" ]; then
  echo "⚠️ Tu es déjà sur MAIN."
  exit 1
fi
echo "🏆 Promotion de $SUB vers MAIN..."
git checkout main
git reset --hard "$SUB"
git push -f origin main
echo "✅ Le MAIN est maintenant aligné sur ton exploration. Tu peux reprendre ton 'save.sh' habituel."
