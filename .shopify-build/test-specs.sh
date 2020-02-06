#!/bin/bash

set -eo pipefail

if [[ $(uname) == "Darwin" ]]; then
  source .shopify-build/setup-macos.sh
else
  source .shopify-build/setup-linux.sh
fi

source .shopify-build/use-truffleruby.sh

ruby spec/mspec/bin/mspec --config spec/truffle.mspec --format specdoc --excl-tag fails "$@"
