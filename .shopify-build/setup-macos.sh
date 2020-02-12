#!/bin/bash

# Legacy Xcode setup

export SDKROOT=`xcrun --show-sdk-path`

# Dependencies needed for building TruffleRuby

brew install \
  openssl

# Dependencies only needed for tests

brew install \
  autoconf \
  automake \
  cmake \
  libtool \
  libxml2 \
  libxslt \
  memcached \
  pkgconfig \
  ragel \
  snappy \
  sqlite3 \
  mysql

bundle config --global build.mysql2 --with-opt-dir="$(brew --prefix openssl)"
