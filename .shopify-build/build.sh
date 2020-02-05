#!/bin/bash

set -eo pipefail

if [[ $(uname) == "Darwin" ]]; then
  source .shopify-build/setup-macos.sh
fi

echo "--- Installing JVMCI"
tool/jt.rb install jvmci

echo "--- Installing mx"
tool/jt.rb mx --version

echo "--- Installing Graal"
tool/jt.rb mx sforceimports

echo "--- Building"
config=shopify
tool/jt.rb build --env $config

echo "--- Packaging"
build_home=$(tool/jt.rb mx --env $config graalvm-home)
os=$($build_home/bin/ruby -e "puts({'darwin' => 'macos'}.fetch(Truffle::System.host_os, &:itself))")
cpu=$($build_home/bin/ruby -e "puts({'x86_64' => 'amd64'}.fetch(Truffle::System.host_cpu, &:itself))")
revision=$($build_home/bin/ruby -e 'puts TruffleRuby.revision')
name=truffleruby-shopify-$os-$cpu-$revision
mkdir .shopify-build/package
pushd .shopify-build/package
cp -R $build_home $name # -r would break links or attributes so we use -R
tar -zcf ../artifacts/$name.tar.gz $name
popd

echo "--- Package test and build info"
mkdir .shopify-build/package-test
pushd .shopify-build/package-test
tar -zxf ../artifacts/$name.tar.gz
cat $name/release
echo # release file has no line ending
$name/bin/ruby --version:graalvm
$name/bin/ruby --version
$name/bin/ruby --native --version
$name/bin/ruby --jvm --version
popd
