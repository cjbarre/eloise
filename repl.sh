#!/bin/bash
cd ./clojure/$1
CLJ_CONFIG=../../versions clojure -A:defaults:nrepl:clj-kondo -m nrepl.cmdline