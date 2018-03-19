OpenSeizureDetector - Pebble Version
====================================

This seizure detector uses a (Pebble)[http://getpebble.com] smart watch or Android Wear watch.The watch has an accelerometer and vibrator motor and a bluetooth radio
to talk to another computer.  

Principle of Operation
======================
It is based on an accelerometer monitoring movement.  It uses a fourier
transform to extract the frequency spectrum of the movement, and monitors
movements in a given frequency band.   The idea is that it will detect the
rhythmic movements associated with a seizure, but not normal day to day
activities.

If the acceleration within the given frequency band is more than a
threshod value, it starts a timer.  If the acceleration remains above
the threshold for a given period, it issues a warning beep.
If it remains above the threshold for a longer specified period, the unit
alarms (continuous tone rather than beep).

Likewise, a heart rate detection routine has been added for watches which have these sensors.

Licence
=======
My code is licenced under the GNU Public Licence - for associated libraries 
please see Credits below.

Credits
=======
The following libraries are used:
* (SYLT-FFT)[https://github.com/stg/SYLT-FFT] by D. Taylor.
* (NanoHTTPD)[https://github.com/NanoHttpd/nanohttpd]
* (jQuery)[http://jquery.org]
* (jBeep)[http://www.ultraduz.com.br]
* (Chartjs)[http://www.chartjs.org]
* (MPAndroidChart)[https://github.com/PhilJay/MPAndroidChart]

Logo based on ["Star of life2" by Verdy p - Own work. Licensed under Public Domain via Wikimedia Commons](http://commons.wikimedia.org/wiki/File:Star_of_life2.svg#mediaviewer/File:Star_of_life2.svg).

Other icons crated using http://romannurik.github.io/AndroidAssetStudio.



Graham Jones, 01 March 2015.  (grahamjones139+sd@gmail.com)
