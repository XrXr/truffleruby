#!/usr/bin/env bash
# Hook run after extraction in the installation directory by a Ruby installer.
# Useful to perform tasks that depend on the user machine and
# cannot be generally done in advance before building the release tarball.

set -e

lib_truffle=$(cd "$(dirname "$0")" && pwd -P)
root=$(dirname "$(dirname "$lib_truffle")")

# In case the script shells out to ruby, make sure to use truffleruby and not rely on system ruby
export PATH="$root/bin:$PATH"

cd "$root"

function recompile_openssl() {
  cd src/main/c/openssl
  "$root/bin/truffleruby" -w extconf.rb
  make
  cp "openssl.$(ruby -e "print RbConfig::CONFIG['DLEXT']")" "$root/lib/mri"
}

if [ "$TRUFFLERUBY_RECOMPILE_OPENSSL" == "false" ]; then
  echo "Skipping recompilation of the OpenSSL extension (TRUFFLERUBY_RECOMPILE_OPENSSL=false)"
elif [ "$TRUFFLERUBY_RECOMPILE_OPENSSL" == "true" ]; then
  echo "Recompiling the OpenSSL C extension (TRUFFLERUBY_RECOMPILE_OPENSSL=true)"
  recompile_openssl
else
  echo "Recompiling the OpenSSL C extension (against the installed libssl)"
  recompile_openssl
fi

echo "TruffleRuby was successfully installed in $root"
