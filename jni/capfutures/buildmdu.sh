#!/bin/sh

SBE_PATH=/coda/simple-binary-encoding-1.1.6-RC2
JDK_PATH=${JAVA_HOME:=/coda/jdk1.8.0_77}
VENDOR_PATH=${CAPFUTURES_PATH:=/coda/vendor/capfutures.1.4.0}
echo "Compiling ctpmduapi using vendor libraries in $VENDOR_PATH"

g++ -DEMBEDDED_MDU -g -std=c++0x -fPIC -Ofast -funroll-loops -I $SBE_PATH/main/cpp -I $VENDOR_PATH/include/ -I $JDK_PATH/include/ -I $JDK_PATH/include/linux/ -I ../../../lunar/target/generated-sources/cpp -o libctpmduapi.so -shared ctpmduapi.cpp $VENDOR_PATH/lib/libEI6.so  -ldl -lpthread -lrt

echo "Done!"
