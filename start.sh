#!/bin/bash

cd "$(dirname "$0")"

flock -n /var/lock/vikbot ./wrapper.sh >> ./log 2>&1
