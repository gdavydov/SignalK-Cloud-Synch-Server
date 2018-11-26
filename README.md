SignalK cloud sych server
==============

This is the partially based on signalk-aretmis server.

The artemis server leverages Java8+ async, lambdas, streams, and other new features of Java, resulting in a simpler implementation, better suited to signalk.

It also uses a time-series database as the core storage for all data. This means all data is persistent, and it can recover the vessels entire signalk data at any point backwards through time. The intention is to provide a platform to implement a signalk history api that allows analysis over time, and against historic data.

Its a drop in replacement for the signalk-java-server, missing functionality is a bug.

It assumes influx db is running on localhost:8086

REST API docs
-------------

Swagger REST API docs are now available at http://localhost:8080/docs/


Installation
============

The artemis server is normally installed as the server part of signalk-java (https://github.com/SignalK/signalk-java) project, which includes the supporting web interface and misc config UI etc.

The default signalk-java installs the old java server, to get this new version see signalk-java README

Development
===========

Clone this project and signalk-java from github in the normal way. The artemis project uses maven to build, if you use an IDE like eclipse, netbeans, or intelliJ it should build automatically. 

TODO: more needed here about local builds for dev...


NMEA
====

Artemis server uses the signalk-parser-nmea0183 project modified to run under java8 nashorn, and to be useable directly from the java jar file without the full npm install process. This means the src/main/resources/dist/bundle.js file is commited to git, but as its not expected to change often, and greatly simplifies deployment that disadvantage is accepted for now.

To merge future changes from the signalk-parser-nmea0183 project, clone the signalk-parser-nmea0183 project separately, and run the following to create an es5 transpiled version:

```
  rm -rf node_modules/
  npm install
  npm install -D babel-cli
  npm install -D babel-preset-es2015
  nano ./package.json 
  	add tasks:
  		"build-es5-hooks": "babel hooks --out-dir hooks-es5",
    	"build-es5-lib": "babel lib --out-dir lib-es5",
  npm run build-es5-hooks
  npm run build-es5-lib
  
```
  The hooks-es5 dir and the some parts of the lib-es5 dir were then copied to artemis-server/src/main/resources/signalk-parser-nmea0183/ and modified to suit. future changes to signalk-parser-nmea0183 will have to be copied over as required, and the webpack re-run to create the dist/bundle.js file.
  
  
