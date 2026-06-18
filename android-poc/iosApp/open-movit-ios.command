#!/bin/bash
# افتح مشروع iOS الصحيح (CocoaPods workspace) — لا تفتح iosApp.xcodeproj وحده.
cd "$(dirname "$0")"
open "$(pwd)/iosApp.xcworkspace"
