#!/bin/bash --login
# Credit for the script goes to https://gist.github.com/claybridges/cea5d4afd24eda268164

# This allows you to use rvm in a script. Otherwise you get an
# error along the lines of "cannot use rvm as function".
[[ -s "$HOME/.rvm/scripts/rvm" ]] && source "$HOME/.rvm/scripts/rvm"

# Cause rvm to use system ruby. AFAIK, this is effective only for
# the scope of this script.
rvm use system

unset RUBYLIB
unset RUBYOPT
unset BUNDLE_BIN_PATH
unset _ORIGINAL_GEM_PATH
unset BUNDLE_GEMFILE

set -x          # echoes commands
xcodebuild "$@" # calls xcodebuild with all the arguments passed to this
