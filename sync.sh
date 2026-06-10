#!/usr/bin/env bash
set -u
# AIDE -> GitHub Tam Senkronizasyon Scripti (Cemal Özdemir)
# Kullanım:
#   ./sync.sh [debug|release] "commit mesajı"
# Default: debug, branch -> main

AIDE_DIR="/storage/internal_new/project/AquaLight"
GIT_DIR="$HOME/projects/AquaLight"

MODE=${1:-debug}
MESSAGE=${2:-"sync: AIDE değişiklikleri"}

BRANCH="main"
if [[ "$MODE" == "release" ]]; then
  BRANCH="release"
fi

echo "🧹 Senkronizasyon başlıyor ($MODE → $BRANCH)..."

# GIT dizinine geç
cd "$GIT_DIR" || { echo "❌ Git klasörüne girilemedi: $GIT_DIR"; exit 1; }

# Uzak dalı güncelle (varsa)
git fetch origin "$BRANCH" --quiet 2>/dev/null || true

# Aktif dalı öğren
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "HEAD")

# Eğer farklı bir daldaysak, güvenli şekilde geçiş yap (stash kullan)
if [[ "$CURRENT_BRANCH" != "$BRANCH" ]]; then
  echo "⚙️ Şu anda '$CURRENT_BRANCH' dalındasın, '$BRANCH' dalına geçiliyor..."
  # Geçici değişiklikleri stash et (untracked dahil)
  git stash push -u -m "sync: geçici stash before checkout" >/dev/null 2>&1 || true

  # Checkout yap (varsa remote izleyerek, yoksa yeni oluştur)
  if git rev-parse --verify "$BRANCH" >/dev/null 2>&1; then
    git checkout "$BRANCH" || { echo "❌ '$BRANCH' dalına geçilemedi."; git stash pop >/dev/null 2>&1 || true; exit 1; }
  else
    # yaratmaya çalış, origin/branch varsa onu takip et
    if git ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
      git checkout -b "$BRANCH" "origin/$BRANCH" || { echo "❌ '$BRANCH' oluşturulamadı."; git stash pop >/dev/null 2>&1 || true; exit 1; }
    else
      git checkout -b "$BRANCH" || { echo "❌ Yeni '$BRANCH' oluşturulamadı."; git stash pop >/dev/null 2>&1 || true; exit 1; }
    fi
  fi

  # Stash geri yükle (başarısız olsa da devam et, kullanıcıya bilgi ver)
  if git stash list | grep -q "sync: geçici stash before checkout"; then
    git stash pop --index >/dev/null 2>&1 || echo "⚠️ Stash geri yüklenemedi — lütfen manuel kontrol et."
  fi
fi

# Rebase / yarım işlem varsa temizle
if [ -d ".git/rebase-apply" ] || [ -d ".git/rebase-merge" ]; then
  echo "🧼 Önceki rebase işlemi tespit edildi, temizleniyor..."
  git rebase --abort 2>/dev/null || true
fi

# Dosyaları AIDE'den GIT dizinine senkronize et
echo "📦 Dosyalar kopyalanıyor (AIDE -> Git)..."
if command -v rsync >/dev/null 2>&1; then
  rsync -a --delete \
    --exclude='.git' \
    --exclude='.gradle' \
    --exclude='build' \
    --exclude='app/build' \
    --exclude='.idea' \
    --exclude='.aide' \
    --exclude='captures' \
    --exclude='*.iml' \
    --exclude='local.properties' \
    --exclude='sync.sh' \
    --include='.github/***' \
    "$AIDE_DIR"/ "$GIT_DIR"/ >/dev/null 2>&1 || echo "⚠️ rsync sırasında uyarı/veri atlandı."
else
  echo "⚠️ rsync bulunamadı — cp ile kopyalanıyor (daha az tercih edilir)..."
  cp -rT "$AIDE_DIR" "$GIT_DIR" 2>/dev/null || { echo "❌ cp başarısız."; exit 1; }
fi

# Değişiklikleri ekle / commit
echo "🧩 Değişiklikler hazırlanıyor..."
git add -A

if git diff --cached --quiet; then
  echo "ℹ️ Commit yapılacak değişiklik yok."
else
  echo "✅ Commit oluşturuluyor: '$MESSAGE'"
  git commit -m "$MESSAGE" || { echo "❌ Commit sırasında hata."; exit 1; }
fi

# Uzakla senkronize et
echo "🌐 GitHub ($BRANCH) ile senkronize ediliyor..."
git pull --rebase origin "$BRANCH" || echo "⚠️ pull sırasında uyarı/konflikt olabilir, manuel kontrol edin."
git push origin "$BRANCH" && echo "🎉 Gönderim tamamlandı!" || echo "❌ Push başarısız; yetki veya dal hatası olabilir."

echo "✨ İşlem tamam!"
