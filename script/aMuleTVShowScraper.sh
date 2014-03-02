#!/bin/bash

get_new_links() {
        link_count=0
        oldIFS=$IFS
        IFS=$'\n'
        for link in $(cat "$TMPFILE"); do
                IFS=$oldIFS
                if ! [ -e "$OUTPUT_FILE" ] || ! grep -q -F "$link" "$OUTPUT_FILE"; then
                        echo "$link" >> "$OUTPUT_FILE"
                        let link_count=link_count+1
                fi
        done
        IFS=$oldIFS
        echo $link_count
}

show_usage() {
        echo "Usage: $0 [OPTIONS] <OUTPUT_FILE>" >&2
        echo "" >&2
        echo "Valid OPTIONS" >&2
        echo "  -l LIMIT       Limits search to LIMIT episodes per show" >&2
        echo "  -i INPUT_FILE  File containing missing episodes CSV (default STDIN)" >&2
        echo "  -j JAR_PATH    Path to aMuleTVShowScraper.jar file" >&2
        echo "  -s             Start aMule if not running">&2
        echo "  -p PASSWORD    aMule EC password">&2
        echo "  -P PORT        aMule EC port">&2
}


AMULE_PORT=4712
AMULE_PWD=""

JAR_PATH="."

[ -e /etc/aMuleTVShowScraper ] && source /etc/aMuleTVShowScraper
[ -e ~/.aMuleTVShowScraperrc ] && source ~/.aMuleTVShowScraperrc

INPUT_FILE="-"
PER_SHOW_LIMIT=""
START_AMULE=0

while [ $# -gt 0 ]; do
        case "$1" in
                -i) shift ; INPUT_FILE="$1" ;;
                -l) shift ; PER_SHOW_LIMIT="$1" ;;
                -j) shift ; JAR_PATH="$1" ;;
                -s) START_AMULE=1 ;;
                -p) shift; AMULE_PWD="$1" ;;
                -P) shift; AMULE_PORT="$1" ;;
                -*)
                    echo "Unknown option $1" >&2
                        show_usage
                    exit 1
                    ;;
                *)  break;;     # terminate while loop
        esac
        shift
done

if [ $# -eq 0 ]; then
        echo "OUTPUT_FILE not provided"
        show_usage
        exit 1;
fi

OUTPUT_FILE="$1"

last_show="xxx"
show_counter=0
TMPFILE=$(mktemp)
amule_started=0

if ! ps -efl | grep -v grep | grep -q amuled; then
        echo "Amule not running." >&2
        if [ $START_AMULE -gt 0 ] ; then
                if ! /usr/sbin/invoke-rc.d amule-daemon start; then
                        echo "Failed!" >&2
                        exit 1
                fi
                amule_started=1
                sleep 30
        else
                exit 0
        fi
fi

oldIFS=$IFS
IFS=$'\n'
for episode in $(cat "$INPUT_FILE"); do
        IFS=$oldIFS
        title=$(echo "$episode" | cut -d , -f 1| sed 's/"//g')
        alternateTitle=$(echo "$episode" | cut -d , -f 2 | sed 's/"//g')
        lang=$(echo "$episode" | cut -d , -f 3)
        nativeLang=$(echo "$episode" | cut -d , -f 4)
        res=$(echo "$episode" | cut -d , -f 5)
        season=$(echo "$episode" | cut -d , -f 6)
        ep=$(echo "$episode" | cut -d , -f 7)

        if [ "$title" == "$last_show" ]; then
                let show_counter=$show_counter+1
                if ! [ -z "$PER_SHOW_LIMIT" ] && [ $show_counter -ge "$PER_SHOW_LIMIT" ]; then
                        continue
                fi
        else
                let show_counter=0
        fi

        ADDITIONAL_OPT="-m 50000000 -M 2000000000 -p $AMULE_PORT"
        [ -z "$res" ] || [ "$res" == "any" ] || ADDITIONAL_OPT="$ADDITIONAL_OPT -r $res"
        [ -z "$lang" ] || [ -z "$nativeLang" ] || [ "$lang" == "$nativeLang" ] || ADDITIONAL_OPT="$ADDITIONAL_OPT -l $lang"

        java -jar "$JAR_PATH/aMuleTVShowScraper.jar" $ADDITIONAL_OPT -o "$TMPFILE" localhost "$AMULE_PWD" "$title" $season $ep
        links=$(get_new_links)
        if [ $links -eq 0 ] && ! [ -z "$alternateTitle" ]; then
                java -jar "$JAR_PATH/aMuleTVShowScraper.jar" $ADDITIONAL_OPT -o "$TMPFILE" localhost "$AMULE_PWD" "$alternateTitle" $season $ep
                get_new_links > /dev/null
        fi
        last_show="$title"
done

IFS=$oldIFS
rm "$TMPFILE"

if [ $amule_started -gt 0 ]; then
        /usr/sbin/invoke-rc.d amule-daemon stop
fi
