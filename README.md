# ppd42ns-driver
ppd42ns-driver is a java library that operates dust sensor called [PPD42NS](http://wiki.seeedstudio.com/Grove-Dust_Sensor/) to connect PPD42NS to GPIO terminal of Raspberry Pi 3B and make it for use in java.
I releases this in the form of the Eclipse plug-in project.
You need Java 8 or higher.

I use [Pi4J](https://pi4j.com/)
for gpio communication in java and have confirmed that it works in Raspberry Pi 3B ([Raspbian Buster Lite OS](https://www.raspberrypi.org/downloads/raspbian/) (2019-07-10)).

## Connection of PPD42NS and Raspberry Pi 3B
**Connect with `Vin <--> Vin`,`GND <--> GND`, `PM2.5 <--> Tx`.**
[This](https://github.com/mauricecyril/pidustsensor) is also helpful.
- `Pins` of [PPD42NS](http://wiki.seeedstudio.com/Grove-Dust_Sensor/)
  - Vin (pin#3 - Red)
  - GND (pin#1 - Black)
  - PM2.5 (pin#4 - Yellow)
- [GPIO of Raspberry Pi 3B](https://www.raspberrypi.org/documentation/usage/gpio/README.md)
  - Vin --> (2) or (4)
  - GND --> (6), (9), (14), (20), (25), (30), (34) or (39)
  - Tx --> (19) GPIO10, (38) GPIO20 or (8) GPIO14

## Install Raspbian Buster Lite OS (2019-07-10)
The reason for using this version is that it is the latest as of July 2019 and [BlueZ](http://www.bluez.org/) 5.50 is included from the beginning, and use Bluetooth and serial communication simultaneously.

## Configuration of Raspbian Buster Lite OS
- Edit `/boot/cmdline.txt`
```
console=serial0,115200 --> removed
```
- Edit `/boot/config.txt`
```
@@ -45,7 +45,7 @@
 # Uncomment some or all of these to enable the optional hardware interfaces
 #dtparam=i2c_arm=on
 #dtparam=i2s=on
-#dtparam=spi=on
+dtparam=spi=on
 
 # Uncomment this to enable the lirc-rpi module
 #dtoverlay=lirc-rpi
@@ -55,6 +55,10 @@
 # Enable audio (loads snd_bcm2835)
 dtparam=audio=on
 
+enable_uart=1
+dtoverlay=pi3-miniuart-bt
+core_freq=250
+
 [pi4]
 # Enable DRM VC4 V3D driver on top of the dispmanx display stack
 dtoverlay=vc4-fkms-v3d
```
When editing is complete, reboot.

## Install WiringPi Native Library
Pi4J depends on the [WiringPi](http://wiringpi.com/) native library by Gordon Henderson.
The Pi4J native library is dynamically linked to WiringPi.
```
# apt-get update
# apt-get install wiringpi
```
When using with Raspberry Pi 4B, install the latest version as follows.
Please refer to [here](http://wiringpi.com/wiringpi-updated-to-2-52-for-the-raspberry-pi-4b/).
```
# wget https://project-downloads.drogon.net/wiringpi-latest.deb
# dpkg -i wiringpi-latest.deb
```
Please make sure it’s version 2.52.
```
# gpio -v
gpio version: 2.52
```
**Note. In October 2019, I have not confirmed the official information that can use Raspberry Pi 4B with Pi4J and WiringPi.
I've simply checked that it works, but some problems may occur.**

## Install jdk8 on Raspberry Pi 3B
For example, the installation of OpenJDK 8 is shown below.
```
# apt-get update
# apt-get install openjdk-8-jdk
```

## Install git
If git is not included, please install it.
```
# apt-get install git
```

## Use this with the following bundles
- [SLF4J 1.7.26](https://www.slf4j.org/)
- [Pi4J 1.2 (pi4j-core.jar)](https://github.com/s5uishida/pi4j-core-osgi)

I would like to thank the authors of these very useful codes, and all the contributors.

## How to use
The following sample code will be helpful.
**In the following code, ppd42ns.read() takes about 30 seconds to measure.**
```java
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.RaspiPin;

import io.github.s5uishida.iot.device.ppd42ns.driver.PPD42NSDriver;
import io.github.s5uishida.iot.device.ppd42ns.driver.PPD42NSObservationData;

public class MyPPD42NS {
    private static final Logger LOG = LoggerFactory.getLogger(MyPPD42NS.class);
    
    public static void main(String[] args) {
        PPD42NSDriver ppd42ns = PPD42NSDriver.getInstance(RaspiPin.GPIO_10);
        ppd42ns.open();
    
        while (true) {
            try {
                PPD42NSObservationData data = ppd42ns.read();
                LOG.info("[{}] {}", ppd42ns.getName(), data.toString());
            } catch (IOException e) {
                LOG.warn("caught - {}", e.toString());
            }
        }
    
//      if (ppd42ns != null) {
//          ppd42ns.close();
//      }
    }
}
```
