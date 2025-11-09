#!/bin/bash

# 🔄 AIDE -> GitHub otomatik senkronizasyon scripti
# Cemal Özdemir için özel yapılandırma 😎

AIDE_DIR="/storage/internal/project/AquaLight"
GIT_DIR="$HOME/projects/AquaLight"

echo "📁 AIDE projesi senkronize ediliyor..."
cp -r "$AIDE_DIR"/* "$GIT_DIR"/ 2>/dev/null || { echo "❌ Kopyalama hatası! AIDE dizinine erişilemiyor."; exit 1; }

cd "$GIT_DIR" || { echo "❌ Git klasörüne girilemedi!"; exit 1; }

# Eğer kullanıcı mesaj verdiyse onu commit mesajı olarak kullan
if [ -z "$1" ]; then
    COMMIT_MSG="sync: AIDE değişiklikleri"
else
    COMMIT_MSG="$1"
fi

echo "🧩 Değişiklikler git'e ekleniyor..."
git add .

echo "🧾 Commit oluşturuluyor: '$COMMIT_MSG'"
git commit -m "$COMMIT_MSG" || echo "⚠️ Commit yapılacak değişiklik yok."

echo "🚀 GitHub'a gönderiliyor..."
git push && echo "✅ Senkronizasyon tamamlandı!" || echo "❌ Push başarısız!"

echo "✨ İşlem tamam!"
