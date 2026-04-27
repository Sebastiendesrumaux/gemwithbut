#!/data/data/com.termux/files/usr/bin/bash
echo "[UNDO] Recul d'un pas dans l'historique..."
git reset --hard HEAD~1
echo "[UNDO] Terminé. Tu es maintenant sur le commit précédent :"
git log -1 --oneline
