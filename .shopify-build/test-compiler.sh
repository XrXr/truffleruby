#!/bin/bash

set -eo pipefail

if [[ $(uname) == "Darwin" ]]; then
  source .shopify-build/setup-macos.sh
else
  source .shopify-build/setup-linux.sh
fi

unset GEM_HOME GEM_PATH GEM_ROOT RUBY_ENGINE RUBY_ROOT RUBY_VERSION
PATH=$PWD/.shopify-build/build/bin:$PATH
ruby --version

ruby --experimental-options --engine.CompilationExceptionsAreThrown test/truffle/compiler/optional-assignment-lazy-load/optional-assignment-lazy-load.rb
