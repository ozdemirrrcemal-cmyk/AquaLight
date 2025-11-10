#!/bin/bash
#  ^=^z^` AIDE  ^f^r GitHub Tam Senkronizasyon Scripti (Cemal  ^vzdemir i  in)

AIDE_DIR="/storage/internal/project/AquaLight"
GIT_DIR="$HOME/projects/AquaLight"

echo " ^=   Senkronizasyon ba ^=l  yor..."

cd "$GIT_DIR" || { echo " ^}^l Git klas  r  ne girilemedi!"; exit 1; }

# Eğer release parametresi verildiyse, release moduna geç
if [ "$1" = "release" ]; then
    echo " 🚀 Release modu aktif: main → release aktarımı başlatılıyor..."
    git checkout release || { echo " ❌ 'release' dalı bulunamadı!"; exit 1; }
    git pull origin release
    git merge main --no-edit || { echo " ⚠️ Merge sırasında çakışma olabilir, kontrol et."; exit 1; }
    git commit --allow-empty -m "release: otomatik build tetikleme"
    git push origin release && echo " ✅ Release dalı gönderildi, CI/CD başlatıldı!" || echo " ❌ Push başarısız!"
    echo " 🎯 Release işlemi tamamlandı."
    exit 0
fi

# 2️⃣ Detached HEAD kontrolü
if [ "$(git rev-parse --abbrev-ref HEAD)" = "HEAD" ]; then
    echo " ^z^y  ^o Detached HEAD alg  land  , main dal  na ge  iliyor..."
    git checkout main || git checkout -b main origin/main
fi

# 3️⃣ Rebase kalıntılarını temizle
if [ -d ".git/rebase-apply" ] || [ -d ".git/rebase-merge" ]; then
    echo " ^=    ^vnceki rebase i ^=lemi tespit edildi, temizleniyor..."
    git rebase --abort 2>/dev/null
fi

# 4️⃣ Dosyaları kopyala
echo " ^=^s^a Dosyalar kopyalan  yor..."
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
    echo " ^z   ^o rsync bulunamad  , cp ile kopyalan  yor..."
    cp -rT "$AIDE_DIR" "$GIT_DIR"
    rm -rf "$GIT_DIR/.git" "$GIT_DIR/.gradle" "$GIT_DIR/build" "$GIT_DIR"/*.iml 2>/dev/null
fi

# 5️⃣ Değişiklikleri ekle
echo " ^=   De ^=i ^=iklikler haz  rlan  yor..."
git add -A

# 6️⃣ Commit mesajı
if [ -z "$1" ]; then
    COMMIT_MSG="sync: AIDE de ^=i ^=iklikleri"
else
    COMMIT_MSG="$1"
fi

# 7️⃣ Commit oluştur
if git diff --cached --quiet; then
    echo " ^d   ^o Commit yap  lacak de ^=i ^=iklik yok."
else
    echo " ^=   Commit olu ^=turuluyor: '$COMMIT_MSG'"
    git commit -m "$COMMIT_MSG"
fi

# 8️⃣ GitHub senkronizasyonu
echo " ^=^l^p GitHub ile senkronize ediliyor..."
git pull --rebase origin main
git push origin main && echo " ^|^e G  nderim tamamland  !" || echo " ^}^l Push ba ^=ar  s  z!"

echo " ^|     ^=lem tamam!"
