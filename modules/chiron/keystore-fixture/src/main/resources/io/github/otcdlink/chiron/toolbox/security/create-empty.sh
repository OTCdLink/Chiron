#!/bin/sh

keytool -genkeypair -dname "cn=none" -alias foo -keypass nopassword -keystore empty.jks -storepass Storepass -validity 10000 -keyalg RSA -keysize 2048

keytool -delete -alias foo -keypass nopassword -keystore empty.jks -storepass Storepass

keytool -list -v  -storepass Storepass -keystore empty.jks
