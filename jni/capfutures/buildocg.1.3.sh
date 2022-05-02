#!/bin/sh

SBE_PATH=/coda/simple-binary-encoding-1.1.6-RC2
JDK_PATH=${JAVA_HOME:=/coda/jdk1.8.0_77}
VENDOR_PATH=${CAPFUTURES_PATH:=/coda/vendor/capfutures}
echo "Compiling ctpocgapi using vendor libraries in $VENDOR_PATH"

g++ -g -std=c++0x -fPIC -O3 -I $VENDOR_PATH/include/ -I $JDK_PATH/include/ -I $JDK_PATH/include/linux/ -o libctpocgapi.so -shared ctpocgapi.cpp $VENDOR_PATH/lib/libEI6.so -ldl -lpthread -lrt

echo "Done!"
