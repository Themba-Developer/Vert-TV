#!/bin/sh
set -eu
cd "$(dirname "$0")/../backend"
npm ci
npx wrangler whoami
npx wrangler d1 create vert-tv
printf '\nCopy the returned database_id into backend/wrangler.jsonc, then follow README.md.\n'
