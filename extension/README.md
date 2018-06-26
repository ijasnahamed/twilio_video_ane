--- Steps to generate ane library ---

#1 Copy wrapper swc file to extension

cp ../wrapper/TwilioVideo.swc ./

#2 Copy wrapper library swf file to android extension

cp ../wrapper/library.swf android/

#3 Copy android library jar to android extension

cp ../androidLib/build/classes/artifacts/androidLib_jar/androidLib.jar android/Android.jar

#4 Now generate ane by combining wrapper with android library

adt -package -target ane TwilioVideo.ane extension.xml -swc TwilioVideo.swc -platform Android-ARM -C android .
