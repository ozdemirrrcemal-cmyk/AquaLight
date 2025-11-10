#!/bin/bash
# 🚀 AIDE → GitHub otomatik senkronizasyon scripti
# Cemal Özdemir - Kişisel proje yapılandırması

AIDE_DIR="/storage/internal/project/AquaLight"
GIT_DIR="$HOME/projects/AquaLight"

echo "📁 AIDE projesi senkronize ediliyor..."

# Dosyaları kopyala (AIDE -> Git)
cp -r "$AIDE_DIR"/* "$GIT_DIR"/ 2>/dev/null || {
    echo "❌ Kopyalama hatası! AIDE dizinine erişilemiyor."
    exit 1
}

# Git klasörüne geç
cd "$GIT_DIR" || {
    echo "❌ Git klasörüne girilemedi!"
    exit 1
}

# Commit mesajı kontrolü
if [ -z "$1" ]; then
    COMMIT_MSG="sync: AIDE değişiklikleri"
else
    COMMIT_MSG="$1"
fi

# Tüm değişiklikleri (ekleme, silme dahil) ekle
echo "🧩 Değişiklikler git'e ekleniyor..."
git add -A

# Commit oluştur
echo "🧾 Commit oluşturuluyor: '$COMMIT_MSG'"
git commit -m "$COMMIT_MSG" || echo "⚠️ Commit yapılacak değişiklik yok."

# GitHub'a gönder
echo "🚀 GitHub'a gönderiliyor..."
git push && echo "✅ Senkronizasyon tamamlandı!" || echo "❌ Push başarısız!"

echo "✨ İşlem tamam!"
