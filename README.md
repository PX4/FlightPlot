
FlightPlot
==========

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/DrTon/FlightPlot?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Build Status](http://sitl01.dronetest.io/buildStatus/icon?job=PX4+FlightPlot)](http://sitl01.dronetest.io/job/PX4 FlightPlot/)

Universal flight log plotter

Installation
------------
Requirements:
 -  Java 6 or newer (JDK, http://www.oracle.com/technetwork/java/javase/downloads/index.html)
 -  ant (In ubuntu, you should install ant like (sudo apt-get install ant)


Clone repository:
```
https://github.com/DrTon/FlightPlot.git
```

Compile:
```
cd FlightPlot
ant
```

Run:
```
java -jar out/production/flightplot.jar
```

Developing
----------

For Developer:
 - If you want to modify flightplot, use IntelliJ IDEA tool

http://pixhawk.org/dev/flightplot

#### Supported formats:
 - PX4 log (.px4log, .bin)
 - APM log (.bin)
 - ULog (.ulg)
 
#### Features:
 - Data processing: low pass filtering, scaling, shifting, derivative, integral, etc.
 - Track export in KML and GPS format
 - Saving plot as image

Binaries for Linux, Mac OS, Windows can be found on [project homepage](https://pixhawk.org/dev/flightplot#download).

Building from sources
----------------------
Requirements:
 -  Java 6 or newer (JDK, http://www.oracle.com/technetwork/java/javase/downloads/index.html)
 -  ant

Clone repository (`--recursive` flag is required to pull in [jMAVlib](https://github.com/DrTon/jMAVlib)):
```
git clone --recursive https://github.com/DrTon/FlightPlot.git
```

Build:
```
cd FlightPlot
ant 
```

if you want to create deb file for ubuntu, use gen_deb.
```
cd FlightPlot
ant gen_deb
sudo dpkg -i out/production/FlightPlot.deb
```

Run:
```
java -jar out/production/flightplot.jar
```

Developing
----------

IntelliJ IDEA IDE was used to develop FlightPlot, project files already exist in repo.
