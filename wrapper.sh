#!/bin/bash

date
echo start wrapper script

cd "$(dirname "$0")"
# . env/bin/activate

while true;do
    date
    echo start kotlin bot
    #cp emotes-bot-kt/build/libs/emotes-bot-kt.jar ./
    java -jar vikbot-kt.jar
    if [ $? -ne 0 ] #return 4 means requested stop. Else restart is probably needed
    then
        break
    fi
done

date
echo script ended
