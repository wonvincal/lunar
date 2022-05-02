#!/bin/sh

VENDOR_PATH=${CAPFUTURES_PATH:=/coda/vendor/capfutures.1.4.3}
echo "Compiling ctpmdulistener using vendor libraries in $VENDOR_PATH"

g++ -DEMBEDDED_MDU -g -std=c++0x -fPIC -O3 -I $VENDOR_PATH/include/ -o ctpmdulistener ctpmdulistener.cpp $VENDOR_PATH/lib/libEI6.so  -ldl -lpthread -Wl,-rpath .

echo "Done!"

