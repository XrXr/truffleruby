# Take a TruffleRuby binary tarball from the artifact directory, extract it, and put it onto the path

unset GEM_HOME GEM_PATH GEM_ROOT RUBY_ENGINE RUBY_ROOT RUBY_VERSION
mkdir .shopify-build/package
tar -zxf .shopify-build/artifacts/*.tar.gz -C .shopify-build/package --strip-components=1
PATH=$PWD/.shopify-build/package/bin:$PATH
ruby --version:graalvm
ruby --version
