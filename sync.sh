#!/bin/bash
# AIDE → GitHub senkronizasyon scripti

AIDE_DIR="/storage/internal/project/AquaLight"
GIT_DIR="$HOME/projects/AquaLight"

echo "Senkronizasyon basliyor..."

cd "$GIT_DIR" || { echo "Git klasorune girilemedi!"; exit 1; }

if [ "$(git rev-parse --abbrev-ref HEAD)" = "HEAD" ]; then
    echo "Detached HEAD algilandi, main dalina geciliyor..."
    git checkout main || git checkout -b main origin/main
fi

if [ -d ".git/rebase-apply" ] || [ -d ".git/rebase-merge" ]; then
    echo "Onceki rebase islemi tespit edildi, temizleniyor..."
    git rebase --abort 2>/dev/null
fi

echo "Dosyalar kopyalaniyor..."
rsync -av --delete "$AIDE_DIR"/ "$GIT_DIR"/ \
  --exclude=".git" \
  --exclude=".gradle" \
  --exclude="build" \
  --exclude="*.iml" \
  >/dev/null

echo "Degisiklikler hazirlaniyor..."
git add -A

if [ -z "$1" ]; then
    COMMIT_MSG="sync: AIDE degisiklikleri"
else
    COMMIT_MSG="$1"
fi

if git diff --cached --quiet; then
    echo "Commit yapilacak degisiklik yok."
else
    echo "Commit olusturuluyor: '$COMMIT_MSG'"
    git commit -m "$COMMIT_MSG"
fi

echo "GitHub ile senkronize ediliyor..."
git pull --rebase origin main
git push origin main && echo "Gonderim tamamlandi!" || echo "Push basarisiz!"

echo "Islem tamam!"
