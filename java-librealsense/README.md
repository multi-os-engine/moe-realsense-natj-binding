# Java librealsense API

### librealsense
[GitHub repository](https://github.com/IntelRealSense/librealsense.git)

### Requirements
***OS X***: libusb installed via [Homebrew](http://brew.sh),
```sh
brew install libusb
```

***Linux***: Configurational requirements described in librealsense's documentation

### Testing Examples
***OS X***:
```sh
cd cpp-tutorial-1-depth
rm -rf ../build && rm -rf build && ../gradlew build
java -Djava.library.path=../deps/native/darwin:build -jar ./build/libs/cpp-tutorial-1-depth-1.0-SNAPSHOT.jar
```

***Linux (32-bit)***:
```sh
cd cpp-tutorial-1-depth
rm -rf ../build && rm -rf build && ../gradlew build
java -Djava.library.path=../deps/native/linux-i686:build -jar ./build/libs/cpp-tutorial-1-depth-1.0-SNAPSHOT.jar
```

***Linux (64-bit)***:
```sh
cd cpp-tutorial-1-depth
rm -rf ../build && rm -rf build && ../gradlew build
java -Djava.library.path=../deps/native/linux-x86_64:build -jar ./build/libs/cpp-tutorial-1-depth-1.0-SNAPSHOT.jar
```
