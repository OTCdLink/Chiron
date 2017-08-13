#!/bin/bash

# https://weblogs.java.net/blog/evanx/archive/2015/02/13/considering-private-certificate-authority

rm  their-keystore.jks 2> /dev/null
rm  my-keystore.jks    2> /dev/null
rm  my-truststore.jks  2> /dev/null

echo "===================================================="
echo "Creating fake third-party chain ca2 -> ca1 -> ca ..."
echo "===================================================="

keytool -genkeypair -alias ca0  -dname cn=ca0                         \
  -validity 10000 -keyalg RSA -keysize 2048                           \
  -ext BasicConstraints:critical=ca:true,pathlen:10000                \
  -keystore their-keystore.jks -keypass Keypass -storepass Storepass

keytool -genkeypair -alias ca1 -dname cn=ca1                          \
  -validity 10000 -keyalg RSA -keysize 2048                           \
  -keystore their-keystore.jks -keypass Keypass -storepass Storepass

keytool -genkeypair -alias ca2 -dname cn=ca2                          \
  -validity 10000 -keyalg RSA -keysize 2048                           \
  -keystore their-keystore.jks -keypass Keypass -storepass Storepass


  keytool -certreq -alias ca1                                            \
    -keystore their-keystore.jks -keypass Keypass -storepass Storepass   \
| keytool -gencert -alias ca0                                            \
    -ext KeyUsage:critical=keyCertSign                                   \
    -ext SubjectAlternativeName=dns:ca1                                  \
    -keystore their-keystore.jks -keypass Keypass -storepass Storepass   \
| keytool -importcert -alias ca1                                         \
    -keystore   their-keystore.jks -keypass Keypass -storepass Storepass

#echo "Debug exit" ; exit 0

  keytool -certreq -alias ca2                                           \
    -keystore their-keystore.jks -keypass Keypass -storepass Storepass  \
| keytool -gencert -alias ca1                                           \
    -ext KeyUsage:critical=keyCertSign                                  \
    -ext SubjectAlternativeName=dns:ca2                                 \
    -keystore their-keystore.jks -keypass Keypass -storepass Storepass  \
| keytool -importcert -alias ca2                                        \
    -keystore their-keystore.jks -keypass Keypass -storepass Storepass

keytool -list -v -storepass Storepass -keystore their-keystore.jks


echo  "===================================================================="
echo  "Fake third-party chain generated. Now generating my-keystore.jks ..."
echo  "===================================================================="
read -p "Press a key to continue."

# Import authority's certificate chain

  keytool -exportcert -alias ca0                                        \
    -keystore their-keystore.jks -keypass Keypass -storepass Storepass  \
| keytool -importcert -trustcacerts -noprompt -alias ca0                \
    -keystore  my-keystore.jks -keypass Keypass -storepass Storepass

  keytool -exportcert -alias ca1                                        \
    -keystore their-keystore.jks -keypass Keypass -storepass Storepass  \
| keytool -importcert -noprompt -alias ca1                              \
    -keystore  my-keystore.jks -keypass Keypass -storepass Storepass

  keytool -exportcert -alias ca2                                        \
    -keystore their-keystore.jks -keypass Keypass -storepass Storepass  \
| keytool -importcert -noprompt -alias ca2                              \
    -keystore  my-keystore.jks -keypass Keypass -storepass Storepass

# Create our own certificate, the authority signs it.

keytool -genkeypair -alias e1  -dname cn=e1                        \
  -validity 10000 -keyalg RSA -keysize 2048                        \
  -keystore my-keystore.jks -keypass Keypass -storepass Storepass

keytool -certreq -alias e1                                              \
    -keystore my-keystore.jks -keypass Keypass -storepass Storepass     \
| keytool -gencert -alias ca2                                           \
    -ext SubjectAlternativeName=dns:localhost,ip:127.0.0.1              \
    -ext KeyUsage:critical=keyEncipherment,digitalSignature             \
    -ext ExtendedKeyUsage=serverAuth,clientAuth                         \
    -keystore their-keystore.jks -keypass Keypass -storepass Storepass  \
| keytool -importcert -alias e1                                         \
    -keystore my-keystore.jks -keypass Keypass -storepass Storepass

keytool -list -v  -storepass Storepass -keystore  my-keystore.jks

echo "================================================="
echo "Keystore generated. Now generating truststore ..."
echo "================================================="
read -p "Press a key to continue."

keytool -exportcert -alias ca0                                         \
    -keystore my-keystore.jks -keypass Keypass -storepass Storepass    \
| keytool -importcert -trustcacerts -noprompt -alias ca                \
    -keystore my-truststore.jks -keypass Keypass -storepass Storepass

  keytool -exportcert -alias ca1                                       \
    -keystore my-keystore.jks -keypass Keypass -storepass Storepass    \
| keytool -importcert -noprompt -alias ca1                             \
    -keystore my-truststore.jks -keypass Keypass -storepass Storepass

  keytool -exportcert -alias ca2                                       \
    -keystore my-keystore.jks -keypass Keypass -storepass Storepass    \
| keytool -importcert -noprompt -alias ca2                             \
    -keystore my-truststore.jks -keypass Keypass -storepass Storepass

  keytool -exportcert -alias e1                                        \
    -keystore my-keystore.jks -keypass Keypass -storepass Storepass    \
| keytool -importcert -noprompt -alias e1                              \
    -keystore my-truststore.jks -keypass Keypass -storepass Storepass

keytool -list -v  -storepass Storepass -keystore  my-truststore.jks

rm  their-keystore.jks 2> /dev/null


echo "=========================================================="
echo "Creating additional certificate caxx to add some noise ..."
echo "=========================================================="

keytool -genkeypair -alias caxx  -dname cn=caxx                       \
  -validity 10000 -keyalg RSA -keysize 2048                           \
  -keystore my-keystore.jks -keypass Keypass -storepass Storepass

