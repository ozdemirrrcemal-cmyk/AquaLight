#!/bin/bash
# 🧩 AIDE → GitHub Tam Senkronizasyon Scripti (Cemal Özdemir)

AIDE_DIR="/storage/internal/project/AquaLight"
GIT_DIR="$HOME/projects/AquaLight"

MODE=${1:-debug}                       # 1. argüman: debug veya release (varsayılan debug)
MESSAGE=${2:-"sync: AIDE değişiklikleri"}  # 2. argüman: commit mesajı

BRANCH="main"
if [[ "$MODE" == "release" ]]; then
  BRANCH="release"
fi

echo "🧹 Senkronizasyon başlıyor ($MODE → $BRANCH)..."

cd "$GIT_DIR" || { echo "❌ Git klasörüne girilemedi!"; exit 1; }

# 🔁 Hedef dalı kontrol et / oluştur
git checkout "$BRANCH" 2>/dev/null || git checkout -b "$BRANCH" "origin/$BRANCH"

# 🧽 Rebase kalıntılarını temizle
if [ -d ".git/rebase-apply" ] || [ -d ".git/rebase-merge" ]; then
  echo "🧼 Eski rebase işlemi bulundu, temizleniyor..."
  git rebase --abort 2>/dev/null
fi

# 📦 Dosyaları kopyala
echo "📦 Dosyalar kopyalanıyor..."
if command -v rsync >/dev/null 2>&1; then
  rsync -av --delete "$AIDE_DIR"/ "$GIT_DIR"/ \
    --exclude=".git" \
    --exclude=".gradle" \
    --exclude="build" \
    --exclude="app/build" \
    --exclude=".idea" \
    --exclude=".aide" \
    --exclude="captures" \
    --exclude="*.iml" \
    --exclude="local.properties" \
    --exclude="sync.sh" \
    --include=".github/***" \
    >/dev/null
else
  echo "⚠️ rsync bulunamadı, cp ile kopyalanıyor..."
  cp -rT "$AIDE_DIR" "$GIT_DIR"
fi

# 🧩 Commit işlemi
echo "🧩 Değişiklikler hazırlanıyor..."
git add -A
if git diff --cached --quiet; then
  echo "ℹ️ Commit yapılacak değişiklik yok."
else
  echo "✅ Commit oluşturuluyor: '$MESSAGE'"
  git commit -m "$MESSAGE"
fi

# 🚀 GitHub’a gönder
echo "🌐 GitHub ($BRANCH) ile senkronize ediliyor..."
git pull --rebase origin "$BRANCH"
git push origin "$BRANCH" && echo "🎉 Gönderim tamamlandı!" || echo "❌ Push başarısız!"

echo "✨ İşlem tamam!"
