#!/usr/bin/env bash
# Publishes a folder of UI screenshots to the orphan branch `ui-shots` (never main) and prints
# the Markdown to paste in the PR body, so Yago can review them from the phone.
#
#   scripts/ui-shots-publish.sh <pr-or-branch-label> <screenshots-dir> [<before-dir>]
#
# With <before-dir>, the table shows "antes | después" side by side (same file names).
set -euo pipefail

label=$1
after_dir=$(realpath "$2")
before_dir=${3:+$(realpath "$3")}

root=$(git rev-parse --path-format=absolute --git-common-dir)/..
remote_url=$(git -C "$root" remote get-url origin)
repo=$(sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##' <<<"$remote_url")
wt=$(mktemp -d)/ui-shots

git -C "$root" fetch -q origin ui-shots 2>/dev/null || true
if git -C "$root" rev-parse -q --verify origin/ui-shots >/dev/null; then
  git -C "$root" worktree add -q --detach "$wt" origin/ui-shots
else
  git -C "$root" worktree add -q --orphan -b ui-shots-tmp "$wt"
fi
trap 'git -C "$root" worktree remove --force "$wt"; git -C "$root" branch -q -D ui-shots-tmp 2>/dev/null || true' EXIT

dest="$label/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$wt/$dest/after"
cp "$after_dir"/*.png "$wt/$dest/after/"
[ -f "$after_dir/console-errors.txt" ] && cp "$after_dir/console-errors.txt" "$wt/$dest/"
if [ -n "$before_dir" ]; then
  mkdir -p "$wt/$dest/before"
  cp "$before_dir"/*.png "$wt/$dest/before/"
fi

git -C "$wt" add -A
git -C "$wt" commit -q -m "chore(ui-shots): $dest"
git -C "$wt" push -q origin HEAD:refs/heads/ui-shots

raw="https://raw.githubusercontent.com/$repo/ui-shots/$dest"
echo "### Capturas (390 px y 360 px, claro y oscuro)"
echo
if [ -n "$before_dir" ]; then
  echo "| Pantalla | Antes | Después |"
  echo "|---|---|---|"
else
  echo "| Pantalla | Captura |"
  echo "|---|---|"
fi
for f in "$wt/$dest/after"/*.png; do
  name=$(basename "$f")
  if [ -n "$before_dir" ] && [ -f "$wt/$dest/before/$name" ]; then
    echo "| \`${name%.png}\` | <img src=\"$raw/before/$name\" width=\"220\"> | <img src=\"$raw/after/$name\" width=\"220\"> |"
  elif [ -n "$before_dir" ]; then
    echo "| \`${name%.png}\` | — | <img src=\"$raw/after/$name\" width=\"220\"> |"
  else
    echo "| \`${name%.png}\` | <img src=\"$raw/after/$name\" width=\"220\"> |"
  fi
done
