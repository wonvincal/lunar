#!/bin/sh

JDK_PATH=${JAVA_HOME:=/coda/jdk1.8.0_77}
echo "Compiling nativethreadinfo"

g++ -g -std=c++0x -fPIC -O3 -I $JDK_PATH/include/ -I $JDK_PATH/include/linux/ -o libnativethreadinfo.so -shared nativethreadinfo.cpp -ldl -lpthread -lrt

echo "Done!"
