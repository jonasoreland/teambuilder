#!/bin/sh

exec java -Dfile.encoding=UTF-8 -cp build/libs/teambuilder-all.jar org.oreland.teambuilder.Main $*
