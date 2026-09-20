# OpenCV 4.11.0 carries no consumer keep rules. Its JNI layer resolves Java
# classes and members by name, including Mat, Core, Imgproc and Android loaders.
-keep class org.opencv.** { *; }
