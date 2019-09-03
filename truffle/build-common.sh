# Don't run this directly - see README.md

# Get sources
git clone git@github.com:Shopify/graal-jvmci-8-shopify.git
git clone git@github.com:Shopify/mx-shopify.git
git clone git@github.com:Shopify/graal-shopify.git
git clone git@github.com:Shopify/truffleruby-shopify.git

# Build JDK 8 JVMCI
pushd graal-jvmci-8-shopify
$bootstrap_java_home/bin/java -version
JAVA_HOME=$bootstrap_java_home $build_dir/mx-shopify/mx build
popd
jvmci_home=`echo $build_dir/graal-jvmci-8-shopify/openjdk*/*-amd64/product/$home_prefix`
$jvmci_home/bin/java -version

# Build GraalVM
pushd graal-shopify/vm
# This configuration is a combination of truffleruby/mx.truffleruby/native and graal/vm/mx.vm/ce-complete
JAVA_HOME=$jvmci_home \
DYNAMIC_IMPORTS=/substratevm,/tools,/sulong,truffleruby \
SKIP_LIBRARIES=native-image-agent \
FORCE_BASH_LAUNCHERS=polyglot,lli,native-image,graalvm-native-clang++,native-image-configure,graalvm-native-clang,gu \
DISABLE_INSTALLABLES=true \
LIBGRAAL=true \
EXCLUDE_COMPONENTS=nju \
$build_dir/mx-shopify/mx build
popd

# This is the name that mx uses for the build directory, automatically made from the configuration
configure_name=GRAALVM_LIBGRAAL_BGRAALVM-NATIVE-CLANG_BGRAALVM-NATIVE-CLANG++_BLLI_BNATIVE-IMAGE_BNATIVE-IMAGE-CONFIGURE_INS_LIBPOLY_LLP_NI_NIL_PRO_RBY_RBYL_SLG_SNATIVE-IMAGE-AGENT_VVM

# Expand that to find the directory where bin is now
built_bin=`echo graal-shopify/vm/mxbuild/*-amd64/$configure_name/*/$home_prefix/bin`

# Check we can actually at least run TruffleRuby
$built_bin/ruby -v

# Create the name that we want to call the distribution (the tarball and the directory in it)
shopify_name=truffleruby-shopify-$platform-$($built_bin/ruby -e 'puts TruffleRuby.revision')

# Make a tarball
pushd graal-shopify/vm/mxbuild/*-amd64/$configure_name
mv graalvm-*/$home_prefix $shopify_name
tar -zcf $build_dir/$shopify_name.tar.gz $shopify_name
popd

# Checksum
shasum -a 256 $shopify_name.tar.gz > $shopify_name.tar.gz.sha256
cat $shopify_name.tar.gz.sha256

# Print the contents we're shipping
tar -ztf $shopify_name.tar.gz
tar -zxf $shopify_name.tar.gz -O $shopify_name/release

# Print the size of the tarball
du -h $shopify_name.tar.gz
