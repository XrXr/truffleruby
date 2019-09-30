#!/usr/bin/env bash

# get the absolute path of the executable and resolve symlinks
SELF_PATH=$(cd "$(dirname "$0")" && pwd -P)/$(basename "$0")
while [ -h "$SELF_PATH" ]; do
  # 1) cd to directory of the symlink
  # 2) cd to the directory of where the symlink points
  # 3) get the pwd
  # 4) append the basename
  DIR=$(dirname "$SELF_PATH")
  SYM=$(readlink "$SELF_PATH")
  SELF_PATH=$(cd "$DIR" && cd "$(dirname "$SYM")" && pwd)/$(basename "$SYM")
done

# shellcheck source=test/truffle/common.sh.inc
source "$(dirname $SELF_PATH)/../common.sh.inc"
QUERY="ruby $(dirname $SELF_PATH)/../../../tool/query-versions-json.rb"

function check_launchers() {
  if [ -n "$2" ]; then
    [[ "$(${1}truffleruby --version)" =~ truffleruby\ .*\ like\ ruby\ $($QUERY ruby.version) ]]
    [[ "$(${1}ruby --version)" =~ truffleruby\ .*\ like\ ruby\ $($QUERY ruby.version) ]]
  fi
  [[ "$(${1}gem --version)" =~ ^$($QUERY gems.default.gem)$ ]]
  [[ "$(${1}irb --version)" =~ ^irb\ $($QUERY gems.default.irb) ]]
  [[ "$(${1}rake --version)" =~ ^rake,\ version\ $($QUERY gems.default.rake) ]]
  [[ "$(${1}rdoc --version)" =~ ^$($QUERY gems.default.rdoc)$ ]]
  [[ "$(${1}ri --version)" =~ ^ri\ $($QUERY gems.default.rdoc)$ ]]
}

function check_in_dir() {
  cd $1
  pwd
  echo "** Check all launchers work in $1 dir"
  check_launchers "./" true
  echo "** Check all launchers work in $1 dir using -S option"
  check_launchers "./truffleruby -S "
  cd -
}

# Use the Ruby home of the `jt ruby` launcher
graalvm_home="$(jt ruby -e "print Truffle::System.get_java_property('org.graalvm.home')")"
ruby_home="$(jt ruby -e 'print Truffle::Boot.ruby_home')"
cd "$ruby_home"

echo '** Check all launchers work'
check_launchers bin/ true
check_in_dir bin

if [ -n "$graalvm_home" ]; then
  check_in_dir "$graalvm_home/bin"
  if [ -d "$graalvm_home/jre/bin" ]; then
    check_in_dir "$graalvm_home/jre/bin"
  fi
fi

echo '** Check gem executables are installed in all bin dirs'

cd "$(dirname $SELF_PATH)/hello-world"
"$ruby_home/bin/gem" build hello-world.gemspec
"$ruby_home/bin/gem" install hello-world-0.0.1.gem
cd -

version="$(bin/ruby -v)"
test "$(bin/hello-world.rb)" = "Hello world! from $version"
if [ -n "$graalvm_home" ]; then
  test "$($graalvm_home/bin/hello-world.rb)" = "Hello world! from $version"
  if [ -d "$graalvm_home/jre/bin" ]; then
    test "$($graalvm_home/jre/bin/hello-world.rb)" = "Hello world! from $version"
  fi
fi

bin/gem uninstall hello-world -x

test ! -f "bin/hello-world.rb"
if [ -n "$graalvm_home" ]; then
  test ! -f "$graalvm_home/bin/hello-world.rb"
  if [ -d "$graalvm_home/jre/bin" ]; then
    test ! -f "$graalvm_home/jre/bin/hello-world.rb"
  fi
fi

echo '** Check bundled gems'

# see doc/contributor/stdlib.md
bundled_gems=(
  "did_you_mean $($QUERY gems.bundled.did_you_mean)"
  "minitest $($QUERY gems.bundled.minitest)"
  "net-telnet $($QUERY gems.bundled.net-telnet)"
  "power_assert $($QUERY gems.bundled.power_assert)"
  "rake $($QUERY gems.bundled.rake)"
  "test-unit $($QUERY gems.bundled.test-unit)"
  "xmlrpc $($QUERY gems.bundled.xmlrpc)"
)
gem_list=$(bin/gem list)

for bundled_gem in "${bundled_gems[@]}"; do
  bundled_gem="${bundled_gem//./\\.}"
  bundled_gem="${bundled_gem/ /.*}"
  [[ "${gem_list}" =~ ${bundled_gem} ]]
done
