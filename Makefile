JAVA_HOME ?= /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
ANDROID_HOME ?= $(HOME)/Library/Android/sdk
export JAVA_HOME ANDROID_HOME

.PHONY: check build install run
check:
	./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
build:
	./gradlew :app:assembleDebug --console=plain
install: build
	@test -n "$(ANDROID_DEVICE)" || (echo 'Set ANDROID_DEVICE to an explicitly selected emulator or TV'; exit 1)
	$(ANDROID_HOME)/platform-tools/adb -s $(ANDROID_DEVICE) install -r app/build/outputs/apk/debug/app-debug.apk
run:
	@test -n "$(ANDROID_DEVICE)" || (echo 'Set ANDROID_DEVICE'; exit 1)
	$(ANDROID_HOME)/platform-tools/adb -s $(ANDROID_DEVICE) shell am start -n fr.bonamy.iptv/.MainActivity
