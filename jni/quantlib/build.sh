#!/bin/sh

JDK_PATH=${JAVA_HOME:=/coda/jdk1.8.0_77}
QL_PATH=/coda/QuantLib-1.5
echo "Compiling quantlib"

g++ -o libquantlib.so quantlib.cpp -I $QL_PATH/include/ -I $JDK_PATH/include/ -I $JDK_PATH/include/linux/ -L $QL_PATH/lib/ -lQuantLib -shared -fPIC

echo "Done!"
