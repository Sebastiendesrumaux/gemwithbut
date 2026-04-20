
# Usage: ./create_app.sh NomProjet AliasBase AliasSource
PROJECT_NAME=$1
ALIAS_BASE=$2
ALIAS_SOURCE=$3

if [ -z "$PROJECT_NAME" ] || [ -z "$ALIAS_BASE" ] || [ -z "$ALIAS_SOURCE" ]; then
    echo "Usage: ./set_alias.sh <NomProjet> <AliasBase> <AliasSource>"
    exit 1
fi

# Chemins absolus pour les alias
cd "$PROJECT_NAME"
BASE_DIR=$(pwd)
SOURCE_DIR="$BASE_DIR/app/src/main/java/com/example/$PROJECT_NAME"


# 7. Ajout des alias au .bashrc
echo "alias $ALIAS_BASE='cd $BASE_DIR'" >> ~/.bashrc
echo "alias $ALIAS_SOURCE='cd $SOURCE_DIR'" >> ~/.bashrc

# Actualisation de l'environnement
source ~/.bashrc

echo "Projet $PROJECT_NAME configuré. Alias '$ALIAS_BASE' et '$ALIAS_SOURCE' créés."
