#!/bin/bash

set -eo pipefail

if [[ $(uname) == "Darwin" ]]; then
  source .shopify-build/setup-macos.sh
else
  source .shopify-build/setup-linux.sh
fi

source .shopify-build/use-truffleruby.sh

ruby --experimental-options --engine.CompilationExceptionsAreThrown test/truffle/compiler/optional-assignment-lazy-load/optional-assignment-lazy-load.rb
