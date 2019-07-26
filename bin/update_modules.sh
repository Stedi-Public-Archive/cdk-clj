#!/usr/bin/env bash

set -ue

CDK_MODULES_DIR=./target/cdk-modules
CDK_MODULES_TARGET=${CDK_MODULES_DIR}/latest
CDK_MODULES_ZIP=${CDK_MODULES_TARGET}.zip

rm -fr $CDK_MODULES_DIR
mkdir -p $CDK_MODULES_TARGET
ZIP_URL=$(curl -s https://api.github.com/repos/aws/aws-cdk/releases/latest | jq -r '.assets[0].browser_download_url')
wget $ZIP_URL -O $CDK_MODULES_ZIP
unzip $CDK_MODULES_ZIP -d $CDK_MODULES_TARGET
rm ./resources/*.jsii.tgz
cp $CDK_MODULES_TARGET/js/*.jsii.tgz ./resources
ls resources | grep jsii > resources/jsii-modules.txt
