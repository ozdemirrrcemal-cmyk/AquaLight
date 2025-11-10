#!/bin/bash
#  ^=^z^` AIDE  ^f^r GitHub Tam Senkronizasyon Scripti (Cemal  ^vzdemir i  in)

AIDE_DIR="/storage/internal/project/AquaLight"
GIT_DIR="$HOME/projects/AquaLight"

echo " ^=   Senkronizasyon ba ^=l  yor..."

cd "$GIT_DIR" || { echo " ^}^l Git klas  r  ne girilemedi!"; exit 1; }

# EÃÂer release parametresi verildiyse, release moduna geÃÂ§
if [ "$1" = "release" ]; then
    echo " ÄÂÂÂ Release modu aktif: main Ã¢ÂÂ release aktarÃÂ±mÃÂ± baÃÂlatÃÂ±lÃÂ±yor..."
    git checkout release || { echo " Ã¢ÂÂ 'release' dalÃÂ± bulunamadÃÂ±!"; exit 1; }
    git pull origin release
    git merge main --no-edit || { echo " Ã¢ÂÂ Ã¯Â¸Â Merge sÃÂ±rasÃÂ±nda ÃÂ§akÃÂ±ÃÂma olabilir, kontrol et."; exit 1; }
    git commit --allow-empty -m "release: otomatik build tetikleme"
    git push origin release && echo " Ã¢ÂÂ Release dalÃÂ± gÃÂ¶nderildi, CI/CD baÃÂlatÃÂ±ldÃÂ±!" || echo " Ã¢ÂÂ Push baÃÂarÃÂ±sÃÂ±z!"
    echo " ÄÂÂÂ¯ Release iÃÂlemi tamamlandÃÂ±."
    exit 0
fi

# 2Ã¯Â¸ÂÃ¢ÂÂ£ Detached HEAD kontrolÃÂ¼
if [ "$(git rev-parse --abbrev-ref HEAD)" = "HEAD" ]; then
    echo " ^z^y  ^o Detached HEAD alg  land  , main dal  na ge  iliyor..."
    git checkout main || git checkout -b main origin/main
fi

# 3Ã¯Â¸ÂÃ¢ÂÂ£ Rebase kalÃÂ±ntÃÂ±larÃÂ±nÃÂ± temizle
if [ -d ".git/rebase-apply" ] || [ -d ".git/rebase-merge" ]; then
    echo " ^=    ^vnceki rebase i ^=lemi tespit edildi, temizleniyor..."
    git rebase --abort 2>/dev/null
fi

# 4Ã¯Â¸ÂÃ¢ÂÂ£ DosyalarÃÂ± kopyala
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

# 5Ã¯Â¸ÂÃ¢ÂÂ£ DeÃÂiÃÂiklikleri ekle
echo " ^=   De ^=i ^=iklikler haz  rlan  yor..."
git add -A

# 6Ã¯Â¸ÂÃ¢ÂÂ£ Commit mesajÃÂ±
if [ -z "$1" ]; then
    COMMIT_MSG="sync: AIDE de ^=i ^=iklikleri"
else
    COMMIT_MSG="$1"
fi

# 7Ã¯Â¸ÂÃ¢ÂÂ£ Commit oluÃÂtur
if git diff --cached --quiet; then
    echo " ^d   ^o Commit yap  lacak de ^=i ^=iklik yok."
else
    echo " ^=   Commit olu ^=turuluyor: '$COMMIT_MSG'"
    git commit -m "$COMMIT_MSG"
fi

# 8Ã¯Â¸ÂÃ¢ÂÂ£ GitHub senkronizasyonu
echo " ^=^l^p GitHub ile senkronize ediliyor..."
git pull --rebase origin main
git push origin main && echo " ^|^e G  nderim tamamland  !" || echo " ^}^l Push ba ^=ar  s  z!"

echo " ^|     ^=lem tamam!"
