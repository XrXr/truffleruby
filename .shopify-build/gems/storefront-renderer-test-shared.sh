repository-tag git@github.com:Shopify/storefront-renderer.git truffle-ci storefront-renderer

bundle install --path vendor/bundle
bundle exec rake db:load test:shared
