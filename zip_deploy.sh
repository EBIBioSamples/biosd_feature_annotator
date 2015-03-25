#!/bin/sh

# Deploys the command line binary. This doesn't do much, but it's needed with Bamboo.
# 

MYDIR=$(dirname "$0")

cd "$MYDIR"
version="$(mvn help:evaluate -Dexpression=project.version | grep -v '\[')"

cd target

target="$1"

if [ "$target" == "" ]; then
  target=.
fi;

echo
echo 
echo "_______________ Deploying the Command Line Binary ($version) to $target _________________"

# We need to remove old versions and unused libs
rm -Rf "$target/biosd_feature_annotator_${version}/lib"

yes A| unzip biosd_feature_annotator_${version}.zip -d "$target"
chmod -R ug=rwX,o=rX "$target/biosd_feature_annotator_${version}"

echo ______________________________________________________________________________
echo
echo
echo
