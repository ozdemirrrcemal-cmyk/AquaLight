#!/bin/bash

# 🚀 AIDE → GitHub otomatik senkronizasyon scripti (Cemal Özdemir için)

AIDE_DIR="/storage/internal/project/AquaLight"
GIT_DIR="$HOME/projects/AquaLight"

echo "📁 AIDE projesi senkronize ediliyor..."

# 1️⃣ AIDE projesi mevcut mu?
if [ ! -d "$AIDE_DIR" ]; then
    echo "❌ AIDE dizini bulunamadı: $AIDE_DIR"
    exit 1
fi

# 2️⃣ Git klasörü mevcut mu?
if [ ! -d "$GIT_DIR/.git" ]; then
    echo "❌ Git dizini hatalı veya repo başlatılmamış!"
    exit 1
fi

# 3️⃣ Güncel değişiklikleri kopyala (var olanları günceller, silinenleri algılar)
rsync -av --delete "$AIDE_DIR"/ "$GIT_DIR"/

# 4️⃣ Git işlemleri
cd "$GIT_DIR" || { echo "❌ Git klasörüne girilemedi!"; exit 1; }

if [ -z "$1" ]; then
    COMMIT_MSG="sync: AIDE değişiklikleri"
else
    COMMIT_MSG="$1"
fi

echo "🧩 Değişiklikler git'e ekleniyor..."
git add -A

echo "🧾 Commit oluşturuluyor: '$COMMIT_MSG'"
git commit -m "$COMMIT_MSG" || echo "⚠️ Commit yapılacak değişiklik yok."

echo "🚀 GitHub'a gönderiliyor..."
git push && echo "✅ Senkronizasyon tamamlandı!" || echo "❌ Push başarısız!"

echo "✨ İşlem tamam!"
