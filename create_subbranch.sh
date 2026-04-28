#!/bin/bash
NOM="sub-$(date +%Y%m%d-%H%M)"
git checkout -b "$NOM"
echo "🚀 Sous-branche '$NOM' créée à partir de ta position actuelle."
echo "Tes prochains 'save_on_subbranch.sh' iront ici."
