#!/usr/bin/env bash

dir="$(dirname "$(realpath "$0")")"

function fail {
  echo $1
  exit $2
}

sudo apt-get update
sudo apt-get install -y xmlstarlet

eval "$(ssh-agent -s)"
openssl aes-256-cbc -K $encrypted_6c62aa4934cd_key -iv $encrypted_6c62aa4934cd_iv -in "$dir/deployKey.enc" -out "$dir/deployKey" -d
chmod 600 "$dir/deployKey"
ssh-add "$dir/deployKey"

ssh-add -l

git remote set-url origin git@github.com:FIUS/ICGE2.git
git fetch
