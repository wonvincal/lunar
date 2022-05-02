#!/usr/bin/env bash

# usage - write to stderr
usage() { echo "Usage: $0 [-c <absolute_path_to_conf>] [-s <system_name>] [-h hostname] [-p port] [-a aeronDir (default to ./aeron)] [-l <path to log4j2.xml>] [-d <replay date in yyyymmdd>] [-u debugPort] [-v speedArbVersion] [-j jmxPort ] [-m matchingEngineDelayNano]" 1>&2; exit 1; }

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/.. && pwd )"
BIN_DIR=$DIR/bin
LIB_DIR=$DIR/lib
LUNAR_HOSTNAME=`hostname --long`
LUNAR_AERONDIR="${DIR}/aeron"
while getopts ":c:s:h:p:l:d:e:u:j:v:m:" opt
do
    case $opt in
        c)
            LUNAR_CONFIG_PATH=${OPTARG}
            ;;
        s)
            LUNAR_SYSTEM=${OPTARG}
            ;;
        h)
            LUNAR_HOSTNAME=${OPTARG}
            ;;
        p)
            LUNAR_PORT=${OPTARG}
            ;;
        l)
            LUNAR_LOG_CONFIG_PATH=${OPTARG}
            ;;
        d)
            LUNAR_REPLAY_DATE=${OPTARG}
            ;;
        e)
            LUNAR_REPLAY_SPEED=${OPTARG}
            ;;
        u)
            LUNAR_DEBUG_PORT=${OPTARG}
            ;;
        j)
            LUNAR_JMX_PORT=${OPTARG}
            ;;
        v)
            LUNAR_SPEEDARB_VERSION=${OPTARG}
            ;;
        m)
            LUNAR_MATCHING_ENGINE_DELAY_NANO=${OPTARG}
            ;;
        \?)
            echo "invalid ${OPTARG}"
            usage
            ;;
    esac
done
shift $((OPTIND-1))

if [ -z "${LUNAR_CONFIG_PATH}" ] || [ -z "${LUNAR_SYSTEM}" ] || [ -z "${LUNAR_HOSTNAME}" ] || [ -z "${LUNAR_PORT}" ] || [ -z "${LUNAR_JMX_PORT}" ] || [ -z "${LUNAR_AERONDIR}" ]; then
    usage
fi

if [ ! -f "${LUNAR_CONFIG_PATH}" ]; then
    echo "Configuration file not found at ${LUNAR_CONFIG_PATH}"
    exit 1;
fi

LUNAR_LOG_CONFIG_PROP=""
if [ -n "${LUNAR_LOG_CONFIG_PATH}" ]; then
    if [ ! -f "${LUNAR_LOG_CONFIG_PATH}" ]; then
        echo "Log configuration file not found at ${LUNAR_LOG_CONFIG_PATH}"
        exit 1;
    fi
    LUNAR_LOG_CONFIG_PROP="-Dlog4j.configurationFile=${LUNAR_LOG_CONFIG_PATH}"
fi

LUNAR_REPLAY_DATE_PROP=""
if [ -n "${LUNAR_REPLAY_DATE}" ]; then
    LUNAR_REPLAY_DATE_PROP="-Dreplay.date=${LUNAR_REPLAY_DATE}"
fi

LUNAR_REPLAY_SPEED_PROP=""
if [ -n "${LUNAR_REPLAY_SPEED}" ]; then
    LUNAR_REPLAY_SPEED_PROP="-Dreplay.speed=${LUNAR_REPLAY_SPEED}"
fi

LUNAR_SPEEDARB_VERSION_PROP=""
if [ -n "${LUNAR_SPEEDARB_VERSION}" ]; then
    LUNAR_SPEEDARB_VERSION_PROP="-Dspeedarb.version=${LUNAR_SPEEDARB_VERSION}"
fi

LUNAR_DEBUG_PORT_PROP=""
if [ -n "${LUNAR_DEBUG_PORT}" ]; then
    LUNAR_DEBUG_PORT_PROP="-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=${LUNAR_DEBUG_PORT}"
fi

LUNAR_MATCHING_ENGINE_DELAY_NANO_PROP=""
if [ -n "${LUNAR_MATCHING_ENGINE_DELAY_NANO}" ]; then
    LUNAR_MATCHING_ENGINE_DELAY_NANO_PROP="-Dlunar.matching.engine.delay.ns=${LUNAR_MATCHING_ENGINE_DELAY_NANO}"
else
    LUNAR_MATCHING_ENGINE_DELAY_NANO_PROP="-Dlunar.matching.engine.delay.ns=2000000"
fi

# export env variable
export JAVA_HOME=/coda/jdk1.8.0_77
export LD_PRELOAD=/coda/vendor/capfutures/lib/libtcmalloc_minimal.so.4
export LD_LIBRARY_PATH=/coda/vendor/capfutures/lib:/coda/QuantLib-1.5/lib

export LUNAR_OPTS="-Djava.library.path='$LIB_DIR' -Dconfig.file=$LUNAR_CONFIG_PATH -Dlunar.system=$LUNAR_SYSTEM -Dlunar.host=$LUNAR_HOSTNAME -Dlunar.port=$LUNAR_PORT -Dlunar.jmxPort=$LUNAR_JMX_PORT -Dlunar.aeronDir=$LUNAR_AERONDIR $LUNAR_LOG_CONFIG_PROP $LUNAR_SPEEDARB_VERSION_PROP $LUNAR_REPLAY_DATE_PROP $LUNAR_REPLAY_SPEED_PROP $LUNAR_DEBUG_PORT_PROP $LUNAR_MATCHING_ENGINE_DELAY_NANO_PROP -DLog4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector -Dlog4j2.enable.threadlocals=true -Dlog4j2.enable.direct.encoders=true -DAsyncLogger.WaitStrategy=busyspin -Dlog4j2.messageFactory=org.apache.logging.log4j.message.ReusableMessageFactory -Dagrona.disable.bounds.checks=true -Dsun.rmi.dgc.server.gcInterval=86400000 -Dsun.rmi.dgc.client.gcInterval=86400000"
export JAVA_OPTS="-XX:+UnlockDiagnosticVMOptions -Xmx6G -Xms6G -XX:MaxNewSize=384M -XX:NewSize=384M -XX:MaxDirectMemorySize=4096M -XX:MaxTenuringThreshold=2 -XX:SurvivorRatio=4 -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=80 -XX:+ExplicitGCInvokesConcurrent -XX:LogFile=/dev/shm/lunar.jvm.$$.log -verbose:gc -XX:+PrintGCApplicationStoppedTime -XX:PrintSafepointStatisticsCount=1 -XX:+PrintSafepointStatistics -XX:GuaranteedSafepointInterval=0 -XX:+LogVMOutput -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintTenuringDistribution -XX:+PrintGCCause -XX:-UseBiasedLocking -XX:ParallelGCThreads=10 -XX:+PerfDisableSharedMem -XX:CICompilerCount=6"
# call bin/lunar
printf "\nStart Lunar System\n"
echo "- config file: ${LUNAR_CONFIG_PATH}"
echo "- system: ${LUNAR_SYSTEM}"
echo "- host: ${LUNAR_HOSTNAME}"
echo "- port: ${LUNAR_PORT}"
echo "- jmx port: ${LUNAR_JMX_PORT}"
echo "- replay date: ${LUNAR_REPLAY_DATE}"
echo "- replay speed: ${LUNAR_REPLAY_SPEED}"
echo "- aero directory: ${LUNAR_AERONDIR}"
if [ -n "${LUNAR_LOG_CONFIG_PATH}" ]; then
    echo "- log4j2.xml: ${LUNAR_LOG_CONFIG_PATH}"
else
    echo "- log4j2.xml: not specified, will use any one found in classpath"
fi
if [ -n "${LUNAR_DEBUG_PORT_PROP}" ]; then
    echo "- debug settings: ${LUNAR_DEBUG_PORT_PROP}"
fi

$BIN_DIR/lunar
