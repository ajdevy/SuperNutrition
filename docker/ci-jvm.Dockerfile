FROM eclipse-temurin:21-jdk-noble

RUN apt-get update \
    && apt-get install --no-install-recommends --yes \
        ca-certificates \
        curl \
        git \
        libgl1 \
        libx11-6 \
        python3 \
        unzip \
    && rm -rf /var/lib/apt/lists/*

ARG ANDROID_CMDLINE_TOOLS_VERSION=13114758

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH
ENV HOME=/tmp/container-home
ENV GRADLE_USER_HOME=/tmp/gradle-home

RUN mkdir -p "$ANDROID_HOME/cmdline-tools" "$HOME" "$GRADLE_USER_HOME" \
    && curl --fail --location --retry 3 --silent --show-error \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
        --output /tmp/android-command-line-tools.zip \
    && unzip -q /tmp/android-command-line-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-36" \
        "build-tools;36.0.0" \
    && rm -f /tmp/android-command-line-tools.zip \
    && chmod 1777 "$HOME" "$GRADLE_USER_HOME"

WORKDIR /workspace
