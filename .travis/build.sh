#!/bin/bash

function deploy() {
  if [ "$1" != "porcelain" ];
  then
    echo "Executing Maven Build (${TRAVIS_BRANCH})..."
    mvn clean deploy -B -Dcodebase.directory=$(pwd) -Dcoverage.reports.dir=$(pwd)/target/all --settings config/src/main/resources/ci/settings.xml
  else
    echo "Skipped Maven Build (${TRAVIS_BRANCH}) [Porcelain]"
  fi
}

function install() {
  if [ "$1" != "porcelain" ];
  then
    echo "Executing Maven Build (${TRAVIS_BRANCH})..."
    mvn clean install -B -Dcoverage.reports.dir=$(pwd)/target/all --settings config/src/main/resources/ci/settings.xml
  else
    echo "Skipped Maven Build (${TRAVIS_BRANCH}) [Porcelain]"
  fi
}

mode=$1

if [ "${TRAVIS_PULL_REQUEST}" = "false" ];
then
  case "${TRAVIS_BRANCH}" in
    master)   deploy "$mode";;
    develop)  deploy "$mode";;
    feature*) install "$mode";;
    *)        install "$mode";;
  esac
else
  install "$mode"
fi
