FlightPlot
==========

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/DrTon/FlightPlot?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Build Status](http://sitl01.dronetest.io/buildStatus/icon?job=PX4+FlightPlot)](http://sitl01.dronetest.io/job/PX4 FlightPlot/)

Universal flight log plotter

Docs and releases can be found on [pixhawk.org/dev/flightplot](http://pixhawk.org/dev/flightplot).

Development
-----------

IntelliJ IDEA IDE was used to develop FlightPlot, project files already exist in repo.

### Supported formats:
 - PX4 log (.px4log, .bin)
 - APM log (.bin)
 - ULog (.ulg)

### Features:
 - Data processing: low pass filtering, scaling, shifting, derivative, integral, etc.
 - Track export in KML and GPS format
 - Saving plot as image

Binaries for Linux, Mac OS, Windows can be found on [project homepage](https://pixhawk.org/dev/flightplot#download).

Building from source
--------------------

Requirements:
 -  Java 6 or newer (JDK, http://www.oracle.com/technetwork/java/javase/downloads/index.html)
 -  ant

Clone the repository. The `--recursive` flag is required to pull in the [jMAVlib](https://github.com/PX4/jMAVlib) submodule).
```
git clone --recursive https://github.com/PX4/FlightPlot.git
```

Build:
```
cd FlightPlot
ant
```

If you want to create deb file for ubuntu, use gen_deb.
```
cd FlightPlot
ant gen_deb
sudo dpkg -i out/production/FlightPlot.deb
```

Run:
```
java -jar out/production/flightplot.jar
```

