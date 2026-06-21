
Showing All Issues

Build target AppAuth of project Pods with configuration Debug

CompileC /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/OIDExternalUserAgentIOSCustomBrowser.o /Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOSCustomBrowser.m normal arm64 objective-c com.apple.compilers.llvm.clang.1_0.compiler (in target 'AppAuth' from project 'Pods')
    cd /Users/mood/POSE/kmp-app/iosApp/Pods
    
    Using response file: /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp
    
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -ivfsstatcache /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache -target arm64-apple-ios18.5-simulator -fmessage-length\=0 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit\=0 -fno-color-diagnostics -fmodules-prune-interval\=86400 -fmodules-prune-after\=345600 -fbuild-session-file\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation -fmodules-validate-once-per-build-session -Wnon-modular-include-in-framework-module -Werror\=non-modular-include-in-framework-module -Wno-trigraphs -Wno-missing-field-initializers -Wno-missing-prototypes -Werror\=return-type -Wdocumentation -Wunreachable-code -Wno-implicit-atomic-properties -Werror\=deprecated-objc-isa-usage -Wno-objc-interface-ivars -Werror\=objc-root-class -Wno-arc-repeated-use-of-weak -Wimplicit-retain-self -Wduplicate-method-match -Wno-missing-braces -Wparentheses -Wswitch -Wunused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wconditional-uninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wno-float-conversion -Wnon-literal-null-conversion -Wobjc-literal-conversion -Wshorten-64-to-32 -Wpointer-sign -Wno-newline-eof -Wno-selector -Wno-strict-selector-match -Wundeclared-selector -Wdeprecated-implementations -Wno-implicit-fallthrough -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -Wno-sign-conversion -Winfinite-recursion -Wcomma -Wblock-capture-autoreleasing -Wstrict-prototypes -Wno-semicolon-before-method-body -Wunguarded-availability -index-store-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Index.noindex/DataStore @/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp -include /Users/mood/POSE/kmp-app/iosApp/Pods/Target\ Support\ Files/AppAuth/AppAuth-prefix.pch -MMD -MT dependencies -MF /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/OIDExternalUserAgentIOSCustomBrowser.d --serialize-diagnostics /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/OIDExternalUserAgentIOSCustomBrowser.dia -c /Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOSCustomBrowser.m -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/OIDExternalUserAgentIOSCustomBrowser.o -index-unit-output-path /Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/OIDExternalUserAgentIOSCustomBrowser.o

/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOSCustomBrowser.m:151:44: warning: 'openURL:' is deprecated: first deprecated in iOS 10.0 [-Wdeprecated-declarations]
  151 |         [[UIApplication sharedApplication] openURL:_appStoreURL];
      |                                            ^~~~~~~
      |                                            openURL:options:completionHandler:
In module 'UIKit' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/Target Support Files/AppAuth/AppAuth-prefix.pch:2:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/UIKit.framework/Headers/UIApplication.h:97:1: note: 'openURL:' has been explicitly marked deprecated here
   97 | - (BOOL)openURL:(NSURL*)url API_DEPRECATED_WITH_REPLACEMENT("openURL:options:completionHandler:", ios(2.0, 10.0)) API_UNAVAILABLE(visionos, watchos);
      | ^
/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOSCustomBrowser.m:165:63: warning: 'openURL:' is deprecated: first deprecated in iOS 10.0 [-Wdeprecated-declarations]
  165 |     BOOL openedInBrowser = [[UIApplication sharedApplication] openURL:requestURL];
      |                                                               ^~~~~~~
      |                                                               openURL:options:completionHandler:
In module 'UIKit' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/Target Support Files/AppAuth/AppAuth-prefix.pch:2:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/UIKit.framework/Headers/UIApplication.h:97:1: note: 'openURL:' has been explicitly marked deprecated here
   97 | - (BOOL)openURL:(NSURL*)url API_DEPRECATED_WITH_REPLACEMENT("openURL:options:completionHandler:", ios(2.0, 10.0)) API_UNAVAILABLE(visionos, watchos);
      | ^
2 warnings generated.

/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOSCustomBrowser.m:151:44: 'openURL:' is deprecated: first deprecated in iOS 10.0

/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOSCustomBrowser.m:165:63: 'openURL:' is deprecated: first deprecated in iOS 10.0

CompileC /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/OIDExternalUserAgentIOS.o /Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m normal arm64 objective-c com.apple.compilers.llvm.clang.1_0.compiler (in target 'AppAuth' from project 'Pods')
    cd /Users/mood/POSE/kmp-app/iosApp/Pods
    
    Using response file: /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp
    
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -ivfsstatcache /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache -target arm64-apple-ios18.5-simulator -fmessage-length\=0 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit\=0 -fno-color-diagnostics -fmodules-prune-interval\=86400 -fmodules-prune-after\=345600 -fbuild-session-file\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation -fmodules-validate-once-per-build-session -Wnon-modular-include-in-framework-module -Werror\=non-modular-include-in-framework-module -Wno-trigraphs -Wno-missing-field-initializers -Wno-missing-prototypes -Werror\=return-type -Wdocumentation -Wunreachable-code -Wno-implicit-atomic-properties -Werror\=deprecated-objc-isa-usage -Wno-objc-interface-ivars -Werror\=objc-root-class -Wno-arc-repeated-use-of-weak -Wimplicit-retain-self -Wduplicate-method-match -Wno-missing-braces -Wparentheses -Wswitch -Wunused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wconditional-uninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wno-float-conversion -Wnon-literal-null-conversion -Wobjc-literal-conversion -Wshorten-64-to-32 -Wpointer-sign -Wno-newline-eof -Wno-selector -Wno-strict-selector-match -Wundeclared-selector -Wdeprecated-implementations -Wno-implicit-fallthrough -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -Wno-sign-conversion -Winfinite-recursion -Wcomma -Wblock-capture-autoreleasing -Wstrict-prototypes -Wno-semicolon-before-method-body -Wunguarded-availability -index-store-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Index.noindex/DataStore @/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp -include /Users/mood/POSE/kmp-app/iosApp/Pods/Target\ Support\ Files/AppAuth/AppAuth-prefix.pch -MMD -MT dependencies -MF /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/OIDExternalUserAgentIOS.d --serialize-diagnostics /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/OIDExternalUserAgentIOS.dia -c /Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/OIDExternalUserAgentIOS.o -index-unit-output-path /Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/OIDExternalUserAgentIOS.o

/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:53:3: warning: 'SFAuthenticationSession' is deprecated: first deprecated in iOS 12.0 [-Wdeprecated-declarations]
   53 |   SFAuthenticationSession *_authenticationVC;
      |   ^~~~~~~~~~~~~~~~~~~~~~~
      |   ASWebAuthenticationSession
In module 'SafariServices' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:25:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/SafariServices.framework/Headers/SFAuthenticationSession.h:63:12: note: 'SFAuthenticationSession' has been explicitly marked deprecated here
   63 | @interface SFAuthenticationSession : NSObject
      |            ^
/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:143:7: warning: 'SFAuthenticationSession' is deprecated: first deprecated in iOS 12.0 [-Wdeprecated-declarations]
  143 |       SFAuthenticationSession *authenticationVC =
      |       ^~~~~~~~~~~~~~~~~~~~~~~
      |       ASWebAuthenticationSession
In module 'SafariServices' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:25:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/SafariServices.framework/Headers/SFAuthenticationSession.h:63:12: note: 'SFAuthenticationSession' has been explicitly marked deprecated here
   63 | @interface SFAuthenticationSession : NSObject
      |            ^
/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:144:37: warning: 'SFAuthenticationSession' is deprecated: first deprecated in iOS 12.0 [-Wdeprecated-declarations]
  144 |           [[SFAuthenticationSession alloc] initWithURL:requestURL
      |                                     ^~~~~
      |                                     ASWebAuthenticationSession
In module 'SafariServices' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:25:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/SafariServices.framework/Headers/SFAuthenticationSession.h:63:12: note: 'SFAuthenticationSession' has been explicitly marked deprecated here
   63 | @interface SFAuthenticationSession : NSObject
      |            ^
/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:180:58: warning: 'openURL:' is deprecated: first deprecated in iOS 10.0 [-Wdeprecated-declarations]
  180 |     openedUserAgent = [[UIApplication sharedApplication] openURL:requestURL];
      |                                                          ^~~~~~~
      |                                                          openURL:options:completionHandler:
In module 'UIKit' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/Target Support Files/AppAuth/AppAuth-prefix.pch:2:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/UIKit.framework/Headers/UIApplication.h:97:1: note: 'openURL:' has been explicitly marked deprecated here
   97 | - (BOOL)openURL:(NSURL*)url API_DEPRECATED_WITH_REPLACEMENT("openURL:options:completionHandler:", ios(2.0, 10.0)) API_UNAVAILABLE(visionos, watchos);
      | ^
/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:203:3: warning: 'SFAuthenticationSession' is deprecated: first deprecated in iOS 12.0 [-Wdeprecated-declarations]
  203 |   SFAuthenticationSession *authenticationVC = _authenticationVC;
      |   ^~~~~~~~~~~~~~~~~~~~~~~
      |   ASWebAuthenticationSession
In module 'SafariServices' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:25:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/SafariServices.framework/Headers/SFAuthenticationSession.h:63:12: note: 'SFAuthenticationSession' has been explicitly marked deprecated here
   63 | @interface SFAuthenticationSession : NSObject
      |            ^
5 warnings generated.

/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:53:3: 'SFAuthenticationSession' is deprecated: first deprecated in iOS 12.0

/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:143:7: 'SFAuthenticationSession' is deprecated: first deprecated in iOS 12.0

/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:144:37: 'SFAuthenticationSession' is deprecated: first deprecated in iOS 12.0

/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:180:58: 'openURL:' is deprecated: first deprecated in iOS 10.0

/Users/mood/POSE/kmp-app/iosApp/Pods/AppAuth/Sources/AppAuth/iOS/OIDExternalUserAgentIOS.m:203:3: 'SFAuthenticationSession' is deprecated: first deprecated in iOS 12.0

Libtool /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/AppAuth/AppAuth.framework/AppAuth normal (in target 'AppAuth' from project 'Pods')
    cd /Users/mood/POSE/kmp-app/iosApp/Pods
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/libtool -static -arch_only arm64 -D -syslibroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -L/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/AppAuth -filelist /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/AppAuth.LinkFileList -dependency_info /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppAuth.build/Objects-normal/arm64/AppAuth_libtool_dependency_info.dat -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/AppAuth/AppAuth.framework/AppAuth

libtool: warning: 'OIDAuthorizationService+IOS.o' has no symbols
libtool: warning: 'OIDAuthState+IOS.o' has no symbols
libtool: warning: 'OIDExternalUserAgentCatalyst.o' has no symbols

'OIDAuthorizationService+IOS.o' has no symbols

'OIDAuthState+IOS.o' has no symbols

'OIDExternalUserAgentCatalyst.o' has no symbols


Build target GTMSessionFetcher of project Pods with configuration Debug

CompileC /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GTMSessionFetcher.build/Objects-normal/arm64/GTMSessionFetcher.o /Users/mood/POSE/kmp-app/iosApp/Pods/GTMSessionFetcher/Sources/Core/GTMSessionFetcher.m normal arm64 objective-c com.apple.compilers.llvm.clang.1_0.compiler (in target 'GTMSessionFetcher' from project 'Pods')
    cd /Users/mood/POSE/kmp-app/iosApp/Pods
    
    Using response file: /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GTMSessionFetcher.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp
    
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -ivfsstatcache /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache -target arm64-apple-ios18.5-simulator -fmessage-length\=0 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit\=0 -fno-color-diagnostics -fmodules-prune-interval\=86400 -fmodules-prune-after\=345600 -fbuild-session-file\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation -fmodules-validate-once-per-build-session -Wnon-modular-include-in-framework-module -Werror\=non-modular-include-in-framework-module -Wno-trigraphs -Wno-missing-field-initializers -Wno-missing-prototypes -Werror\=return-type -Wdocumentation -Wunreachable-code -Wno-implicit-atomic-properties -Werror\=deprecated-objc-isa-usage -Wno-objc-interface-ivars -Werror\=objc-root-class -Wno-arc-repeated-use-of-weak -Wimplicit-retain-self -Wduplicate-method-match -Wno-missing-braces -Wparentheses -Wswitch -Wunused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wconditional-uninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wno-float-conversion -Wnon-literal-null-conversion -Wobjc-literal-conversion -Wshorten-64-to-32 -Wpointer-sign -Wno-newline-eof -Wno-selector -Wno-strict-selector-match -Wundeclared-selector -Wdeprecated-implementations -Wno-implicit-fallthrough -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -Wno-sign-conversion -Winfinite-recursion -Wcomma -Wblock-capture-autoreleasing -Wstrict-prototypes -Wno-semicolon-before-method-body -Wunguarded-availability -index-store-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Index.noindex/DataStore @/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GTMSessionFetcher.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp -MMD -MT dependencies -MF /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GTMSessionFetcher.build/Objects-normal/arm64/GTMSessionFetcher.d --serialize-diagnostics /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GTMSessionFetcher.build/Objects-normal/arm64/GTMSessionFetcher.dia -c /Users/mood/POSE/kmp-app/iosApp/Pods/GTMSessionFetcher/Sources/Core/GTMSessionFetcher.m -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GTMSessionFetcher.build/Objects-normal/arm64/GTMSessionFetcher.o -index-unit-output-path /Pods.build/Debug-iphonesimulator/GTMSessionFetcher.build/Objects-normal/arm64/GTMSessionFetcher.o

/Users/mood/POSE/kmp-app/iosApp/Pods/GTMSessionFetcher/Sources/Core/GTMSessionFetcher.m:519:11: warning: 'NSURLErrorFailingURLStringErrorKey' is deprecated: first deprecated in iOS 18.4 - Use NSURLErrorFailingURLErrorKey instead [-Wdeprecated-declarations]
  519 |         @{NSURLErrorFailingURLStringErrorKey : (urlString ? urlString : @"(missing URL)")};
      |           ^
In module 'Foundation' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/GTMSessionFetcher/Sources/Core/Public/GTMSessionFetcher/GTMSessionFetcher.h:262:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/Foundation.framework/Headers/NSURLError.h:42:36: note: 'NSURLErrorFailingURLStringErrorKey' has been explicitly marked deprecated here
   42 | FOUNDATION_EXPORT NSString * const NSURLErrorFailingURLStringErrorKey API_DEPRECATED("Use NSURLErrorFailingURLErrorKey instead", macos(10.6,15.4), ios(4.0,18.4), watchos(2.0,11.4), tvos(9.0,18.4), visionos(1.0,2.4));
      |                                    ^
1 warning generated.

/Users/mood/POSE/kmp-app/iosApp/Pods/GTMSessionFetcher/Sources/Core/GTMSessionFetcher.m:519:11: 'NSURLErrorFailingURLStringErrorKey' is deprecated: first deprecated in iOS 18.4 - Use NSURLErrorFailingURLErrorKey instead


Build target GoogleUtilities of project Pods with configuration Debug

CompileC /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleUtilities.build/Objects-normal/arm64/GULNetworkInfo.o /Users/mood/POSE/kmp-app/iosApp/Pods/GoogleUtilities/GoogleUtilities/Environment/NetworkInfo/GULNetworkInfo.m normal arm64 objective-c com.apple.compilers.llvm.clang.1_0.compiler (in target 'GoogleUtilities' from project 'Pods')
    cd /Users/mood/POSE/kmp-app/iosApp/Pods
    
    Using response file: /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleUtilities.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp
    
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -ivfsstatcache /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache -target arm64-apple-ios18.5-simulator -fmessage-length\=0 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit\=0 -fno-color-diagnostics -fmodules-prune-interval\=86400 -fmodules-prune-after\=345600 -fbuild-session-file\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation -fmodules-validate-once-per-build-session -Wnon-modular-include-in-framework-module -Werror\=non-modular-include-in-framework-module -Wno-trigraphs -Wno-missing-field-initializers -Wno-missing-prototypes -Werror\=return-type -Wdocumentation -Wunreachable-code -Wno-implicit-atomic-properties -Werror\=deprecated-objc-isa-usage -Wno-objc-interface-ivars -Werror\=objc-root-class -Wno-arc-repeated-use-of-weak -Wimplicit-retain-self -Wduplicate-method-match -Wno-missing-braces -Wparentheses -Wswitch -Wunused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wconditional-uninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wno-float-conversion -Wnon-literal-null-conversion -Wobjc-literal-conversion -Wshorten-64-to-32 -Wpointer-sign -Wno-newline-eof -Wno-selector -Wno-strict-selector-match -Wundeclared-selector -Wdeprecated-implementations -Wno-implicit-fallthrough -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -Wno-sign-conversion -Winfinite-recursion -Wcomma -Wblock-capture-autoreleasing -Wstrict-prototypes -Wno-semicolon-before-method-body -Wunguarded-availability -index-store-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Index.noindex/DataStore @/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleUtilities.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp -MMD -MT dependencies -MF /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleUtilities.build/Objects-normal/arm64/GULNetworkInfo.d --serialize-diagnostics /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleUtilities.build/Objects-normal/arm64/GULNetworkInfo.dia -c /Users/mood/POSE/kmp-app/iosApp/Pods/GoogleUtilities/GoogleUtilities/Environment/NetworkInfo/GULNetworkInfo.m -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleUtilities.build/Objects-normal/arm64/GULNetworkInfo.o -index-unit-output-path /Pods.build/Debug-iphonesimulator/GoogleUtilities.build/Objects-normal/arm64/GULNetworkInfo.o

/Users/mood/POSE/kmp-app/iosApp/Pods/GoogleUtilities/GoogleUtilities/Environment/NetworkInfo/GULNetworkInfo.m:47:23: warning: 'SCNetworkReachabilityCreateWithName' is deprecated: first deprecated in iOS 17.4 [-Wdeprecated-declarations]
   47 |     reachabilityRef = SCNetworkReachabilityCreateWithName(kCFAllocatorSystemDefault, "google.com");
      |                       ^~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      |                       Use URLSession or NWConnection to create connections that dynamically handle changing networks. Use NWPathMonitor to enumerate available network interfaces.
In module 'SystemConfiguration' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/GoogleUtilities/GoogleUtilities/Environment/NetworkInfo/GULNetworkInfo.m:24:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/SystemConfiguration.framework/Headers/SCNetworkReachability.h:249:1: note: 'SCNetworkReachabilityCreateWithName' has been explicitly marked deprecated here
  249 | SCNetworkReachabilityCreateWithName             (
      | ^
/Users/mood/POSE/kmp-app/iosApp/Pods/GoogleUtilities/GoogleUtilities/Environment/NetworkInfo/GULNetworkInfo.m:55:3: warning: 'SCNetworkReachabilityGetFlags' is deprecated: first deprecated in iOS 17.4 [-Wdeprecated-declarations]
   55 |   SCNetworkReachabilityGetFlags(reachabilityRef, &reachabilityFlags);
      |   ^~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      |   Use URLSession or NWConnection to create connections that dynamically handle changing networks. Use NWPathMonitor to enumerate available network interfaces.
In module 'SystemConfiguration' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/GoogleUtilities/GoogleUtilities/Environment/NetworkInfo/GULNetworkInfo.m:24:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/SystemConfiguration.framework/Headers/SCNetworkReachability.h:278:1: note: 'SCNetworkReachabilityGetFlags' has been explicitly marked deprecated here
  278 | SCNetworkReachabilityGetFlags                   (
      | ^
2 warnings generated.

/Users/mood/POSE/kmp-app/iosApp/Pods/GoogleUtilities/GoogleUtilities/Environment/NetworkInfo/GULNetworkInfo.m:47:23: 'SCNetworkReachabilityCreateWithName' is deprecated: first deprecated in iOS 17.4

/Users/mood/POSE/kmp-app/iosApp/Pods/GoogleUtilities/GoogleUtilities/Environment/NetworkInfo/GULNetworkInfo.m:55:3: 'SCNetworkReachabilityGetFlags' is deprecated: first deprecated in iOS 17.4


Build target RecaptchaInterop of project Pods with configuration Debug

Libtool /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/RecaptchaInterop/RecaptchaInterop.framework/RecaptchaInterop normal (in target 'RecaptchaInterop' from project 'Pods')
    cd /Users/mood/POSE/kmp-app/iosApp/Pods
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/libtool -static -arch_only arm64 -D -syslibroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -L/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/RecaptchaInterop -filelist /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/RecaptchaInterop.build/Objects-normal/arm64/RecaptchaInterop.LinkFileList -dependency_info /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/RecaptchaInterop.build/Objects-normal/arm64/RecaptchaInterop_libtool_dependency_info.dat -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/RecaptchaInterop/RecaptchaInterop.framework/RecaptchaInterop

libtool: warning: 'placeholder.o' has no symbols

'placeholder.o' has no symbols


Build target AppCheckCore of project Pods with configuration Debug

Libtool /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/AppCheckCore/AppCheckCore.framework/AppCheckCore normal (in target 'AppCheckCore' from project 'Pods')
    cd /Users/mood/POSE/kmp-app/iosApp/Pods
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/libtool -static -arch_only arm64 -D -syslibroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -L/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/AppCheckCore -filelist /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppCheckCore.build/Objects-normal/arm64/AppCheckCore.LinkFileList -dependency_info /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/AppCheckCore.build/Objects-normal/arm64/AppCheckCore_libtool_dependency_info.dat -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/AppCheckCore/AppCheckCore.framework/AppCheckCore

libtool: warning: 'GACAppCheckStoredToken+GACAppCheckToken.o' has no symbols
libtool: warning: 'GACAppCheckToken+APIResponse.o' has no symbols

'GACAppCheckStoredToken+GACAppCheckToken.o' has no symbols

'GACAppCheckToken+APIResponse.o' has no symbols


Build target GoogleSignIn of project Pods with configuration Debug

CompileC /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/GIDActivityIndicatorViewController.o /Users/mood/POSE/kmp-app/iosApp/Pods/GoogleSignIn/GoogleSignIn/Sources/GIDAppCheck/UI/GIDActivityIndicatorViewController.m normal arm64 objective-c com.apple.compilers.llvm.clang.1_0.compiler (in target 'GoogleSignIn' from project 'Pods')
    cd /Users/mood/POSE/kmp-app/iosApp/Pods
    
    Using response file: /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp
    
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -ivfsstatcache /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache -target arm64-apple-ios18.5-simulator -fmessage-length\=0 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit\=0 -fno-color-diagnostics -fmodules-prune-interval\=86400 -fmodules-prune-after\=345600 -fbuild-session-file\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation -fmodules-validate-once-per-build-session -Wnon-modular-include-in-framework-module -Werror\=non-modular-include-in-framework-module -Wno-trigraphs -Wno-missing-field-initializers -Wno-missing-prototypes -Werror\=return-type -Wdocumentation -Wunreachable-code -Wno-implicit-atomic-properties -Werror\=deprecated-objc-isa-usage -Wno-objc-interface-ivars -Werror\=objc-root-class -Wno-arc-repeated-use-of-weak -Wimplicit-retain-self -Wduplicate-method-match -Wno-missing-braces -Wparentheses -Wswitch -Wunused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wconditional-uninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wno-float-conversion -Wnon-literal-null-conversion -Wobjc-literal-conversion -Wshorten-64-to-32 -Wpointer-sign -Wno-newline-eof -Wno-selector -Wno-strict-selector-match -Wundeclared-selector -Wdeprecated-implementations -Wno-implicit-fallthrough -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -Wno-sign-conversion -Winfinite-recursion -Wcomma -Wblock-capture-autoreleasing -Wstrict-prototypes -Wno-semicolon-before-method-body -Wunguarded-availability -index-store-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Index.noindex/DataStore @/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp -MMD -MT dependencies -MF /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/GIDActivityIndicatorViewController.d --serialize-diagnostics /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/GIDActivityIndicatorViewController.dia -c /Users/mood/POSE/kmp-app/iosApp/Pods/GoogleSignIn/GoogleSignIn/Sources/GIDAppCheck/UI/GIDActivityIndicatorViewController.m -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/GIDActivityIndicatorViewController.o -index-unit-output-path /Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/GIDActivityIndicatorViewController.o

/Users/mood/POSE/kmp-app/iosApp/Pods/GoogleSignIn/GoogleSignIn/Sources/GIDAppCheck/UI/GIDActivityIndicatorViewController.m:34:13: warning: 'UIActivityIndicatorViewStyleGray' is deprecated: first deprecated in iOS 13.0 [-Wdeprecated-declarations]
   34 |     style = UIActivityIndicatorViewStyleGray;
      |             ^~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      |             UIActivityIndicatorViewStyleMedium
In module 'UIKit' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/GoogleSignIn/GoogleSignIn/Sources/GIDAppCheck/UI/GIDActivityIndicatorViewController.h:21:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/UIKit.framework/Headers/UIActivityIndicatorView.h:20:5: note: 'UIActivityIndicatorViewStyleGray' has been explicitly marked deprecated here
   20 |     UIActivityIndicatorViewStyleGray API_DEPRECATED_WITH_REPLACEMENT("UIActivityIndicatorViewStyleMedium", ios(2.0, 13.0)) API_UNAVAILABLE(tvos, visionos, watchos) = 2,
      |     ^
1 warning generated.

/Users/mood/POSE/kmp-app/iosApp/Pods/GoogleSignIn/GoogleSignIn/Sources/GIDAppCheck/UI/GIDActivityIndicatorViewController.m:34:13: 'UIActivityIndicatorViewStyleGray' is deprecated: first deprecated in iOS 13.0

CompileC /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/NSBundle+GID3PAdditions.o /Users/mood/POSE/kmp-app/iosApp/Pods/GoogleSignIn/GoogleSignIn/Sources/NSBundle+GID3PAdditions.m normal arm64 objective-c com.apple.compilers.llvm.clang.1_0.compiler (in target 'GoogleSignIn' from project 'Pods')
    cd /Users/mood/POSE/kmp-app/iosApp/Pods
    
    Using response file: /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp
    
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -x objective-c -ivfsstatcache /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache -target arm64-apple-ios18.5-simulator -fmessage-length\=0 -fdiagnostics-show-note-include-stack -fmacro-backtrace-limit\=0 -fno-color-diagnostics -fmodules-prune-interval\=86400 -fmodules-prune-after\=345600 -fbuild-session-file\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation -fmodules-validate-once-per-build-session -Wnon-modular-include-in-framework-module -Werror\=non-modular-include-in-framework-module -Wno-trigraphs -Wno-missing-field-initializers -Wno-missing-prototypes -Werror\=return-type -Wdocumentation -Wunreachable-code -Wno-implicit-atomic-properties -Werror\=deprecated-objc-isa-usage -Wno-objc-interface-ivars -Werror\=objc-root-class -Wno-arc-repeated-use-of-weak -Wimplicit-retain-self -Wduplicate-method-match -Wno-missing-braces -Wparentheses -Wswitch -Wunused-function -Wno-unused-label -Wno-unused-parameter -Wunused-variable -Wunused-value -Wempty-body -Wuninitialized -Wconditional-uninitialized -Wno-unknown-pragmas -Wno-shadow -Wno-four-char-constants -Wno-conversion -Wconstant-conversion -Wint-conversion -Wbool-conversion -Wenum-conversion -Wno-float-conversion -Wnon-literal-null-conversion -Wobjc-literal-conversion -Wshorten-64-to-32 -Wpointer-sign -Wno-newline-eof -Wno-selector -Wno-strict-selector-match -Wundeclared-selector -Wdeprecated-implementations -Wno-implicit-fallthrough -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -fstrict-aliasing -Wprotocol -Wdeprecated-declarations -Wno-sign-conversion -Winfinite-recursion -Wcomma -Wblock-capture-autoreleasing -Wstrict-prototypes -Wno-semicolon-before-method-body -Wunguarded-availability -index-store-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Index.noindex/DataStore @/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/e6072d4f65d7061329687fe24e3d63a7-common-args.resp -MMD -MT dependencies -MF /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/NSBundle+GID3PAdditions.d --serialize-diagnostics /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/NSBundle+GID3PAdditions.dia -c /Users/mood/POSE/kmp-app/iosApp/Pods/GoogleSignIn/GoogleSignIn/Sources/NSBundle+GID3PAdditions.m -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/NSBundle+GID3PAdditions.o -index-unit-output-path /Pods.build/Debug-iphonesimulator/GoogleSignIn.build/Objects-normal/arm64/NSBundle+GID3PAdditions.o

/Users/mood/POSE/kmp-app/iosApp/Pods/GoogleSignIn/GoogleSignIn/Sources/NSBundle+GID3PAdditions.m:65:24: warning: 'CTFontManagerRegisterGraphicsFont' is deprecated: first deprecated in iOS 18 - Use CTFontManagerCreateFontDescriptorsFromData or CTFontManagerRegisterFontsForURL [-Wdeprecated-declarations]
   65 |       if (!newFont || !CTFontManagerRegisterGraphicsFont(newFont, &error)) {
      |                        ^
In module 'CoreText' imported from /Users/mood/POSE/kmp-app/iosApp/Pods/GoogleSignIn/GoogleSignIn/Sources/NSBundle+GID3PAdditions.m:17:
/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/CoreText.framework/Headers/CTFontManager.h:216:6: note: 'CTFontManagerRegisterGraphicsFont' has been explicitly marked deprecated here
  216 | bool CTFontManagerRegisterGraphicsFont(
      |      ^
1 warning generated.

/Users/mood/POSE/kmp-app/iosApp/Pods/GoogleSignIn/GoogleSignIn/Sources/NSBundle+GID3PAdditions.m:65:24: 'CTFontManagerRegisterGraphicsFont' is deprecated: first deprecated in iOS 18 - Use CTFontManagerCreateFontDescriptorsFromData or CTFontManagerRegisterFontsForURL

