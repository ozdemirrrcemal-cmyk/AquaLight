#!/bin/bash
# 🚀 AIDE → GitHub tam senkronizasyon scripti (Cemal Özdemir için)

AIDE_DIR="/storage/internal/project/AquaLight"
GIT_DIR="$HOME/projects/AquaLight"

echo "🧹 Senkronizasyon başlıyor..."

# 🔹 Git klasörüne gir
cd "$GIT_DIR" || { echo "❌ Git klasörüne girilemedi!"; exit 1; }

# 🔹 Eğer detached HEAD varsa main'e dön
if [ "$(git rev-parse --abbrev-ref HEAD)" = "HEAD" ]; then
    echo "⚙️ Detached HEAD algılandı, main dalına geçiliyor..."
    git checkout main || git checkout -b main origin/main
fi

# 🔹 Rebase kalıntısı varsa iptal et
if [ -d ".git/rebase-apply" ] || [ -d ".git/rebase-merge" ]; then
    echo "⚠️ Önceki rebase işlemi tespit edildi, temizleniyor..."
    git rebase --abort 2>/dev/null
fi

# 🔹 Dosyaları birebir senkronize et (AIDE'deki silinenler de kaldırılır)
echo "📁 Dosyalar kopyalanıyor..."
rsync -av --delete "$AIDE_DIR"/ "$GIT_DIR"/ \
  --exclude=".git" \
  --exclude=".gradle" \
  --exclude="build" \
  --exclude="*.iml" \
  >/dev/null

# 🔹 Değişiklikleri ekle (silinenler dahil)
echo "🧩 Değişiklikler hazırlanıyor..."
git add -A

# 🔹 Commit mesajı
if [ -z "$1" ]; then
    COMMIT_MSG="sync: AIDE değişiklikleri"
else
    COMMIT_MSG="$1"
fi

# 🔹 Commit oluştur
if git diff --cached --quiet; then
    echo "⚠️ Commit yapılacak değişiklik yok."
else
    echo "🧾 Commit oluşturuluyor: '$COMMIT_MSG'"
    git commit -m "$COMMIT_MSG"
fi

# 🔹 GitHub ile senkronize et
echo "🌐 GitHub ile senkronize ediliyor..."
git pull --rebase origin main
git push origin main && echo "✅ Gönderim tamamlandı!" || echo "❌ Push başarısız!"

echo "✨ İşlem tamam!"