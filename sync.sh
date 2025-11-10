#!/bin/bash
# 🚀 AIDE → GitHub Tam Senkronizasyon Scripti (Cemal Özdemir için)

AIDE_DIR="/storage/internal/project/AquaLight"
GIT_DIR="$HOME/projects/AquaLight"

echo "🧩 Senkronizasyon başlıyor..."

# 1️⃣ Git dizinine geç
cd "$GIT_DIR" || { echo "❌ Git klasörüne girilemedi!"; exit 1; }

# 2️⃣ Detached HEAD durumunu düzelt
if [ "$(git rev-parse --abbrev-ref HEAD)" = "HEAD" ]; then
    echo "⚙️ Detached HEAD algılandı, main dalına geçiliyor..."
    git checkout main || git checkout -b main origin/main
fi

# 3️⃣ Rebase kalıntılarını temizle
if [ -d ".git/rebase-apply" ] || [ -d ".git/rebase-merge" ]; then
    echo "🧹 Önceki rebase işlemi tespit edildi, temizleniyor..."
    git rebase --abort 2>/dev/null
fi

# 4️⃣ Dosyaları kopyala (rsync varsa)
echo "📁 Dosyalar kopyalanıyor..."
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
    rm -rf "$GIT_DIR/.git" "$GIT_DIR/.gradle" "$GIT_DIR/build" "$GIT_DIR"/*.iml 2>/dev/null
fi

# 5️⃣ Değişiklikleri ekle
echo "🧾 Değişiklikler hazırlanıyor..."
git add -A

# 6️⃣ Commit mesajı oluştur
if [ -z "$1" ]; then
    COMMIT_MSG="sync: AIDE değişiklikleri"
else
    COMMIT_MSG="$1"
fi

# 7️⃣ Commit oluştur (değişiklik varsa)
if git diff --cached --quiet; then
    echo "ℹ️ Commit yapılacak değişiklik yok."
else
    echo "🪶 Commit oluşturuluyor: '$COMMIT_MSG'"
    git commit -m "$COMMIT_MSG"
fi

# 8️⃣ GitHub ile senkronize et
echo "🌐 GitHub ile senkronize ediliyor..."
git pull --rebase origin main
git push origin main && echo "✅ Gönderim tamamlandı!" || echo "❌ Push başarısız!"

echo "✨ İşlem tamam!"
