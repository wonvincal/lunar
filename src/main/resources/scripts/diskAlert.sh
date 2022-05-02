#!/bin/sh

usage() { echo "Usage $0 [-t threshold (default to 90%)]" 1>&2; exit 1; }
THRESHOLD=90
EXCLUDE_LIST=
while getopts "t:" opt
do
    case $opt in
        t)
            THRESHOLD=${OPTARG}
            ;;
        \?)
            echo "invalid ${OPTARG}"
            usage
            ;;
    esac
done
shift $((OPTIND-1))

if [ -z "${THRESHOLD}" ]; then
    usage
fi

processLine() {
while read output;
do
    echo $output
    usep=$(echo $output | awk '{ print $1}' | cut -d'%' -f1  )
    partition=$(echo $output | awk '{ print $2 }' )
    if [ $usep -ge $THRESHOLD ]; 
    then
        ALERT="${ALERT}Running out of space \"$partition ($usep%)\" on $(hostname) as on $(date)"'\n'
    fi
done

if [ ! -z "${ALERT}" ]; then
    echo ""
    echo "Alert! Running out of space"
    echo "$ALERT"
fi
}

if [ "$EXCLUDE_LIST" != "" ]; then
    df -H | grep -vE '^Filesystem|tmpfs|cgmfs|udev|none|${EXCLUDE_LIST}' | awk '{ print $5 " " $1 }' | processLine
else
    df -H | grep -vE '^Filesystem|tmpfs|cgmfs|udev|none' | awk '{ print $5 " " $1 }' | processLine
fi
