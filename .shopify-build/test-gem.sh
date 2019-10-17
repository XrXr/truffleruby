#!/bin/bash

set -eo pipefail

if [[ $(uname) == "Darwin" ]]; then
  source .shopify-build/setup-macos.sh
fi

unset GEM_HOME GEM_PATH GEM_ROOT RUBY_ENGINE RUBY_ROOT RUBY_VERSION
PATH=$PWD/.shopify-build/build/bin:$PATH
ruby --version

gem=${1}
script_path="$PWD/.shopify-build/gems/$gem.sh"
if [[ ! -f $script_path ]]; then
  echo "No script found for $gem"
  exit 1
fi

repository() {
  git clone --single-branch "${1}" "${2-repo}"
  pushd "${2-repo}"
}

apply-patch() {
  git apply ../.shopify-build/gems/${1}
}

while read -r line
do
  [[ $line =~ ^\\s*#.*$ ]] && continue
  [[ $line =~ ^\\s*$ ]] && continue
  echo "--- $line"
  eval "$line"
done < $script_path
