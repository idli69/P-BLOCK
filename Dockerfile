FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y openjdk-17-jdk wget unzip git curl && rm -rf /var/lib/apt/lists/*

ENV ANDROID_SDK_ROOT=/opt/android-sdk
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip -O cmdline-tools.zip \
    && unzip -q cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools \
    && rm cmdline-tools.zip \
    && mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest

ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

RUN yes | sdkmanager --licenses > /dev/null
RUN sdkmanager "platforms;android-34" "build-tools;34.0.0"

RUN wget -q https://services.gradle.org/distributions/gradle-8.7-bin.zip -O gradle.zip \
    && unzip -q gradle.zip -d /opt \
    && ln -s /opt/gradle-8.7/bin/gradle /usr/bin/gradle \
    && rm gradle.zip

WORKDIR /workspace
CMD ["bash", "-c", "gradle assembleDebug --no-daemon"]
