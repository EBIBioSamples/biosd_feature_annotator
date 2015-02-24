#!/bin/sh

# Deploys the command line binary. This doesn't do much, but it's needed with Bamboo.
# 

MYDIR=$(dirname "$0")
cd "$MYDIR"/target

target="$1"

if [ "$target" == "" ]; then
  target=.
fi;

echo
echo 
echo _______________ Deploying the Command Line Binary to $target _________________

yes A| unzip biosd_feature_annotator_*.zip -d "$target"
chmod -R ug=rwX,o=rX "$target/biosd_feature_annotator_"*

echo ______________________________________________________________________________
echo
echo
echo
