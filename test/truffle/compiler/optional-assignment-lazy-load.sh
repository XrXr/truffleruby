#!/usr/bin/env bash

source test/truffle/common.sh.inc

jt --use jvm-ce ruby --vm.Dgraal.TruffleCompilationExceptionsAreFatal=true test/truffle/compiler/optional-assignment-lazy-load/optional-assignment-lazy-load.rb
