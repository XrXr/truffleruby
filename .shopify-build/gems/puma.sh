repository https://github.com/puma/puma.git puma
apply-patch puma.patch

# macOS only allows 256 simultaneous FDs, which is too low
if [[ $(uname) == "Darwin" ]]; then ulimit -n 2560; fi

bundle install
bundle exec rake compile
NIO4R_PURE=true CI=true bundle exec rake test
