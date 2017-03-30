#!/bin/bash
#
# Build Olympus after updating version string and deploy

#function local_wait() {
#  local cmd="$@"
#  local log_file=travis_wait_$$.log
#  $cmd &
#  while true; do
#    ps -p$! 2>&1 >/dev/null
#    if [ $? = 0 ]; then
#      echo "still running: $cmd"; sleep 15
#    else
#      echo "$cmd finished"
#      cat $log_file
#      break
#    fi
#  done
#}

wait_with_output() {
  local cmd="$@"
  local log_file=travis_wait_$$.log

  $cmd 2>&1 >$log_file &
  local cmd_pid=$!

  travis_jigger $! $cmd &
  local jigger_pid=$!
  local result

  { wait $cmd_pid 2>/dev/null; result=$?; ps -p$jigger_pid 2>&1>/dev/null && kill $jigger_pid; } || exit 1
  exit $result
}

travis_jigger() {
  local timeout=30 # in minutes
  local count=0

  local cmd_pid=$1
  shift

  while [ $count -lt $timeout ]; do
    count=$(($count + 1))
    echo -e "\033[0mStill running ($count of $timeout): $@"
    sleep 60
  done

  echo -e "\n\033[31;1mTimeout reached. Terminating $@\033[0m\n"
  kill -9 $cmd_pid
}

#local_wait cat #find / -name "*" > /dev/null 2>&1 > /dev/null 
