#!/bin/bash

rm  my-keystore.jks    2> /dev/null
rm  my-truststore.jks  2> /dev/null

keytool -genkeypair -alias e1  -dname cn=e1                        \
  -validity 10000 -keyalg RSA -keysize 2048                        \
  -keystore my-keystore.jks -keypass Keypass -storepass Storepass



keytool -list -v  -storepass Storepass -keystore  my-keystore.jks

echo "================================================="
echo "Keystore generated. Now generating truststore ..."
echo "================================================="
read -p "Press a key to continue."

  keytool -exportcert -alias e1                                        \
    -keystore my-keystore.jks -keypass Keypass -storepass Storepass    \
| keytool -importcert -noprompt -alias e1                              \
    -keystore my-truststore.jks -keypass Keypass -storepass Storepass

keytool -list -v  -storepass Storepass -keystore  my-truststore.jks


