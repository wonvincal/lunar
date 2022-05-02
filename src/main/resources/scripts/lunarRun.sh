#!/usr/bin/env bash

# usage - write to stderr
usage() { echo "Usage $0 [-n env ] [-r <path to script>] [-i info ] [-x <exception email>] [-e <email>] [-s <subject>] [-l <log folder>]" 1>&2; exit 1; }
MAX_LINES_EMAIL=200
EXCEPTION_EMAIL="support@codacapitalpartners.com"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
while getopts "r:x:e:s:l:n:i:" opt
do
    case $opt in
        r)
            RUN_PATH=${OPTARG}
            ;;
        x)
            EXCEPTION_EMAIL=${OPTARG}
            ;;
        e)
            EMAIL=${OPTARG}
            ;;
        s)
            SUBJECT=${OPTARG}
            ;;
        l)
            LOG_FOLDER=${OPTARG}
            ;;
        i)
            INFO=${OPTARG}
            ;;
        n)
            RUN_ENV=${OPTARG}
            ;;
        \?)
            echo "invalid ${OPTARG}"
            usage
            ;;
    esac
done
shift $((OPTIND-1))

if [ -z "${RUN_ENV}" ] || [ -z "${RUN_PATH}" ] || [ -z "${SUBJECT}" ] || [ -z "${LOG_FOLDER}" ] || [ -z "${INFO}" ]; then
    usage
fi

if [ ! -d "${LOG_FOLDER}" ]; then
    echo "${LOG_FOLDER} does not exist"
    usage
fi

START=$(date +%s.%N)
#OUTPUT=$(eval ${RUN_PATH})
OUTPUT="$(${RUN_PATH} 2>&1)"
END=$(date +%s.%N)
DIFF=$(echo "$END - $START" | bc)

if [[ $DIFF =~ ^\. ]]; then
    DIFF="$(echo "${DIFF} * 1000000" | bc)"
    DIFF=`echo "${DIFF}" | sed 's/0//g'`
    DIFF="${DIFF} microsecond"
else
    DIFF="${DIFF} second"
fi

FILE_PREFIX="${INFO//[ @,.~]/_}"
FILE_INDEX=1
LOG_FILE=${FILE_PREFIX}.${FILE_INDEX}.log
while [ -f "${LOG_FILE}" ]
do
    ((FILE_INDEX+=1))
    LOG_FILE=${FILE_PREFIX}.${FILE_INDEX}.log
done

# write all outputs including stderr into the log file
# Ignore case
shopt -s nocasematch

SUMMARY="Command: $RUN_PATH"$'\n'
SUMMARY+="Env: $RUN_ENV"$'\n'
SUMMARY+="Log: $LOG_FOLDER/$LOG_FILE"$'\n'
SUMMARY+="Time taken: $DIFF"$'\n'
# Search for all possible error terms
if [[ ${OUTPUT} =~ ^.*(no such|error|warn|fail|alert).*$ ]]; then
    # Send alert email to support
    SUMMARY+="Failure search terms: no such|error|warn|fail|alert"$'\n'
    SUMMARY+="Result: FAILED"$'\n'

    # write summary to a temp file so that we can feed the temp file as body content of email"
    TEMP_OUT="$(mktemp)"
    echo "$SUMMARY" > $TEMP_OUT
    echo "" >> $TEMP_OUT
    echo "Output" >> $TEMP_OUT
    echo "======" >> $TEMP_OUT
    NUM_LINES=`wc -l <<< "${OUTPUT}"`
    if [ $NUM_LINES -gt $MAX_LINES_EMAIL ]; then
        head -n $MAX_LINES_EMAIL <<< "${OUTPUT}" >> $TEMP_OUT
        echo "...output exceeds maximum of $MAX_LINES_EMAIL lines, truncated..." >> $TEMP_OUT
    else    
        echo "$OUTPUT" >> $TEMP_OUT
    fi
    /usr/bin/mail -s "[FAILED] $SUBJECT" "$EXCEPTION_EMAIL" < $TEMP_OUT
else
    SUMMARY+="Result: SUCCESS"$'\n'
fi
echo "${SUMMARY}" > ${LOG_FILE}
echo "${OUTPUT}" >> ${LOG_FILE}
