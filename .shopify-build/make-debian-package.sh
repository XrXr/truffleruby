#!/bin/bash

set -exuo pipefail

# Intentionally use a subshell to preserve our environment.
# We only care about the tar extraction in the script.
bash .shopify-build/use-truffleruby.sh

pushd .shopify-build
mkdir -p truffle/DEBIAN
mkdir -p truffle/opt
mv package truffle/opt/truffleruby

RUBY_VERSION=$(truffle/opt/truffleruby/bin/ruby -e "puts RUBY_VERSION")
SHORT_COMMIT_SHA=$(echo "${BUILDKITE_COMMIT}" | head -c 7)
PACKAGE_VERSION="${RUBY_VERSION}+${SHORT_COMMIT_SHA}-1"
cat << EOF > truffle/DEBIAN/control
Package: truffleruby-shopify
Version: $PACKAGE_VERSION
Section: base
Priority: optional
Architecture: amd64
Depends: tzdata, build-essential, automake, libtool, pkg-config, libssl-dev, libz-dev
Maintainer: TruffleRuby Shopify <truffleruby@shopify.com>
Description: Truffle Ruby
 A Ruby implementation built with GraalVM using the Truffle framework.
EOF

dpkg-deb --build truffle
package_cloud push --url https://packages.shopify.io shopify/truffleruby/ubuntu/xenial truffle.deb
