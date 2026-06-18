
Showing Recent Messages

Prepare build

ComputePackagePrebuildTargetDependencyGraph

CreateBuildRequest

SendProjectDescription

CreateBuildOperation

ComputeTargetDependencyGraph

note: Building targets in dependency order
note: Target dependency graph (1 target)
    Target 'iosApp' in project 'iosApp' (no dependencies)

Building targets in dependency order

Target dependency graph (1 target)

GatherProvisioningInputs

ClangStatCache /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang-stat-cache /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache
    cd /Users/mood/POSE/android-poc/iosApp
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang-stat-cache /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -o /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache

SwiftExplicitDependencyGeneratePcm arm64 /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/SwiftExplicitPrecompiledModules/MovitApp-3B13QTGU4VECLWQQ5K3D3D0ZS.pcm

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:114860:16: warning: imported declaration 'MovitAppOffsetCompanion' could not be mapped to 'Offset.Companion' [#ClangDeclarationImport]
114858 | 
114859 | __attribute__((objc_subclassing_restricted))
114860 | __attribute__((swift_name("Offset.Companion")))
       |                |- warning: imported declaration 'MovitAppOffsetCompanion' could not be mapped to 'Offset.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
114861 | @interface MovitAppOffsetCompanion : MovitAppBase
114862 | + (instancetype)alloc __attribute__((unavailable));

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:114986:16: warning: imported declaration 'MovitAppSize_Companion' could not be mapped to 'Size_.Companion' [#ClangDeclarationImport]
114984 | 
114985 | __attribute__((objc_subclassing_restricted))
114986 | __attribute__((swift_name("Size_.Companion")))
       |                |- warning: imported declaration 'MovitAppSize_Companion' could not be mapped to 'Size_.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
114987 | @interface MovitAppSize_Companion : MovitAppBase
114988 | + (instancetype)alloc __attribute__((unavailable));

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115025:16: warning: imported declaration 'MovitAppColor_Companion' could not be mapped to 'Color_.Companion' [#ClangDeclarationImport]
115023 | 
115024 | __attribute__((objc_subclassing_restricted))
115025 | __attribute__((swift_name("Color_.Companion")))
       |                |- warning: imported declaration 'MovitAppColor_Companion' could not be mapped to 'Color_.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
115026 | @interface MovitAppColor_Companion : MovitAppBase
115027 | + (instancetype)alloc __attribute__((unavailable));

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115162:16: warning: imported declaration 'MovitAppPathOperationCompanion' could not be mapped to 'PathOperation.Companion' [#ClangDeclarationImport]
115160 | 
115161 | __attribute__((objc_subclassing_restricted))
115162 | __attribute__((swift_name("PathOperation.Companion")))
       |                |- warning: imported declaration 'MovitAppPathOperationCompanion' could not be mapped to 'PathOperation.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
115163 | @interface MovitAppPathOperationCompanion : MovitAppBase
115164 | + (instancetype)alloc __attribute__((unavailable));

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115797:16: warning: imported declaration 'MovitAppDpCompanion' could not be mapped to 'Dp.Companion' [#ClangDeclarationImport]
115795 | 
115796 | __attribute__((objc_subclassing_restricted))
115797 | __attribute__((swift_name("Dp.Companion")))
       |                |- warning: imported declaration 'MovitAppDpCompanion' could not be mapped to 'Dp.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
115798 | @interface MovitAppDpCompanion : MovitAppBase
115799 | + (instancetype)alloc __attribute__((unavailable));

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115844:16: warning: imported declaration 'MovitAppDpOffsetCompanion' could not be mapped to 'DpOffset.Companion' [#ClangDeclarationImport]
115842 | 
115843 | __attribute__((objc_subclassing_restricted))
115844 | __attribute__((swift_name("DpOffset.Companion")))
       |                |- warning: imported declaration 'MovitAppDpOffsetCompanion' could not be mapped to 'DpOffset.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
115845 | @interface MovitAppDpOffsetCompanion : MovitAppBase
115846 | + (instancetype)alloc __attribute__((unavailable));

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115900:16: warning: imported declaration 'MovitAppIntOffsetCompanion' could not be mapped to 'IntOffset.Companion' [#ClangDeclarationImport]
115898 | 
115899 | __attribute__((objc_subclassing_restricted))
115900 | __attribute__((swift_name("IntOffset.Companion")))
       |                |- warning: imported declaration 'MovitAppIntOffsetCompanion' could not be mapped to 'IntOffset.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
115901 | @interface MovitAppIntOffsetCompanion : MovitAppBase
115902 | + (instancetype)alloc __attribute__((unavailable));

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115934:16: warning: imported declaration 'MovitAppIntSizeCompanion' could not be mapped to 'IntSize.Companion' [#ClangDeclarationImport]
115932 | 
115933 | __attribute__((objc_subclassing_restricted))
115934 | __attribute__((swift_name("IntSize.Companion")))
       |                |- warning: imported declaration 'MovitAppIntSizeCompanion' could not be mapped to 'IntSize.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
115935 | @interface MovitAppIntSizeCompanion : MovitAppBase
115936 | + (instancetype)alloc __attribute__((unavailable));

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:118961:16: warning: imported declaration 'MovitAppKotlinCharCompanion' could not be mapped to 'KotlinChar.Companion' [#ClangDeclarationImport]
118959 | 
118960 | __attribute__((objc_subclassing_restricted))
118961 | __attribute__((swift_name("KotlinChar.Companion")))
       |                |- warning: imported declaration 'MovitAppKotlinCharCompanion' could not be mapped to 'KotlinChar.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
118962 | @interface MovitAppKotlinCharCompanion : MovitAppBase
118963 | + (instancetype)alloc __attribute__((unavailable));

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:119226:16: warning: imported declaration 'MovitAppKotlinStringCompanion' could not be mapped to 'KotlinString.Companion' [#ClangDeclarationImport]
119224 | 
119225 | __attribute__((objc_subclassing_restricted))
119226 | __attribute__((swift_name("KotlinString.Companion")))
       |                |- warning: imported declaration 'MovitAppKotlinStringCompanion' could not be mapped to 'KotlinString.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
119227 | @interface MovitAppKotlinStringCompanion : MovitAppBase
119228 | + (instancetype)alloc __attribute__((unavailable));

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:119652:16: warning: imported declaration 'MovitAppKotlinDurationCompanion' could not be mapped to 'KotlinDuration.Companion' [#ClangDeclarationImport]
119650 | 
119651 | __attribute__((objc_subclassing_restricted))
119652 | __attribute__((swift_name("KotlinDuration.Companion")))
       |                |- warning: imported declaration 'MovitAppKotlinDurationCompanion' could not be mapped to 'KotlinDuration.Companion' [#ClangDeclarationImport]
       |                `- note: please report this issue to the owners of 'MovitApp'
119653 | @interface MovitAppKotlinDurationCompanion : MovitAppBase
119654 | + (instancetype)alloc __attribute__((unavailable));

[#ClangDeclarationImport]: <https://docs.swift.org/compiler/documentation/diagnostics/clang-declaration-import>

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:114860:16: imported declaration 'MovitAppOffsetCompanion' could not be mapped to 'Offset.Companion' [#ClangDeclarationImport]

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:114986:16: imported declaration 'MovitAppSize_Companion' could not be mapped to 'Size_.Companion' [#ClangDeclarationImport]

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115025:16: imported declaration 'MovitAppColor_Companion' could not be mapped to 'Color_.Companion' [#ClangDeclarationImport]

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115162:16: imported declaration 'MovitAppPathOperationCompanion' could not be mapped to 'PathOperation.Companion' [#ClangDeclarationImport]

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115797:16: imported declaration 'MovitAppDpCompanion' could not be mapped to 'Dp.Companion' [#ClangDeclarationImport]

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115844:16: imported declaration 'MovitAppDpOffsetCompanion' could not be mapped to 'DpOffset.Companion' [#ClangDeclarationImport]

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115900:16: imported declaration 'MovitAppIntOffsetCompanion' could not be mapped to 'IntOffset.Companion' [#ClangDeclarationImport]

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:115934:16: imported declaration 'MovitAppIntSizeCompanion' could not be mapped to 'IntSize.Companion' [#ClangDeclarationImport]

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:118961:16: imported declaration 'MovitAppKotlinCharCompanion' could not be mapped to 'KotlinChar.Companion' [#ClangDeclarationImport]

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:119226:16: imported declaration 'MovitAppKotlinStringCompanion' could not be mapped to 'KotlinString.Companion' [#ClangDeclarationImport]

/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/Headers/MovitApp.h:119652:16: imported declaration 'MovitAppKotlinDurationCompanion' could not be mapped to 'KotlinDuration.Companion' [#ClangDeclarationImport]


Build target iosApp of project iosApp with configuration Debug
note: Run script build phase 'Build MovitApp Kotlin framework' will be run during every build because the option to run the script phase "Based on dependency analysis" is unchecked. (in target 'iosApp' from project 'iosApp')
note: Run script build phase 'Ensure pose_landmarker model in bundle' will be run during every build because the option to run the script phase "Based on dependency analysis" is unchecked. (in target 'iosApp' from project 'iosApp')
note: Run script build phase 'Require iosApp.xcworkspace (CocoaPods)' will be run during every build because the option to run the script phase "Based on dependency analysis" is unchecked. (in target 'iosApp' from project 'iosApp')


Run script build phase 'Build MovitApp Kotlin framework' will be run during every build because the option to run the script phase "Based on dependency analysis" is unchecked.

Run script build phase 'Ensure pose_landmarker model in bundle' will be run during every build because the option to run the script phase "Based on dependency analysis" is unchecked.

Run script build phase 'Require iosApp.xcworkspace (CocoaPods)' will be run during every build because the option to run the script phase "Based on dependency analysis" is unchecked.

PhaseScriptExecution Require\ iosApp.xcworkspace\ (CocoaPods) /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Script-AF82C982105A73DD73455872.sh (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    export ACTION\=build
    export AD_HOC_CODE_SIGNING_ALLOWED\=YES
    export AGGREGATE_TRACKED_DOMAINS\=YES
    export ALLOW_BUILD_REQUEST_OVERRIDES\=NO
    export ALLOW_TARGET_PLATFORM_SPECIALIZATION\=NO
    export ALTERNATE_GROUP\=staff
    export ALTERNATE_MODE\=u+w,go-w,a+rX
    export ALTERNATE_OWNER\=mood
    export ALTERNATIVE_DISTRIBUTION_WEB\=NO
    export ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES\=NO
    export ALWAYS_SEARCH_USER_PATHS\=NO
    export ALWAYS_USE_SEPARATE_HEADERMAPS\=NO
    export APPLICATION_EXTENSION_API_ONLY\=NO
    export APPLY_RULES_IN_COPY_FILES\=NO
    export APPLY_RULES_IN_COPY_HEADERS\=NO
    export APP_SHORTCUTS_ENABLE_FLEXIBLE_MATCHING\=YES
    export ARCHS\=arm64
    export ARCHS_BASE\=arm64
    export ARCHS_STANDARD\=arm64\ x86_64
    export ARCHS_STANDARD_32_64_BIT\=arm64\ x86_64
    export ARCHS_STANDARD_64_BIT\=arm64\ x86_64
    export ARCHS_STANDARD_INCLUDING_64_BIT\=arm64\ x86_64
    export ARCHS_UNIVERSAL_IPHONE_OS\=arm64\ x86_64
    export ASSETCATALOG_COMPILER_APPICON_NAME\=AppIcon
    export ASSETCATALOG_FILTER_FOR_DEVICE_MODEL\=iPhone18,1
    export ASSETCATALOG_FILTER_FOR_DEVICE_OS_VERSION\=26.5
    export ASSETCATALOG_FILTER_FOR_THINNING_DEVICE_CONFIGURATION\=iPhone18,1
    export AUTOMATICALLY_MERGE_DEPENDENCIES\=NO
    export AUTOMATION_APPLE_EVENTS\=NO
    export AVAILABLE_PLATFORMS\=android\ appletvos\ appletvsimulator\ driverkit\ freebsd\ iphoneos\ iphonesimulator\ linux\ macosx\ none\ openbsd\ qnx\ watchos\ watchsimulator\ webassembly\ xros\ xrsimulator
    export AppIdentifierPrefix\=S2QF6L6CJZ.
    export BUILD_ACTIVE_RESOURCES_ONLY\=YES
    export BUILD_COMPONENTS\=headers\ build
    export BUILD_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products
    export BUILD_LIBRARY_FOR_DISTRIBUTION\=NO
    export BUILD_ONLY_KNOWN_LOCALIZATIONS\=NO
    export BUILD_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products
    export BUILD_STYLE\=
    export BUILD_VARIANTS\=normal
    export BUILT_PRODUCTS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export BUNDLE_CONTENTS_FOLDER_PATH_deep\=Contents/
    export BUNDLE_EXECUTABLE_FOLDER_NAME_deep\=MacOS
    export BUNDLE_EXTENSIONS_FOLDER_PATH\=Extensions
    export BUNDLE_FORMAT\=shallow
    export BUNDLE_FRAMEWORKS_FOLDER_PATH\=Frameworks
    export BUNDLE_PLUGINS_FOLDER_PATH\=PlugIns
    export BUNDLE_PRIVATE_HEADERS_FOLDER_PATH\=PrivateHeaders
    export BUNDLE_PUBLIC_HEADERS_FOLDER_PATH\=Headers
    export CACHE_ROOT\=/var/folders/xz/9yr8chc564x0w_mj7j122k6m0000gn/C/com.apple.DeveloperTools/26.5-17F42/Xcode
    export CCHROOT\=/var/folders/xz/9yr8chc564x0w_mj7j122k6m0000gn/C/com.apple.DeveloperTools/26.5-17F42/Xcode
    export CHMOD\=/bin/chmod
    export CHOWN\=chown
    export CLANG_ANALYZER_NONNULL\=YES
    export CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION\=YES_AGGRESSIVE
    export CLANG_CACHE_FINE_GRAINED_OUTPUTS\=YES
    export CLANG_CXX_LANGUAGE_STANDARD\=gnu++14
    export CLANG_CXX_LIBRARY\=libc++
    export CLANG_ENABLE_EXPLICIT_MODULES\=YES
    export CLANG_ENABLE_MODULES\=YES
    export CLANG_ENABLE_OBJC_ARC\=YES
    export CLANG_ENABLE_OBJC_WEAK\=YES
    export CLANG_MODULES_BUILD_SESSION_FILE\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation
    export CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING\=YES
    export CLANG_WARN_BOOL_CONVERSION\=YES
    export CLANG_WARN_COMMA\=YES
    export CLANG_WARN_CONSTANT_CONVERSION\=YES
    export CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS\=YES
    export CLANG_WARN_DIRECT_OBJC_ISA_USAGE\=YES_ERROR
    export CLANG_WARN_DOCUMENTATION_COMMENTS\=YES
    export CLANG_WARN_EMPTY_BODY\=YES
    export CLANG_WARN_ENUM_CONVERSION\=YES
    export CLANG_WARN_INFINITE_RECURSION\=YES
    export CLANG_WARN_INT_CONVERSION\=YES
    export CLANG_WARN_NON_LITERAL_NULL_CONVERSION\=YES
    export CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF\=YES
    export CLANG_WARN_OBJC_LITERAL_CONVERSION\=YES
    export CLANG_WARN_OBJC_ROOT_CLASS\=YES_ERROR
    export CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER\=YES
    export CLANG_WARN_RANGE_LOOP_ANALYSIS\=YES
    export CLANG_WARN_STRICT_PROTOTYPES\=YES
    export CLANG_WARN_SUSPICIOUS_MOVE\=YES
    export CLANG_WARN_UNGUARDED_AVAILABILITY\=YES_AGGRESSIVE
    export CLANG_WARN_UNREACHABLE_CODE\=YES
    export CLANG_WARN__DUPLICATE_METHOD_MATCH\=YES
    export CLASS_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/JavaClasses
    export CLEAN_PRECOMPS\=YES
    export CLONE_HEADERS\=NO
    export CODESIGNING_FOLDER_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app
    export CODE_SIGNING_ALLOWED\=YES
    export CODE_SIGNING_REQUIRED\=YES
    export CODE_SIGN_CONTEXT_CLASS\=XCiPhoneSimulatorCodeSignContext
    export CODE_SIGN_ENTITLEMENTS\=iosApp/iosApp.entitlements
    export CODE_SIGN_IDENTITY\=iPhone\ Developer
    export CODE_SIGN_INJECT_BASE_ENTITLEMENTS\=YES
    export COLOR_DIAGNOSTICS\=NO
    export COMBINE_HIDPI_IMAGES\=NO
    export COMPILATION_CACHE_CAS_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/CompilationCache.noindex
    export COMPILATION_CACHE_KEEP_CAS_DIRECTORY\=YES
    export COMPILER_INDEX_STORE_ENABLE\=Default
    export COMPOSITE_SDK_DIRS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/CompositeSDKs
    export COMPRESS_PNG_FILES\=YES
    export CONFIGURATION\=Debug
    export CONFIGURATION_BUILD_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export CONFIGURATION_TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator
    export CONTENTS_FOLDER_PATH\=iosApp.app
    export CONTENTS_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/Contents
    export CONTENTS_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app
    export COPYING_PRESERVES_HFS_DATA\=NO
    export COPY_HEADERS_RUN_UNIFDEF\=NO
    export COPY_PHASE_STRIP\=NO
    export CORRESPONDING_DEVICE_PLATFORM_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform
    export CORRESPONDING_DEVICE_PLATFORM_NAME\=iphoneos
    export CORRESPONDING_DEVICE_SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS26.5.sdk
    export CORRESPONDING_DEVICE_SDK_NAME\=iphoneos26.5
    export CP\=/bin/cp
    export CREATE_INFOPLIST_SECTION_IN_BINARY\=NO
    export CURRENT_ARCH\=undefined_arch
    export CURRENT_VARIANT\=normal
    export DEAD_CODE_STRIPPING\=YES
    export DEBUGGING_SYMBOLS\=YES
    export DEBUG_INFORMATION_FORMAT\=dwarf
    export DEBUG_INFORMATION_VERSION\=compiler-default
    export DEFAULT_COMPILER\=com.apple.compilers.llvm.clang.1_0
    export DEFAULT_DEXT_INSTALL_PATH\=/System/Library/DriverExtensions
    export DEFAULT_KEXT_INSTALL_PATH\=/System/Library/Extensions
    export DEFINES_MODULE\=NO
    export DEPLOYMENT_LOCATION\=NO
    export DEPLOYMENT_POSTPROCESSING\=NO
    export DEPLOYMENT_TARGET_SETTING_NAME\=IPHONEOS_DEPLOYMENT_TARGET
    export DEPLOYMENT_TARGET_SUGGESTED_VALUES\=12.0\ 12.1\ 12.2\ 12.3\ 12.4\ 13.0\ 13.1\ 13.2\ 13.3\ 13.4\ 13.5\ 13.6\ 14.0\ 14.1\ 14.2\ 14.3\ 14.4\ 14.5\ 14.6\ 14.7\ 15.0\ 15.1\ 15.2\ 15.3\ 15.4\ 15.5\ 15.6\ 16.0\ 16.1\ 16.2\ 16.3\ 16.4\ 16.5\ 16.6\ 17.0\ 17.1\ 17.2\ 17.3\ 17.4\ 17.5\ 17.6\ 18.0\ 18.1\ 18.2\ 18.3\ 18.4\ 18.5\ 18.6\ 26.0\ 26.2\ 26.3\ 26.4\ 26.5
    export DERIVED_FILES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources
    export DERIVED_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources
    export DERIVED_SOURCES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources
    export DEVELOPER_APPLICATIONS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications
    export DEVELOPER_BIN_DIR\=/Applications/Xcode.app/Contents/Developer/usr/bin
    export DEVELOPER_DIR\=/Applications/Xcode.app/Contents/Developer
    export DEVELOPER_FRAMEWORKS_DIR\=/Applications/Xcode.app/Contents/Developer/Library/Frameworks
    export DEVELOPER_FRAMEWORKS_DIR_QUOTED\=/Applications/Xcode.app/Contents/Developer/Library/Frameworks
    export DEVELOPER_LIBRARY_DIR\=/Applications/Xcode.app/Contents/Developer/Library
    export DEVELOPER_SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs
    export DEVELOPER_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Tools
    export DEVELOPER_USR_DIR\=/Applications/Xcode.app/Contents/Developer/usr
    export DEVELOPMENT_LANGUAGE\=en
    export DIAGNOSE_MISSING_TARGET_DEPENDENCIES\=YES
    export DIFF\=/usr/bin/diff
    export DOCUMENTATION_FOLDER_PATH\=iosApp.app/en.lproj/Documentation
    export DONT_GENERATE_INFOPLIST_FILE\=NO
    export DSTROOT\=/tmp/iosApp.dst
    export DT_TOOLCHAIN_DIR\=/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain
    export DUMP_DEPENDENCIES\=NO
    export DUMP_DEPENDENCIES_OUTPUT_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-BuildDependencyInfo.json
    export DWARF_DSYM_FILE_NAME\=iosApp.app.dSYM
    export DWARF_DSYM_FILE_SHOULD_ACCOMPANY_PRODUCT\=NO
    export DWARF_DSYM_FOLDER_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export DYNAMIC_LIBRARY_EXTENSION\=dylib
    export EAGER_COMPILATION_ALLOW_SCRIPTS\=NO
    export EAGER_LINKING\=NO
    export EFFECTIVE_PLATFORM_NAME\=-iphonesimulator
    export EFFECTIVE_SWIFT_VERSION\=5
    export EMBEDDED_CONTENT_CONTAINS_SWIFT\=NO
    export EMBED_ASSET_PACKS_IN_PRODUCT_BUNDLE\=NO
    export ENABLE_APP_SANDBOX\=NO
    export ENABLE_CODE_COVERAGE\=YES
    export ENABLE_COHORT_ARCHS\=NO
    export ENABLE_CPLUSPLUS_BOUNDS_SAFE_BUFFERS\=NO
    export ENABLE_C_BOUNDS_SAFETY\=NO
    export ENABLE_DEBUG_DYLIB\=YES
    export ENABLE_DEFAULT_HEADER_SEARCH_PATHS\=YES
    export ENABLE_DEFAULT_SEARCH_PATHS\=YES
    export ENABLE_ENHANCED_SECURITY\=NO
    export ENABLE_HARDENED_RUNTIME\=NO
    export ENABLE_HEADER_DEPENDENCIES\=YES
    export ENABLE_INCOMING_NETWORK_CONNECTIONS\=NO
    export ENABLE_ON_DEMAND_RESOURCES\=YES
    export ENABLE_OUTGOING_NETWORK_CONNECTIONS\=NO
    export ENABLE_POINTER_AUTHENTICATION\=NO
    export ENABLE_PREVIEWS\=NO
    export ENABLE_RESOURCE_ACCESS_AUDIO_INPUT\=NO
    export ENABLE_RESOURCE_ACCESS_BLUETOOTH\=NO
    export ENABLE_RESOURCE_ACCESS_CALENDARS\=NO
    export ENABLE_RESOURCE_ACCESS_CAMERA\=NO
    export ENABLE_RESOURCE_ACCESS_CONTACTS\=NO
    export ENABLE_RESOURCE_ACCESS_LOCATION\=NO
    export ENABLE_RESOURCE_ACCESS_PHOTO_LIBRARY\=NO
    export ENABLE_RESOURCE_ACCESS_PRINTING\=NO
    export ENABLE_RESOURCE_ACCESS_USB\=NO
    export ENABLE_SDK_IMPORTS\=NO
    export ENABLE_SECURITY_COMPILER_WARNINGS\=NO
    export ENABLE_STRICT_OBJC_MSGSEND\=YES
    export ENABLE_TESTABILITY\=YES
    export ENABLE_TESTING_SEARCH_PATHS\=NO
    export ENABLE_USER_SCRIPT_SANDBOXING\=NO
    export ENABLE_XOJIT_PREVIEWS\=YES
    export ENFORCE_VALID_ARCHS\=YES
    export ENTITLEMENTS_ALLOWED\=NO
    export ENTITLEMENTS_DESTINATION\=__entitlements
    export ENTITLEMENTS_REQUIRED\=NO
    export EXCLUDED_ARCHS\=x86_64
    export EXCLUDED_INSTALLSRC_SUBDIRECTORY_PATTERNS\=.DS_Store\ .svn\ .git\ .hg\ CVS
    export EXCLUDED_RECURSIVE_SEARCH_PATH_SUBDIRECTORIES\=\*.nib\ \*.lproj\ \*.framework\ \*.gch\ \*.xcode\*\ \*.xcassets\ \*.icon\ \(\*\)\ .DS_Store\ CVS\ .svn\ .git\ .hg\ \*.pbproj\ \*.pbxproj
    export EXECUTABLES_FOLDER_PATH\=iosApp.app/Executables
    export EXECUTABLE_BLANK_INJECTION_DYLIB_PATH\=iosApp.app/__preview.dylib
    export EXECUTABLE_DEBUG_DYLIB_INSTALL_NAME\=@rpath/iosApp.debug.dylib
    export EXECUTABLE_DEBUG_DYLIB_PATH\=iosApp.app/iosApp.debug.dylib
    export EXECUTABLE_FOLDER_PATH\=iosApp.app
    export EXECUTABLE_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/MacOS
    export EXECUTABLE_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app
    export EXECUTABLE_NAME\=iosApp
    export EXECUTABLE_PATH\=iosApp.app/iosApp
    export EXPANDED_CODE_SIGN_IDENTITY\=-
    export EXPANDED_CODE_SIGN_IDENTITY_NAME\=Sign\ to\ Run\ Locally
    export EXTENSIONS_FOLDER_PATH\=iosApp.app/Extensions
    export FILE_LIST\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects/LinkFileList
    export FIXED_FILES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/FixedFiles
    export FRAMEWORKS_FOLDER_PATH\=iosApp.app/Frameworks
    export FRAMEWORK_FLAG_PREFIX\=-framework
    export FRAMEWORK_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator\ \ /Users/mood/POSE/android-poc/iosApp/../feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5
    export FRAMEWORK_VERSION\=A
    export FULL_PRODUCT_NAME\=iosApp.app
    export FUSE_BUILD_PHASES\=YES
    export FUSE_BUILD_SCRIPT_PHASES\=NO
    export GCC3_VERSION\=3.3
    export GCC_C_LANGUAGE_STANDARD\=gnu11
    export GCC_DYNAMIC_NO_PIC\=NO
    export GCC_INLINES_ARE_PRIVATE_EXTERN\=YES
    export GCC_NO_COMMON_BLOCKS\=YES
    export GCC_OBJC_LEGACY_DISPATCH\=YES
    export GCC_OPTIMIZATION_LEVEL\=0
    export GCC_PFE_FILE_C_DIALECTS\=c\ objective-c\ c++\ objective-c++
    export GCC_PREPROCESSOR_DEFINITIONS\=\ DEBUG\=1
    export GCC_SYMBOLS_PRIVATE_EXTERN\=NO
    export GCC_TREAT_WARNINGS_AS_ERRORS\=NO
    export GCC_VERSION\=com.apple.compilers.llvm.clang.1_0
    export GCC_VERSION_IDENTIFIER\=com_apple_compilers_llvm_clang_1_0
    export GCC_WARN_64_TO_32_BIT_CONVERSION\=YES
    export GCC_WARN_ABOUT_RETURN_TYPE\=YES_ERROR
    export GCC_WARN_UNDECLARED_SELECTOR\=YES
    export GCC_WARN_UNINITIALIZED_AUTOS\=YES_AGGRESSIVE
    export GCC_WARN_UNUSED_FUNCTION\=YES
    export GCC_WARN_UNUSED_VARIABLE\=YES
    export GENERATED_MODULEMAP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/GeneratedModuleMaps-iphonesimulator
    export GENERATE_INFOPLIST_FILE\=NO
    export GENERATE_INTERMEDIATE_TEXT_BASED_STUBS\=YES
    export GENERATE_PKGINFO_FILE\=YES
    export GENERATE_PRELINK_OBJECT_FILE\=NO
    export GENERATE_PROFILING_CODE\=NO
    export GENERATE_TEXT_BASED_STUBS\=NO
    export GID\=20
    export GROUP\=staff
    export HEADERMAP_INCLUDES_FLAT_ENTRIES_FOR_TARGET_BEING_BUILT\=YES
    export HEADERMAP_INCLUDES_FRAMEWORK_ENTRIES_FOR_ALL_PRODUCT_TYPES\=YES
    export HEADERMAP_INCLUDES_FRAMEWORK_ENTRIES_FOR_TARGETS_NOT_BEING_BUILT\=YES
    export HEADERMAP_INCLUDES_NONPUBLIC_NONPRIVATE_HEADERS\=YES
    export HEADERMAP_INCLUDES_PROJECT_HEADERS\=YES
    export HEADERMAP_USES_FRAMEWORK_PREFIX_ENTRIES\=YES
    export HEADERMAP_USES_VFS\=NO
    export HEADER_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/include\ 
    export HOME\=/Users/mood
    export HOST_ARCH\=arm64
    export HOST_PLATFORM\=macosx
    export ICONV\=/usr/bin/iconv
    export IMPLICIT_DEPENDENCY_DOMAIN\=default
    export INDEX_STORE_COMPRESS\=NO
    export INDEX_STORE_ONLY_PROJECT_FILES\=NO
    export INFOPLIST_ENABLE_CFBUNDLEICONS_MERGE\=YES
    export INFOPLIST_EXPAND_BUILD_SETTINGS\=YES
    export INFOPLIST_FILE\=iosApp/Info.plist
    export INFOPLIST_OUTPUT_FORMAT\=binary
    export INFOPLIST_PATH\=iosApp.app/Info.plist
    export INFOPLIST_PREPROCESS\=NO
    export INFOSTRINGS_PATH\=iosApp.app/en.lproj/InfoPlist.strings
    export INLINE_PRIVATE_FRAMEWORKS\=NO
    export INSTALLAPI_IGNORE_SKIP_INSTALL\=YES
    export INSTALLHDRS_COPY_PHASE\=NO
    export INSTALLHDRS_SCRIPT_PHASE\=NO
    export INSTALL_DIR\=/tmp/iosApp.dst/Applications
    export INSTALL_GROUP\=staff
    export INSTALL_MODE_FLAG\=u+w,go-w,a+rX
    export INSTALL_OWNER\=mood
    export INSTALL_PATH\=/Applications
    export INSTALL_ROOT\=/tmp/iosApp.dst
    export IPHONEOS_DEPLOYMENT_TARGET\=18.0
    export IS_UNOPTIMIZED_BUILD\=YES
    export JAVAC_DEFAULT_FLAGS\=-J-Xms64m\ -J-XX:NewSize\=4M\ -J-Dfile.encoding\=UTF8
    export JAVA_APP_STUB\=/System/Library/Frameworks/JavaVM.framework/Resources/MacOS/JavaApplicationStub
    export JAVA_ARCHIVE_CLASSES\=YES
    export JAVA_ARCHIVE_TYPE\=JAR
    export JAVA_COMPILER\=/usr/bin/javac
    export JAVA_FOLDER_PATH\=iosApp.app/Java
    export JAVA_FRAMEWORK_RESOURCES_DIRS\=Resources
    export JAVA_JAR_FLAGS\=cv
    export JAVA_SOURCE_SUBDIR\=.
    export JAVA_USE_DEPENDENCIES\=YES
    export JAVA_ZIP_FLAGS\=-urg
    export JIKES_DEFAULT_FLAGS\=+E\ +OLDCSO
    export KEEP_PRIVATE_EXTERNS\=NO
    export LD_DEPENDENCY_INFO_FILE\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch/iosApp_dependency_info.dat
    export LD_ENTITLEMENTS_SECTION\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.app-Simulated.xcent
    export LD_ENTITLEMENTS_SECTION_DER\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.app-Simulated.xcent.der
    export LD_EXPORT_SYMBOLS\=YES
    export LD_GENERATE_MAP_FILE\=NO
    export LD_MAP_FILE_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-LinkMap-normal-undefined_arch.txt
    export LD_NO_PIE\=NO
    export LD_QUOTE_LINKER_ARGUMENTS_FOR_COMPILER_DRIVER\=YES
    export LD_RUNPATH_SEARCH_PATHS\=\ @executable_path/Frameworks
    export LD_RUNPATH_SEARCH_PATHS_YES\=@loader_path/../Frameworks
    export LD_SHARED_CACHE_ELIGIBLE\=Automatic
    export LD_WARN_DUPLICATE_LIBRARIES\=NO
    export LD_WARN_UNUSED_DYLIBS\=NO
    export LEGACY_DEVELOPER_DIR\=/Applications/Xcode.app/Contents/PlugIns/Xcode3Core.ideplugin/Contents/SharedSupport/Developer
    export LEX\=lex
    export LIBRARY_DEXT_INSTALL_PATH\=/Library/DriverExtensions
    export LIBRARY_FLAG_NOSPACE\=YES
    export LIBRARY_FLAG_PREFIX\=-l
    export LIBRARY_KEXT_INSTALL_PATH\=/Library/Extensions
    export LIBRARY_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator\ 
    export LINKER_DISPLAYS_MANGLED_NAMES\=NO
    export LINK_FILE_LIST_normal_arm64\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.LinkFileList
    export LINK_OBJC_RUNTIME\=YES
    export LINK_WITH_STANDARD_LIBRARIES\=YES
    export LLVM_TARGET_TRIPLE_OS_VERSION\=ios18.0
    export LLVM_TARGET_TRIPLE_SUFFIX\=-simulator
    export LLVM_TARGET_TRIPLE_VENDOR\=apple
    export LM_AUX_CONST_METADATA_LIST_PATH_normal_arm64\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftConstValuesFileList
    export LOCALIZATION_EXPORT_SUPPORTED\=YES
    export LOCALIZATION_PREFERS_STRING_CATALOGS\=NO
    export LOCALIZED_RESOURCES_FOLDER_PATH\=iosApp.app/en.lproj
    export LOCALIZED_STRING_CODE_COMMENTS\=NO
    export LOCALIZED_STRING_MACRO_NAMES\=NSLocalizedString\ CFCopyLocalizedString
    export LOCALIZED_STRING_SWIFTUI_SUPPORT\=YES
    export LOCAL_ADMIN_APPS_DIR\=/Applications/Utilities
    export LOCAL_APPS_DIR\=/Applications
    export LOCAL_DEVELOPER_DIR\=/Library/Developer
    export LOCAL_LIBRARY_DIR\=/Library
    export LOCROOT\=/Users/mood/POSE/android-poc/iosApp
    export LOCSYMROOT\=/Users/mood/POSE/android-poc/iosApp
    export MACH_O_TYPE\=mh_execute
    export MAC_OS_X_PRODUCT_BUILD_VERSION\=25F80
    export MAC_OS_X_VERSION_ACTUAL\=260501
    export MAC_OS_X_VERSION_MAJOR\=260000
    export MAC_OS_X_VERSION_MINOR\=260500
    export MAKE_MERGEABLE\=NO
    export MERGEABLE_LIBRARY\=NO
    export MERGED_BINARY_TYPE\=none
    export MERGE_LINKED_LIBRARIES\=NO
    export METAL_LIBRARY_FILE_BASE\=default
    export METAL_LIBRARY_OUTPUT_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app
    export MODULES_FOLDER_PATH\=iosApp.app/Modules
    export MODULE_CACHE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex
    export MTL_ENABLE_DEBUG_INFO\=INCLUDE_SOURCE
    export MTL_FAST_MATH\=YES
    export NATIVE_ARCH\=arm64
    export NATIVE_ARCH_32_BIT\=arm
    export NATIVE_ARCH_64_BIT\=arm64
    export NATIVE_ARCH_ACTUAL\=arm64
    export NO_COMMON\=YES
    export OBJC_ABI_VERSION\=2
    export OBJECT_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects
    export OBJECT_FILE_DIR_normal\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal
    export OBJROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex
    export ONLY_ACTIVE_ARCH\=YES
    export OS\=MACOS
    export OSAC\=/usr/bin/osacompile
    export OTHER_LDFLAGS\=\ -ObjC\ -framework\ MovitApp\ -lsqlite3
    export PACKAGE_TYPE\=com.apple.package-type.wrapper.application
    export PASCAL_STRINGS\=YES
    export PATH\=/Applications/Xcode.app/Contents/SharedFrameworks/SwiftBuild.framework/Versions/A/PlugIns/SWBBuildService.bundle/Contents/PlugIns/SWBUniversalPlatformPlugin.bundle/Contents/Frameworks/SWBUniversalPlatform.framework/Resources:/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin:/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/local/bin:/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/libexec:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/usr/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/usr/local/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/local/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
    export PATH_PREFIXES_EXCLUDED_FROM_HEADER_DEPENDENCIES\=/usr/include\ /usr/local/include\ /System/Library/Frameworks\ /System/Library/PrivateFrameworks\ /Applications/Xcode.app/Contents/Developer/Headers\ /Applications/Xcode.app/Contents/Developer/SDKs\ /Applications/Xcode.app/Contents/Developer/Platforms
    export PBDEVELOPMENTPLIST_PATH\=iosApp.app/pbdevelopment.plist
    export PER_ARCH_MODULE_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch
    export PER_ARCH_OBJECT_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch
    export PER_VARIANT_OBJECT_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal
    export PKGINFO_FILE_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/PkgInfo
    export PKGINFO_PATH\=iosApp.app/PkgInfo
    export PLATFORM_DEVELOPER_APPLICATIONS_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Applications
    export PLATFORM_DEVELOPER_BIN_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin
    export PLATFORM_DEVELOPER_LIBRARY_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Library
    export PLATFORM_DEVELOPER_SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs
    export PLATFORM_DEVELOPER_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Tools
    export PLATFORM_DEVELOPER_USR_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr
    export PLATFORM_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform
    export PLATFORM_DISPLAY_NAME\=iOS\ Simulator
    export PLATFORM_FAMILY_NAME\=iOS
    export PLATFORM_NAME\=iphonesimulator
    export PLATFORM_PREFERRED_ARCH\=x86_64
    export PLATFORM_PRODUCT_BUILD_VERSION\=23F73
    export PLATFORM_REQUIRES_SWIFT_AUTOLINK_EXTRACT\=NO
    export PLATFORM_REQUIRES_SWIFT_MODULEWRAP\=NO
    export PLATFORM_USES_DSYMS\=YES
    export PLIST_FILE_OUTPUT_FORMAT\=binary
    export PLUGINS_FOLDER_PATH\=iosApp.app/PlugIns
    export PRECOMPS_INCLUDE_HEADERS_FROM_BUILT_PRODUCTS_DIR\=YES
    export PRECOMP_DESTINATION_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/PrefixHeaders
    export PRIVATE_HEADERS_FOLDER_PATH\=iosApp.app/PrivateHeaders
    export PROCESSED_INFOPLIST_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch/Processed-Info.plist
    export PRODUCT_BUNDLE_IDENTIFIER\=com.movit.iosApp
    export PRODUCT_BUNDLE_PACKAGE_TYPE\=APPL
    export PRODUCT_MODULE_NAME\=iosApp
    export PRODUCT_NAME\=iosApp
    export PRODUCT_SETTINGS_PATH\=/Users/mood/POSE/android-poc/iosApp/iosApp/Info.plist
    export PRODUCT_TYPE\=com.apple.product-type.application
    export PROFILING_CODE\=NO
    export PROJECT\=iosApp
    export PROJECT_DERIVED_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/DerivedSources
    export PROJECT_DIR\=/Users/mood/POSE/android-poc/iosApp
    export PROJECT_FILE_PATH\=/Users/mood/POSE/android-poc/iosApp/iosApp.xcodeproj
    export PROJECT_GUID\=7b79493a8b7fc1f2fff3c51cd12ba024
    export PROJECT_NAME\=iosApp
    export PROJECT_TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build
    export PROJECT_TEMP_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex
    export PROVISIONING_PROFILE_REQUIRED\=NO
    export PROVISIONING_PROFILE_REQUIRED_YES_YES\=YES
    export PROVISIONING_PROFILE_SUPPORTED\=YES
    export PUBLIC_HEADERS_FOLDER_PATH\=iosApp.app/Headers
    export RECOMMENDED_IPHONEOS_DEPLOYMENT_TARGET\=15.0
    export RECURSIVE_SEARCH_PATHS_FOLLOW_SYMLINKS\=YES
    export REMOVE_CVS_FROM_RESOURCES\=YES
    export REMOVE_GIT_FROM_RESOURCES\=YES
    export REMOVE_HEADERS_FROM_EMBEDDED_BUNDLES\=YES
    export REMOVE_HG_FROM_RESOURCES\=YES
    export REMOVE_STATIC_EXECUTABLES_FROM_EMBEDDED_BUNDLES\=YES
    export REMOVE_SVN_FROM_RESOURCES\=YES
    export RESCHEDULE_INDEPENDENT_HEADERS_PHASES\=YES
    export REZ_COLLECTOR_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/ResourceManagerResources
    export REZ_OBJECTS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/ResourceManagerResources/Objects
    export REZ_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator\ 
    export RPATH_ORIGIN\=@loader_path
    export RUNTIME_EXCEPTION_ALLOW_DYLD_ENVIRONMENT_VARIABLES\=NO
    export RUNTIME_EXCEPTION_ALLOW_JIT\=NO
    export RUNTIME_EXCEPTION_ALLOW_UNSIGNED_EXECUTABLE_MEMORY\=NO
    export RUNTIME_EXCEPTION_DEBUGGING_TOOL\=NO
    export RUNTIME_EXCEPTION_DISABLE_EXECUTABLE_PAGE_PROTECTION\=NO
    export RUNTIME_EXCEPTION_DISABLE_LIBRARY_VALIDATION\=NO
    export SCANNING_PCM_KEEP_CACHE_DIRECTORY\=YES
    export SCAN_ALL_SOURCE_FILES_FOR_INCLUDES\=NO
    export SCRIPTS_FOLDER_PATH\=iosApp.app/Scripts
    export SCRIPT_INPUT_FILE_COUNT\=0
    export SCRIPT_INPUT_FILE_LIST_COUNT\=0
    export SCRIPT_OUTPUT_FILE_COUNT\=0
    export SCRIPT_OUTPUT_FILE_LIST_COUNT\=0
    export SDKROOT\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_DIR_iphonesimulator\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_DIR_iphonesimulator26_5\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_NAME\=iphonesimulator26.5
    export SDK_NAMES\=iphonesimulator26.5
    export SDK_PRODUCT_BUILD_VERSION\=23F73
    export SDK_STAT_CACHE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData
    export SDK_STAT_CACHE_ENABLE\=YES
    export SDK_STAT_CACHE_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache
    export SDK_VERSION\=26.5
    export SDK_VERSION_ACTUAL\=260500
    export SDK_VERSION_MAJOR\=260000
    export SDK_VERSION_MINOR\=260500
    export SED\=/usr/bin/sed
    export SEPARATE_STRIP\=NO
    export SEPARATE_SYMBOL_EDIT\=NO
    export SET_DIR_MODE_OWNER_GROUP\=YES
    export SET_FILE_MODE_OWNER_GROUP\=NO
    export SHALLOW_BUNDLE\=YES
    export SHALLOW_BUNDLE_TRIPLE\=ios-simulator
    export SHALLOW_BUNDLE_ios_macabi\=NO
    export SHALLOW_BUNDLE_macos\=NO
    export SHARED_DERIVED_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/DerivedSources
    export SHARED_FRAMEWORKS_FOLDER_PATH\=iosApp.app/SharedFrameworks
    export SHARED_PRECOMPS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/PrecompiledHeaders
    export SHARED_SUPPORT_FOLDER_PATH\=iosApp.app/SharedSupport
    export SKIP_INSTALL\=NO
    export SKIP_MERGEABLE_LIBRARY_BUNDLE_HOOK\=NO
    export SOURCE_ROOT\=/Users/mood/POSE/android-poc/iosApp
    export SRCROOT\=/Users/mood/POSE/android-poc/iosApp
    export STRINGSDATA_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch
    export STRINGSDATA_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export STRINGS_FILE_INFOPLIST_RENAME\=YES
    export STRINGS_FILE_OUTPUT_ENCODING\=binary
    export STRING_CATALOG_GENERATE_SYMBOLS\=NO
    export STRIP_BITCODE_FROM_COPIED_FILES\=NO
    export STRIP_INSTALLED_PRODUCT\=NO
    export STRIP_STYLE\=all
    export STRIP_SWIFT_SYMBOLS\=YES
    export SUPPORTED_DEVICE_FAMILIES\=1,2
    export SUPPORTED_PLATFORMS\=iphoneos\ iphonesimulator
    export SUPPORTS_MACCATALYST\=NO
    export SUPPORTS_ON_DEMAND_RESOURCES\=YES
    export SUPPORTS_TEXT_BASED_API\=NO
    export SUPPRESS_WARNINGS\=NO
    export SWIFT_ACTIVE_COMPILATION_CONDITIONS\=DEBUG
    export SWIFT_EMIT_CONST_VALUE_PROTOCOLS\=AnyResolverProviding\ AppEntity\ AppEnum\ AppExtension\ AppIntent\ AppIntentsPackage\ AppShortcutProviding\ AppShortcutsProvider\ AppUnionValue\ AppUnionValueCasesProviding\ DynamicOptionsProvider\ EntityQuery\ ExtensionPointDefining\ IntentValueQuery\ Resolver\ TransientEntity\ _AssistantIntentsProvider\ _GenerativeFunctionExtractable\ _IntentValueRepresentable
    export SWIFT_EMIT_LOC_STRINGS\=NO
    export SWIFT_ENABLE_EXPLICIT_MODULES\=YES
    export SWIFT_OPTIMIZATION_LEVEL\=-Onone
    export SWIFT_PLATFORM_TARGET_PREFIX\=ios
    export SWIFT_RESPONSE_FILE_PATH_normal_arm64\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftFileList
    export SWIFT_VERSION\=5.0
    export SYMROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products
    export SYSTEM_ADMIN_APPS_DIR\=/Applications/Utilities
    export SYSTEM_APPS_DIR\=/Applications
    export SYSTEM_CORE_SERVICES_DIR\=/System/Library/CoreServices
    export SYSTEM_DEMOS_DIR\=/Applications/Extras
    export SYSTEM_DEVELOPER_APPS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications
    export SYSTEM_DEVELOPER_BIN_DIR\=/Applications/Xcode.app/Contents/Developer/usr/bin
    export SYSTEM_DEVELOPER_DEMOS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Utilities/Built\ Examples
    export SYSTEM_DEVELOPER_DIR\=/Applications/Xcode.app/Contents/Developer
    export SYSTEM_DEVELOPER_DOC_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library
    export SYSTEM_DEVELOPER_GRAPHICS_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Graphics\ Tools
    export SYSTEM_DEVELOPER_JAVA_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Java\ Tools
    export SYSTEM_DEVELOPER_PERFORMANCE_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Performance\ Tools
    export SYSTEM_DEVELOPER_RELEASENOTES_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library/releasenotes
    export SYSTEM_DEVELOPER_TOOLS\=/Applications/Xcode.app/Contents/Developer/Tools
    export SYSTEM_DEVELOPER_TOOLS_DOC_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library/documentation/DeveloperTools
    export SYSTEM_DEVELOPER_TOOLS_RELEASENOTES_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library/releasenotes/DeveloperTools
    export SYSTEM_DEVELOPER_USR_DIR\=/Applications/Xcode.app/Contents/Developer/usr
    export SYSTEM_DEVELOPER_UTILITIES_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Utilities
    export SYSTEM_DEXT_INSTALL_PATH\=/System/Library/DriverExtensions
    export SYSTEM_DOCUMENTATION_DIR\=/Library/Documentation
    export SYSTEM_EXTENSIONS_FOLDER_PATH\=iosApp.app/SystemExtensions
    export SYSTEM_EXTENSIONS_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/Library/SystemExtensions
    export SYSTEM_EXTENSIONS_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app/SystemExtensions
    export SYSTEM_KEXT_INSTALL_PATH\=/System/Library/Extensions
    export SYSTEM_LIBRARY_DIR\=/System/Library
    export TAPI_DEMANGLE\=YES
    export TAPI_ENABLE_PROJECT_HEADERS\=NO
    export TAPI_LANGUAGE\=objective-c
    export TAPI_LANGUAGE_STANDARD\=compiler-default
    export TAPI_USE_SRCROOT\=YES
    export TAPI_VERIFY_MODE\=Pedantic
    export TARGETED_DEVICE_FAMILY\=1,2
    export TARGETNAME\=iosApp
    export TARGET_BUILD_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export TARGET_DEVICE_IDENTIFIER\=31C834BE-271E-46D7-A53C-5338368FBF89
    export TARGET_DEVICE_MODEL\=iPhone18,1
    export TARGET_DEVICE_OS_VERSION\=26.5
    export TARGET_DEVICE_PLATFORM_NAME\=iphonesimulator
    export TARGET_NAME\=iosApp
    export TARGET_TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_FILES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex
    export TEMP_SANDBOX_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/TemporaryTaskSandboxes
    export TEST_FRAMEWORK_SEARCH_PATHS\=\ /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Library/Frameworks\ /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/Developer/Library/Frameworks
    export TEST_LIBRARY_SEARCH_PATHS\=\ /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/lib
    export TOOLCHAINS\=com.apple.dt.toolchain.XcodeDefault
    export TOOLCHAIN_DIR\=/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain
    export TREAT_MISSING_BASELINES_AS_TEST_FAILURES\=NO
    export TREAT_MISSING_SCRIPT_PHASE_OUTPUTS_AS_ERRORS\=NO
    export TeamIdentifierPrefix\=S2QF6L6CJZ.
    export UID\=501
    export UNINSTALLED_PRODUCTS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/UninstalledProducts
    export UNLOCALIZED_RESOURCES_FOLDER_PATH\=iosApp.app
    export UNLOCALIZED_RESOURCES_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/Resources
    export UNLOCALIZED_RESOURCES_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app
    export UNSTRIPPED_PRODUCT\=NO
    export USER\=mood
    export USER_APPS_DIR\=/Users/mood/Applications
    export USER_LIBRARY_DIR\=/Users/mood/Library
    export USE_DYNAMIC_NO_PIC\=YES
    export USE_HEADERMAP\=YES
    export USE_HEADER_SYMLINKS\=NO
    export VALIDATE_DEVELOPMENT_ASSET_PATHS\=YES_ERROR
    export VALIDATE_PRODUCT\=NO
    export VALID_ARCHS\=arm64\ x86_64
    export VERBOSE_PBXCP\=NO
    export VERSIONPLIST_PATH\=iosApp.app/version.plist
    export VERSION_INFO_BUILDER\=mood
    export VERSION_INFO_FILE\=iosApp_vers.c
    export VERSION_INFO_STRING\=\"@\(\#\)PROGRAM:iosApp\ \ PROJECT:iosApp-\"
    export WORKSPACE_DIR\=/Users/mood/POSE/android-poc/iosApp
    export WRAPPER_EXTENSION\=app
    export WRAPPER_NAME\=iosApp.app
    export WRAPPER_SUFFIX\=.app
    export WRAP_ASSET_PACKS_IN_SEPARATE_DIRECTORIES\=NO
    export XCODE_APP_SUPPORT_DIR\=/Applications/Xcode.app/Contents/Developer/Library/Xcode
    export XCODE_PRODUCT_BUILD_VERSION\=17F42
    export XCODE_VERSION_ACTUAL\=2650
    export XCODE_VERSION_MAJOR\=2600
    export XCODE_VERSION_MINOR\=2650
    export XPCSERVICES_FOLDER_PATH\=iosApp.app/XPCServices
    export YACC\=yacc
    export _DISCOVER_COMMAND_LINE_LINKER_INPUTS\=YES
    export _DISCOVER_COMMAND_LINE_LINKER_INPUTS_INCLUDE_WL\=YES
    export _LD_MULTIARCH\=YES
    export _WRAPPER_CONTENTS_DIR_SHALLOW_BUNDLE_NO\=/Contents
    export _WRAPPER_PARENT_PATH_SHALLOW_BUNDLE_NO\=/..
    export _WRAPPER_RESOURCES_DIR_SHALLOW_BUNDLE_NO\=/Resources
    export __DIAGNOSE_DEPRECATED_ARCHS\=YES
    export __IS_NOT_MACOS\=YES
    export __IS_NOT_MACOS_macosx\=NO
    export __IS_NOT_SIMULATOR\=NO
    export __IS_NOT_SIMULATOR_simulator\=NO
    export __ORIGINAL_SDK_DEFINED_LLVM_TARGET_TRIPLE_SYS\=ios
    export arch\=undefined_arch
    export variant\=normal
    /bin/sh -c /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Script-AF82C982105A73DD73455872.sh

PhaseScriptExecution Ensure\ pose_landmarker\ model\ in\ bundle /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Script-49A0C7495C36D488C55314C3.sh (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    export ACTION\=build
    export AD_HOC_CODE_SIGNING_ALLOWED\=YES
    export AGGREGATE_TRACKED_DOMAINS\=YES
    export ALLOW_BUILD_REQUEST_OVERRIDES\=NO
    export ALLOW_TARGET_PLATFORM_SPECIALIZATION\=NO
    export ALTERNATE_GROUP\=staff
    export ALTERNATE_MODE\=u+w,go-w,a+rX
    export ALTERNATE_OWNER\=mood
    export ALTERNATIVE_DISTRIBUTION_WEB\=NO
    export ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES\=NO
    export ALWAYS_SEARCH_USER_PATHS\=NO
    export ALWAYS_USE_SEPARATE_HEADERMAPS\=NO
    export APPLICATION_EXTENSION_API_ONLY\=NO
    export APPLY_RULES_IN_COPY_FILES\=NO
    export APPLY_RULES_IN_COPY_HEADERS\=NO
    export APP_SHORTCUTS_ENABLE_FLEXIBLE_MATCHING\=YES
    export ARCHS\=arm64
    export ARCHS_BASE\=arm64
    export ARCHS_STANDARD\=arm64\ x86_64
    export ARCHS_STANDARD_32_64_BIT\=arm64\ x86_64
    export ARCHS_STANDARD_64_BIT\=arm64\ x86_64
    export ARCHS_STANDARD_INCLUDING_64_BIT\=arm64\ x86_64
    export ARCHS_UNIVERSAL_IPHONE_OS\=arm64\ x86_64
    export ASSETCATALOG_COMPILER_APPICON_NAME\=AppIcon
    export ASSETCATALOG_FILTER_FOR_DEVICE_MODEL\=iPhone18,1
    export ASSETCATALOG_FILTER_FOR_DEVICE_OS_VERSION\=26.5
    export ASSETCATALOG_FILTER_FOR_THINNING_DEVICE_CONFIGURATION\=iPhone18,1
    export AUTOMATICALLY_MERGE_DEPENDENCIES\=NO
    export AUTOMATION_APPLE_EVENTS\=NO
    export AVAILABLE_PLATFORMS\=android\ appletvos\ appletvsimulator\ driverkit\ freebsd\ iphoneos\ iphonesimulator\ linux\ macosx\ none\ openbsd\ qnx\ watchos\ watchsimulator\ webassembly\ xros\ xrsimulator
    export AppIdentifierPrefix\=S2QF6L6CJZ.
    export BUILD_ACTIVE_RESOURCES_ONLY\=YES
    export BUILD_COMPONENTS\=headers\ build
    export BUILD_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products
    export BUILD_LIBRARY_FOR_DISTRIBUTION\=NO
    export BUILD_ONLY_KNOWN_LOCALIZATIONS\=NO
    export BUILD_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products
    export BUILD_STYLE\=
    export BUILD_VARIANTS\=normal
    export BUILT_PRODUCTS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export BUNDLE_CONTENTS_FOLDER_PATH_deep\=Contents/
    export BUNDLE_EXECUTABLE_FOLDER_NAME_deep\=MacOS
    export BUNDLE_EXTENSIONS_FOLDER_PATH\=Extensions
    export BUNDLE_FORMAT\=shallow
    export BUNDLE_FRAMEWORKS_FOLDER_PATH\=Frameworks
    export BUNDLE_PLUGINS_FOLDER_PATH\=PlugIns
    export BUNDLE_PRIVATE_HEADERS_FOLDER_PATH\=PrivateHeaders
    export BUNDLE_PUBLIC_HEADERS_FOLDER_PATH\=Headers
    export CACHE_ROOT\=/var/folders/xz/9yr8chc564x0w_mj7j122k6m0000gn/C/com.apple.DeveloperTools/26.5-17F42/Xcode
    export CCHROOT\=/var/folders/xz/9yr8chc564x0w_mj7j122k6m0000gn/C/com.apple.DeveloperTools/26.5-17F42/Xcode
    export CHMOD\=/bin/chmod
    export CHOWN\=chown
    export CLANG_ANALYZER_NONNULL\=YES
    export CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION\=YES_AGGRESSIVE
    export CLANG_CACHE_FINE_GRAINED_OUTPUTS\=YES
    export CLANG_CXX_LANGUAGE_STANDARD\=gnu++14
    export CLANG_CXX_LIBRARY\=libc++
    export CLANG_ENABLE_EXPLICIT_MODULES\=YES
    export CLANG_ENABLE_MODULES\=YES
    export CLANG_ENABLE_OBJC_ARC\=YES
    export CLANG_ENABLE_OBJC_WEAK\=YES
    export CLANG_MODULES_BUILD_SESSION_FILE\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation
    export CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING\=YES
    export CLANG_WARN_BOOL_CONVERSION\=YES
    export CLANG_WARN_COMMA\=YES
    export CLANG_WARN_CONSTANT_CONVERSION\=YES
    export CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS\=YES
    export CLANG_WARN_DIRECT_OBJC_ISA_USAGE\=YES_ERROR
    export CLANG_WARN_DOCUMENTATION_COMMENTS\=YES
    export CLANG_WARN_EMPTY_BODY\=YES
    export CLANG_WARN_ENUM_CONVERSION\=YES
    export CLANG_WARN_INFINITE_RECURSION\=YES
    export CLANG_WARN_INT_CONVERSION\=YES
    export CLANG_WARN_NON_LITERAL_NULL_CONVERSION\=YES
    export CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF\=YES
    export CLANG_WARN_OBJC_LITERAL_CONVERSION\=YES
    export CLANG_WARN_OBJC_ROOT_CLASS\=YES_ERROR
    export CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER\=YES
    export CLANG_WARN_RANGE_LOOP_ANALYSIS\=YES
    export CLANG_WARN_STRICT_PROTOTYPES\=YES
    export CLANG_WARN_SUSPICIOUS_MOVE\=YES
    export CLANG_WARN_UNGUARDED_AVAILABILITY\=YES_AGGRESSIVE
    export CLANG_WARN_UNREACHABLE_CODE\=YES
    export CLANG_WARN__DUPLICATE_METHOD_MATCH\=YES
    export CLASS_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/JavaClasses
    export CLEAN_PRECOMPS\=YES
    export CLONE_HEADERS\=NO
    export CODESIGNING_FOLDER_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app
    export CODE_SIGNING_ALLOWED\=YES
    export CODE_SIGNING_REQUIRED\=YES
    export CODE_SIGN_CONTEXT_CLASS\=XCiPhoneSimulatorCodeSignContext
    export CODE_SIGN_ENTITLEMENTS\=iosApp/iosApp.entitlements
    export CODE_SIGN_IDENTITY\=iPhone\ Developer
    export CODE_SIGN_INJECT_BASE_ENTITLEMENTS\=YES
    export COLOR_DIAGNOSTICS\=NO
    export COMBINE_HIDPI_IMAGES\=NO
    export COMPILATION_CACHE_CAS_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/CompilationCache.noindex
    export COMPILATION_CACHE_KEEP_CAS_DIRECTORY\=YES
    export COMPILER_INDEX_STORE_ENABLE\=Default
    export COMPOSITE_SDK_DIRS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/CompositeSDKs
    export COMPRESS_PNG_FILES\=YES
    export CONFIGURATION\=Debug
    export CONFIGURATION_BUILD_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export CONFIGURATION_TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator
    export CONTENTS_FOLDER_PATH\=iosApp.app
    export CONTENTS_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/Contents
    export CONTENTS_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app
    export COPYING_PRESERVES_HFS_DATA\=NO
    export COPY_HEADERS_RUN_UNIFDEF\=NO
    export COPY_PHASE_STRIP\=NO
    export CORRESPONDING_DEVICE_PLATFORM_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform
    export CORRESPONDING_DEVICE_PLATFORM_NAME\=iphoneos
    export CORRESPONDING_DEVICE_SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS26.5.sdk
    export CORRESPONDING_DEVICE_SDK_NAME\=iphoneos26.5
    export CP\=/bin/cp
    export CREATE_INFOPLIST_SECTION_IN_BINARY\=NO
    export CURRENT_ARCH\=undefined_arch
    export CURRENT_VARIANT\=normal
    export DEAD_CODE_STRIPPING\=YES
    export DEBUGGING_SYMBOLS\=YES
    export DEBUG_INFORMATION_FORMAT\=dwarf
    export DEBUG_INFORMATION_VERSION\=compiler-default
    export DEFAULT_COMPILER\=com.apple.compilers.llvm.clang.1_0
    export DEFAULT_DEXT_INSTALL_PATH\=/System/Library/DriverExtensions
    export DEFAULT_KEXT_INSTALL_PATH\=/System/Library/Extensions
    export DEFINES_MODULE\=NO
    export DEPLOYMENT_LOCATION\=NO
    export DEPLOYMENT_POSTPROCESSING\=NO
    export DEPLOYMENT_TARGET_SETTING_NAME\=IPHONEOS_DEPLOYMENT_TARGET
    export DEPLOYMENT_TARGET_SUGGESTED_VALUES\=12.0\ 12.1\ 12.2\ 12.3\ 12.4\ 13.0\ 13.1\ 13.2\ 13.3\ 13.4\ 13.5\ 13.6\ 14.0\ 14.1\ 14.2\ 14.3\ 14.4\ 14.5\ 14.6\ 14.7\ 15.0\ 15.1\ 15.2\ 15.3\ 15.4\ 15.5\ 15.6\ 16.0\ 16.1\ 16.2\ 16.3\ 16.4\ 16.5\ 16.6\ 17.0\ 17.1\ 17.2\ 17.3\ 17.4\ 17.5\ 17.6\ 18.0\ 18.1\ 18.2\ 18.3\ 18.4\ 18.5\ 18.6\ 26.0\ 26.2\ 26.3\ 26.4\ 26.5
    export DERIVED_FILES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources
    export DERIVED_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources
    export DERIVED_SOURCES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources
    export DEVELOPER_APPLICATIONS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications
    export DEVELOPER_BIN_DIR\=/Applications/Xcode.app/Contents/Developer/usr/bin
    export DEVELOPER_DIR\=/Applications/Xcode.app/Contents/Developer
    export DEVELOPER_FRAMEWORKS_DIR\=/Applications/Xcode.app/Contents/Developer/Library/Frameworks
    export DEVELOPER_FRAMEWORKS_DIR_QUOTED\=/Applications/Xcode.app/Contents/Developer/Library/Frameworks
    export DEVELOPER_LIBRARY_DIR\=/Applications/Xcode.app/Contents/Developer/Library
    export DEVELOPER_SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs
    export DEVELOPER_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Tools
    export DEVELOPER_USR_DIR\=/Applications/Xcode.app/Contents/Developer/usr
    export DEVELOPMENT_LANGUAGE\=en
    export DIAGNOSE_MISSING_TARGET_DEPENDENCIES\=YES
    export DIFF\=/usr/bin/diff
    export DOCUMENTATION_FOLDER_PATH\=iosApp.app/en.lproj/Documentation
    export DONT_GENERATE_INFOPLIST_FILE\=NO
    export DSTROOT\=/tmp/iosApp.dst
    export DT_TOOLCHAIN_DIR\=/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain
    export DUMP_DEPENDENCIES\=NO
    export DUMP_DEPENDENCIES_OUTPUT_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-BuildDependencyInfo.json
    export DWARF_DSYM_FILE_NAME\=iosApp.app.dSYM
    export DWARF_DSYM_FILE_SHOULD_ACCOMPANY_PRODUCT\=NO
    export DWARF_DSYM_FOLDER_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export DYNAMIC_LIBRARY_EXTENSION\=dylib
    export EAGER_COMPILATION_ALLOW_SCRIPTS\=NO
    export EAGER_LINKING\=NO
    export EFFECTIVE_PLATFORM_NAME\=-iphonesimulator
    export EFFECTIVE_SWIFT_VERSION\=5
    export EMBEDDED_CONTENT_CONTAINS_SWIFT\=NO
    export EMBED_ASSET_PACKS_IN_PRODUCT_BUNDLE\=NO
    export ENABLE_APP_SANDBOX\=NO
    export ENABLE_CODE_COVERAGE\=YES
    export ENABLE_COHORT_ARCHS\=NO
    export ENABLE_CPLUSPLUS_BOUNDS_SAFE_BUFFERS\=NO
    export ENABLE_C_BOUNDS_SAFETY\=NO
    export ENABLE_DEBUG_DYLIB\=YES
    export ENABLE_DEFAULT_HEADER_SEARCH_PATHS\=YES
    export ENABLE_DEFAULT_SEARCH_PATHS\=YES
    export ENABLE_ENHANCED_SECURITY\=NO
    export ENABLE_HARDENED_RUNTIME\=NO
    export ENABLE_HEADER_DEPENDENCIES\=YES
    export ENABLE_INCOMING_NETWORK_CONNECTIONS\=NO
    export ENABLE_ON_DEMAND_RESOURCES\=YES
    export ENABLE_OUTGOING_NETWORK_CONNECTIONS\=NO
    export ENABLE_POINTER_AUTHENTICATION\=NO
    export ENABLE_PREVIEWS\=NO
    export ENABLE_RESOURCE_ACCESS_AUDIO_INPUT\=NO
    export ENABLE_RESOURCE_ACCESS_BLUETOOTH\=NO
    export ENABLE_RESOURCE_ACCESS_CALENDARS\=NO
    export ENABLE_RESOURCE_ACCESS_CAMERA\=NO
    export ENABLE_RESOURCE_ACCESS_CONTACTS\=NO
    export ENABLE_RESOURCE_ACCESS_LOCATION\=NO
    export ENABLE_RESOURCE_ACCESS_PHOTO_LIBRARY\=NO
    export ENABLE_RESOURCE_ACCESS_PRINTING\=NO
    export ENABLE_RESOURCE_ACCESS_USB\=NO
    export ENABLE_SDK_IMPORTS\=NO
    export ENABLE_SECURITY_COMPILER_WARNINGS\=NO
    export ENABLE_STRICT_OBJC_MSGSEND\=YES
    export ENABLE_TESTABILITY\=YES
    export ENABLE_TESTING_SEARCH_PATHS\=NO
    export ENABLE_USER_SCRIPT_SANDBOXING\=NO
    export ENABLE_XOJIT_PREVIEWS\=YES
    export ENFORCE_VALID_ARCHS\=YES
    export ENTITLEMENTS_ALLOWED\=NO
    export ENTITLEMENTS_DESTINATION\=__entitlements
    export ENTITLEMENTS_REQUIRED\=NO
    export EXCLUDED_ARCHS\=x86_64
    export EXCLUDED_INSTALLSRC_SUBDIRECTORY_PATTERNS\=.DS_Store\ .svn\ .git\ .hg\ CVS
    export EXCLUDED_RECURSIVE_SEARCH_PATH_SUBDIRECTORIES\=\*.nib\ \*.lproj\ \*.framework\ \*.gch\ \*.xcode\*\ \*.xcassets\ \*.icon\ \(\*\)\ .DS_Store\ CVS\ .svn\ .git\ .hg\ \*.pbproj\ \*.pbxproj
    export EXECUTABLES_FOLDER_PATH\=iosApp.app/Executables
    export EXECUTABLE_BLANK_INJECTION_DYLIB_PATH\=iosApp.app/__preview.dylib
    export EXECUTABLE_DEBUG_DYLIB_INSTALL_NAME\=@rpath/iosApp.debug.dylib
    export EXECUTABLE_DEBUG_DYLIB_PATH\=iosApp.app/iosApp.debug.dylib
    export EXECUTABLE_FOLDER_PATH\=iosApp.app
    export EXECUTABLE_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/MacOS
    export EXECUTABLE_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app
    export EXECUTABLE_NAME\=iosApp
    export EXECUTABLE_PATH\=iosApp.app/iosApp
    export EXPANDED_CODE_SIGN_IDENTITY\=-
    export EXPANDED_CODE_SIGN_IDENTITY_NAME\=Sign\ to\ Run\ Locally
    export EXTENSIONS_FOLDER_PATH\=iosApp.app/Extensions
    export FILE_LIST\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects/LinkFileList
    export FIXED_FILES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/FixedFiles
    export FRAMEWORKS_FOLDER_PATH\=iosApp.app/Frameworks
    export FRAMEWORK_FLAG_PREFIX\=-framework
    export FRAMEWORK_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator\ \ /Users/mood/POSE/android-poc/iosApp/../feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5
    export FRAMEWORK_VERSION\=A
    export FULL_PRODUCT_NAME\=iosApp.app
    export FUSE_BUILD_PHASES\=YES
    export FUSE_BUILD_SCRIPT_PHASES\=NO
    export GCC3_VERSION\=3.3
    export GCC_C_LANGUAGE_STANDARD\=gnu11
    export GCC_DYNAMIC_NO_PIC\=NO
    export GCC_INLINES_ARE_PRIVATE_EXTERN\=YES
    export GCC_NO_COMMON_BLOCKS\=YES
    export GCC_OBJC_LEGACY_DISPATCH\=YES
    export GCC_OPTIMIZATION_LEVEL\=0
    export GCC_PFE_FILE_C_DIALECTS\=c\ objective-c\ c++\ objective-c++
    export GCC_PREPROCESSOR_DEFINITIONS\=\ DEBUG\=1
    export GCC_SYMBOLS_PRIVATE_EXTERN\=NO
    export GCC_TREAT_WARNINGS_AS_ERRORS\=NO
    export GCC_VERSION\=com.apple.compilers.llvm.clang.1_0
    export GCC_VERSION_IDENTIFIER\=com_apple_compilers_llvm_clang_1_0
    export GCC_WARN_64_TO_32_BIT_CONVERSION\=YES
    export GCC_WARN_ABOUT_RETURN_TYPE\=YES_ERROR
    export GCC_WARN_UNDECLARED_SELECTOR\=YES
    export GCC_WARN_UNINITIALIZED_AUTOS\=YES_AGGRESSIVE
    export GCC_WARN_UNUSED_FUNCTION\=YES
    export GCC_WARN_UNUSED_VARIABLE\=YES
    export GENERATED_MODULEMAP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/GeneratedModuleMaps-iphonesimulator
    export GENERATE_INFOPLIST_FILE\=NO
    export GENERATE_INTERMEDIATE_TEXT_BASED_STUBS\=YES
    export GENERATE_PKGINFO_FILE\=YES
    export GENERATE_PRELINK_OBJECT_FILE\=NO
    export GENERATE_PROFILING_CODE\=NO
    export GENERATE_TEXT_BASED_STUBS\=NO
    export GID\=20
    export GROUP\=staff
    export HEADERMAP_INCLUDES_FLAT_ENTRIES_FOR_TARGET_BEING_BUILT\=YES
    export HEADERMAP_INCLUDES_FRAMEWORK_ENTRIES_FOR_ALL_PRODUCT_TYPES\=YES
    export HEADERMAP_INCLUDES_FRAMEWORK_ENTRIES_FOR_TARGETS_NOT_BEING_BUILT\=YES
    export HEADERMAP_INCLUDES_NONPUBLIC_NONPRIVATE_HEADERS\=YES
    export HEADERMAP_INCLUDES_PROJECT_HEADERS\=YES
    export HEADERMAP_USES_FRAMEWORK_PREFIX_ENTRIES\=YES
    export HEADERMAP_USES_VFS\=NO
    export HEADER_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/include\ 
    export HOME\=/Users/mood
    export HOST_ARCH\=arm64
    export HOST_PLATFORM\=macosx
    export ICONV\=/usr/bin/iconv
    export IMPLICIT_DEPENDENCY_DOMAIN\=default
    export INDEX_STORE_COMPRESS\=NO
    export INDEX_STORE_ONLY_PROJECT_FILES\=NO
    export INFOPLIST_ENABLE_CFBUNDLEICONS_MERGE\=YES
    export INFOPLIST_EXPAND_BUILD_SETTINGS\=YES
    export INFOPLIST_FILE\=iosApp/Info.plist
    export INFOPLIST_OUTPUT_FORMAT\=binary
    export INFOPLIST_PATH\=iosApp.app/Info.plist
    export INFOPLIST_PREPROCESS\=NO
    export INFOSTRINGS_PATH\=iosApp.app/en.lproj/InfoPlist.strings
    export INLINE_PRIVATE_FRAMEWORKS\=NO
    export INSTALLAPI_IGNORE_SKIP_INSTALL\=YES
    export INSTALLHDRS_COPY_PHASE\=NO
    export INSTALLHDRS_SCRIPT_PHASE\=NO
    export INSTALL_DIR\=/tmp/iosApp.dst/Applications
    export INSTALL_GROUP\=staff
    export INSTALL_MODE_FLAG\=u+w,go-w,a+rX
    export INSTALL_OWNER\=mood
    export INSTALL_PATH\=/Applications
    export INSTALL_ROOT\=/tmp/iosApp.dst
    export IPHONEOS_DEPLOYMENT_TARGET\=18.0
    export IS_UNOPTIMIZED_BUILD\=YES
    export JAVAC_DEFAULT_FLAGS\=-J-Xms64m\ -J-XX:NewSize\=4M\ -J-Dfile.encoding\=UTF8
    export JAVA_APP_STUB\=/System/Library/Frameworks/JavaVM.framework/Resources/MacOS/JavaApplicationStub
    export JAVA_ARCHIVE_CLASSES\=YES
    export JAVA_ARCHIVE_TYPE\=JAR
    export JAVA_COMPILER\=/usr/bin/javac
    export JAVA_FOLDER_PATH\=iosApp.app/Java
    export JAVA_FRAMEWORK_RESOURCES_DIRS\=Resources
    export JAVA_JAR_FLAGS\=cv
    export JAVA_SOURCE_SUBDIR\=.
    export JAVA_USE_DEPENDENCIES\=YES
    export JAVA_ZIP_FLAGS\=-urg
    export JIKES_DEFAULT_FLAGS\=+E\ +OLDCSO
    export KEEP_PRIVATE_EXTERNS\=NO
    export LD_DEPENDENCY_INFO_FILE\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch/iosApp_dependency_info.dat
    export LD_ENTITLEMENTS_SECTION\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.app-Simulated.xcent
    export LD_ENTITLEMENTS_SECTION_DER\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.app-Simulated.xcent.der
    export LD_EXPORT_SYMBOLS\=YES
    export LD_GENERATE_MAP_FILE\=NO
    export LD_MAP_FILE_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-LinkMap-normal-undefined_arch.txt
    export LD_NO_PIE\=NO
    export LD_QUOTE_LINKER_ARGUMENTS_FOR_COMPILER_DRIVER\=YES
    export LD_RUNPATH_SEARCH_PATHS\=\ @executable_path/Frameworks
    export LD_RUNPATH_SEARCH_PATHS_YES\=@loader_path/../Frameworks
    export LD_SHARED_CACHE_ELIGIBLE\=Automatic
    export LD_WARN_DUPLICATE_LIBRARIES\=NO
    export LD_WARN_UNUSED_DYLIBS\=NO
    export LEGACY_DEVELOPER_DIR\=/Applications/Xcode.app/Contents/PlugIns/Xcode3Core.ideplugin/Contents/SharedSupport/Developer
    export LEX\=lex
    export LIBRARY_DEXT_INSTALL_PATH\=/Library/DriverExtensions
    export LIBRARY_FLAG_NOSPACE\=YES
    export LIBRARY_FLAG_PREFIX\=-l
    export LIBRARY_KEXT_INSTALL_PATH\=/Library/Extensions
    export LIBRARY_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator\ 
    export LINKER_DISPLAYS_MANGLED_NAMES\=NO
    export LINK_FILE_LIST_normal_arm64\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.LinkFileList
    export LINK_OBJC_RUNTIME\=YES
    export LINK_WITH_STANDARD_LIBRARIES\=YES
    export LLVM_TARGET_TRIPLE_OS_VERSION\=ios18.0
    export LLVM_TARGET_TRIPLE_SUFFIX\=-simulator
    export LLVM_TARGET_TRIPLE_VENDOR\=apple
    export LM_AUX_CONST_METADATA_LIST_PATH_normal_arm64\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftConstValuesFileList
    export LOCALIZATION_EXPORT_SUPPORTED\=YES
    export LOCALIZATION_PREFERS_STRING_CATALOGS\=NO
    export LOCALIZED_RESOURCES_FOLDER_PATH\=iosApp.app/en.lproj
    export LOCALIZED_STRING_CODE_COMMENTS\=NO
    export LOCALIZED_STRING_MACRO_NAMES\=NSLocalizedString\ CFCopyLocalizedString
    export LOCALIZED_STRING_SWIFTUI_SUPPORT\=YES
    export LOCAL_ADMIN_APPS_DIR\=/Applications/Utilities
    export LOCAL_APPS_DIR\=/Applications
    export LOCAL_DEVELOPER_DIR\=/Library/Developer
    export LOCAL_LIBRARY_DIR\=/Library
    export LOCROOT\=/Users/mood/POSE/android-poc/iosApp
    export LOCSYMROOT\=/Users/mood/POSE/android-poc/iosApp
    export MACH_O_TYPE\=mh_execute
    export MAC_OS_X_PRODUCT_BUILD_VERSION\=25F80
    export MAC_OS_X_VERSION_ACTUAL\=260501
    export MAC_OS_X_VERSION_MAJOR\=260000
    export MAC_OS_X_VERSION_MINOR\=260500
    export MAKE_MERGEABLE\=NO
    export MERGEABLE_LIBRARY\=NO
    export MERGED_BINARY_TYPE\=none
    export MERGE_LINKED_LIBRARIES\=NO
    export METAL_LIBRARY_FILE_BASE\=default
    export METAL_LIBRARY_OUTPUT_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app
    export MODULES_FOLDER_PATH\=iosApp.app/Modules
    export MODULE_CACHE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex
    export MTL_ENABLE_DEBUG_INFO\=INCLUDE_SOURCE
    export MTL_FAST_MATH\=YES
    export NATIVE_ARCH\=arm64
    export NATIVE_ARCH_32_BIT\=arm
    export NATIVE_ARCH_64_BIT\=arm64
    export NATIVE_ARCH_ACTUAL\=arm64
    export NO_COMMON\=YES
    export OBJC_ABI_VERSION\=2
    export OBJECT_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects
    export OBJECT_FILE_DIR_normal\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal
    export OBJROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex
    export ONLY_ACTIVE_ARCH\=YES
    export OS\=MACOS
    export OSAC\=/usr/bin/osacompile
    export OTHER_LDFLAGS\=\ -ObjC\ -framework\ MovitApp\ -lsqlite3
    export PACKAGE_TYPE\=com.apple.package-type.wrapper.application
    export PASCAL_STRINGS\=YES
    export PATH\=/Applications/Xcode.app/Contents/SharedFrameworks/SwiftBuild.framework/Versions/A/PlugIns/SWBBuildService.bundle/Contents/PlugIns/SWBUniversalPlatformPlugin.bundle/Contents/Frameworks/SWBUniversalPlatform.framework/Resources:/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin:/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/local/bin:/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/libexec:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/usr/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/usr/local/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/local/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
    export PATH_PREFIXES_EXCLUDED_FROM_HEADER_DEPENDENCIES\=/usr/include\ /usr/local/include\ /System/Library/Frameworks\ /System/Library/PrivateFrameworks\ /Applications/Xcode.app/Contents/Developer/Headers\ /Applications/Xcode.app/Contents/Developer/SDKs\ /Applications/Xcode.app/Contents/Developer/Platforms
    export PBDEVELOPMENTPLIST_PATH\=iosApp.app/pbdevelopment.plist
    export PER_ARCH_MODULE_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch
    export PER_ARCH_OBJECT_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch
    export PER_VARIANT_OBJECT_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal
    export PKGINFO_FILE_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/PkgInfo
    export PKGINFO_PATH\=iosApp.app/PkgInfo
    export PLATFORM_DEVELOPER_APPLICATIONS_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Applications
    export PLATFORM_DEVELOPER_BIN_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin
    export PLATFORM_DEVELOPER_LIBRARY_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Library
    export PLATFORM_DEVELOPER_SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs
    export PLATFORM_DEVELOPER_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Tools
    export PLATFORM_DEVELOPER_USR_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr
    export PLATFORM_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform
    export PLATFORM_DISPLAY_NAME\=iOS\ Simulator
    export PLATFORM_FAMILY_NAME\=iOS
    export PLATFORM_NAME\=iphonesimulator
    export PLATFORM_PREFERRED_ARCH\=x86_64
    export PLATFORM_PRODUCT_BUILD_VERSION\=23F73
    export PLATFORM_REQUIRES_SWIFT_AUTOLINK_EXTRACT\=NO
    export PLATFORM_REQUIRES_SWIFT_MODULEWRAP\=NO
    export PLATFORM_USES_DSYMS\=YES
    export PLIST_FILE_OUTPUT_FORMAT\=binary
    export PLUGINS_FOLDER_PATH\=iosApp.app/PlugIns
    export PRECOMPS_INCLUDE_HEADERS_FROM_BUILT_PRODUCTS_DIR\=YES
    export PRECOMP_DESTINATION_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/PrefixHeaders
    export PRIVATE_HEADERS_FOLDER_PATH\=iosApp.app/PrivateHeaders
    export PROCESSED_INFOPLIST_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch/Processed-Info.plist
    export PRODUCT_BUNDLE_IDENTIFIER\=com.movit.iosApp
    export PRODUCT_BUNDLE_PACKAGE_TYPE\=APPL
    export PRODUCT_MODULE_NAME\=iosApp
    export PRODUCT_NAME\=iosApp
    export PRODUCT_SETTINGS_PATH\=/Users/mood/POSE/android-poc/iosApp/iosApp/Info.plist
    export PRODUCT_TYPE\=com.apple.product-type.application
    export PROFILING_CODE\=NO
    export PROJECT\=iosApp
    export PROJECT_DERIVED_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/DerivedSources
    export PROJECT_DIR\=/Users/mood/POSE/android-poc/iosApp
    export PROJECT_FILE_PATH\=/Users/mood/POSE/android-poc/iosApp/iosApp.xcodeproj
    export PROJECT_GUID\=7b79493a8b7fc1f2fff3c51cd12ba024
    export PROJECT_NAME\=iosApp
    export PROJECT_TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build
    export PROJECT_TEMP_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex
    export PROVISIONING_PROFILE_REQUIRED\=NO
    export PROVISIONING_PROFILE_REQUIRED_YES_YES\=YES
    export PROVISIONING_PROFILE_SUPPORTED\=YES
    export PUBLIC_HEADERS_FOLDER_PATH\=iosApp.app/Headers
    export RECOMMENDED_IPHONEOS_DEPLOYMENT_TARGET\=15.0
    export RECURSIVE_SEARCH_PATHS_FOLLOW_SYMLINKS\=YES
    export REMOVE_CVS_FROM_RESOURCES\=YES
    export REMOVE_GIT_FROM_RESOURCES\=YES
    export REMOVE_HEADERS_FROM_EMBEDDED_BUNDLES\=YES
    export REMOVE_HG_FROM_RESOURCES\=YES
    export REMOVE_STATIC_EXECUTABLES_FROM_EMBEDDED_BUNDLES\=YES
    export REMOVE_SVN_FROM_RESOURCES\=YES
    export RESCHEDULE_INDEPENDENT_HEADERS_PHASES\=YES
    export REZ_COLLECTOR_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/ResourceManagerResources
    export REZ_OBJECTS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/ResourceManagerResources/Objects
    export REZ_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator\ 
    export RPATH_ORIGIN\=@loader_path
    export RUNTIME_EXCEPTION_ALLOW_DYLD_ENVIRONMENT_VARIABLES\=NO
    export RUNTIME_EXCEPTION_ALLOW_JIT\=NO
    export RUNTIME_EXCEPTION_ALLOW_UNSIGNED_EXECUTABLE_MEMORY\=NO
    export RUNTIME_EXCEPTION_DEBUGGING_TOOL\=NO
    export RUNTIME_EXCEPTION_DISABLE_EXECUTABLE_PAGE_PROTECTION\=NO
    export RUNTIME_EXCEPTION_DISABLE_LIBRARY_VALIDATION\=NO
    export SCANNING_PCM_KEEP_CACHE_DIRECTORY\=YES
    export SCAN_ALL_SOURCE_FILES_FOR_INCLUDES\=NO
    export SCRIPTS_FOLDER_PATH\=iosApp.app/Scripts
    export SCRIPT_INPUT_FILE_COUNT\=0
    export SCRIPT_INPUT_FILE_LIST_COUNT\=0
    export SCRIPT_OUTPUT_FILE_COUNT\=0
    export SCRIPT_OUTPUT_FILE_LIST_COUNT\=0
    export SDKROOT\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_DIR_iphonesimulator\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_DIR_iphonesimulator26_5\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_NAME\=iphonesimulator26.5
    export SDK_NAMES\=iphonesimulator26.5
    export SDK_PRODUCT_BUILD_VERSION\=23F73
    export SDK_STAT_CACHE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData
    export SDK_STAT_CACHE_ENABLE\=YES
    export SDK_STAT_CACHE_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache
    export SDK_VERSION\=26.5
    export SDK_VERSION_ACTUAL\=260500
    export SDK_VERSION_MAJOR\=260000
    export SDK_VERSION_MINOR\=260500
    export SED\=/usr/bin/sed
    export SEPARATE_STRIP\=NO
    export SEPARATE_SYMBOL_EDIT\=NO
    export SET_DIR_MODE_OWNER_GROUP\=YES
    export SET_FILE_MODE_OWNER_GROUP\=NO
    export SHALLOW_BUNDLE\=YES
    export SHALLOW_BUNDLE_TRIPLE\=ios-simulator
    export SHALLOW_BUNDLE_ios_macabi\=NO
    export SHALLOW_BUNDLE_macos\=NO
    export SHARED_DERIVED_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/DerivedSources
    export SHARED_FRAMEWORKS_FOLDER_PATH\=iosApp.app/SharedFrameworks
    export SHARED_PRECOMPS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/PrecompiledHeaders
    export SHARED_SUPPORT_FOLDER_PATH\=iosApp.app/SharedSupport
    export SKIP_INSTALL\=NO
    export SKIP_MERGEABLE_LIBRARY_BUNDLE_HOOK\=NO
    export SOURCE_ROOT\=/Users/mood/POSE/android-poc/iosApp
    export SRCROOT\=/Users/mood/POSE/android-poc/iosApp
    export STRINGSDATA_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch
    export STRINGSDATA_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export STRINGS_FILE_INFOPLIST_RENAME\=YES
    export STRINGS_FILE_OUTPUT_ENCODING\=binary
    export STRING_CATALOG_GENERATE_SYMBOLS\=NO
    export STRIP_BITCODE_FROM_COPIED_FILES\=NO
    export STRIP_INSTALLED_PRODUCT\=NO
    export STRIP_STYLE\=all
    export STRIP_SWIFT_SYMBOLS\=YES
    export SUPPORTED_DEVICE_FAMILIES\=1,2
    export SUPPORTED_PLATFORMS\=iphoneos\ iphonesimulator
    export SUPPORTS_MACCATALYST\=NO
    export SUPPORTS_ON_DEMAND_RESOURCES\=YES
    export SUPPORTS_TEXT_BASED_API\=NO
    export SUPPRESS_WARNINGS\=NO
    export SWIFT_ACTIVE_COMPILATION_CONDITIONS\=DEBUG
    export SWIFT_EMIT_CONST_VALUE_PROTOCOLS\=AnyResolverProviding\ AppEntity\ AppEnum\ AppExtension\ AppIntent\ AppIntentsPackage\ AppShortcutProviding\ AppShortcutsProvider\ AppUnionValue\ AppUnionValueCasesProviding\ DynamicOptionsProvider\ EntityQuery\ ExtensionPointDefining\ IntentValueQuery\ Resolver\ TransientEntity\ _AssistantIntentsProvider\ _GenerativeFunctionExtractable\ _IntentValueRepresentable
    export SWIFT_EMIT_LOC_STRINGS\=NO
    export SWIFT_ENABLE_EXPLICIT_MODULES\=YES
    export SWIFT_OPTIMIZATION_LEVEL\=-Onone
    export SWIFT_PLATFORM_TARGET_PREFIX\=ios
    export SWIFT_RESPONSE_FILE_PATH_normal_arm64\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftFileList
    export SWIFT_VERSION\=5.0
    export SYMROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products
    export SYSTEM_ADMIN_APPS_DIR\=/Applications/Utilities
    export SYSTEM_APPS_DIR\=/Applications
    export SYSTEM_CORE_SERVICES_DIR\=/System/Library/CoreServices
    export SYSTEM_DEMOS_DIR\=/Applications/Extras
    export SYSTEM_DEVELOPER_APPS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications
    export SYSTEM_DEVELOPER_BIN_DIR\=/Applications/Xcode.app/Contents/Developer/usr/bin
    export SYSTEM_DEVELOPER_DEMOS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Utilities/Built\ Examples
    export SYSTEM_DEVELOPER_DIR\=/Applications/Xcode.app/Contents/Developer
    export SYSTEM_DEVELOPER_DOC_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library
    export SYSTEM_DEVELOPER_GRAPHICS_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Graphics\ Tools
    export SYSTEM_DEVELOPER_JAVA_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Java\ Tools
    export SYSTEM_DEVELOPER_PERFORMANCE_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Performance\ Tools
    export SYSTEM_DEVELOPER_RELEASENOTES_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library/releasenotes
    export SYSTEM_DEVELOPER_TOOLS\=/Applications/Xcode.app/Contents/Developer/Tools
    export SYSTEM_DEVELOPER_TOOLS_DOC_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library/documentation/DeveloperTools
    export SYSTEM_DEVELOPER_TOOLS_RELEASENOTES_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library/releasenotes/DeveloperTools
    export SYSTEM_DEVELOPER_USR_DIR\=/Applications/Xcode.app/Contents/Developer/usr
    export SYSTEM_DEVELOPER_UTILITIES_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Utilities
    export SYSTEM_DEXT_INSTALL_PATH\=/System/Library/DriverExtensions
    export SYSTEM_DOCUMENTATION_DIR\=/Library/Documentation
    export SYSTEM_EXTENSIONS_FOLDER_PATH\=iosApp.app/SystemExtensions
    export SYSTEM_EXTENSIONS_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/Library/SystemExtensions
    export SYSTEM_EXTENSIONS_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app/SystemExtensions
    export SYSTEM_KEXT_INSTALL_PATH\=/System/Library/Extensions
    export SYSTEM_LIBRARY_DIR\=/System/Library
    export TAPI_DEMANGLE\=YES
    export TAPI_ENABLE_PROJECT_HEADERS\=NO
    export TAPI_LANGUAGE\=objective-c
    export TAPI_LANGUAGE_STANDARD\=compiler-default
    export TAPI_USE_SRCROOT\=YES
    export TAPI_VERIFY_MODE\=Pedantic
    export TARGETED_DEVICE_FAMILY\=1,2
    export TARGETNAME\=iosApp
    export TARGET_BUILD_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export TARGET_DEVICE_IDENTIFIER\=31C834BE-271E-46D7-A53C-5338368FBF89
    export TARGET_DEVICE_MODEL\=iPhone18,1
    export TARGET_DEVICE_OS_VERSION\=26.5
    export TARGET_DEVICE_PLATFORM_NAME\=iphonesimulator
    export TARGET_NAME\=iosApp
    export TARGET_TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_FILES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex
    export TEMP_SANDBOX_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/TemporaryTaskSandboxes
    export TEST_FRAMEWORK_SEARCH_PATHS\=\ /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Library/Frameworks\ /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/Developer/Library/Frameworks
    export TEST_LIBRARY_SEARCH_PATHS\=\ /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/lib
    export TOOLCHAINS\=com.apple.dt.toolchain.XcodeDefault
    export TOOLCHAIN_DIR\=/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain
    export TREAT_MISSING_BASELINES_AS_TEST_FAILURES\=NO
    export TREAT_MISSING_SCRIPT_PHASE_OUTPUTS_AS_ERRORS\=NO
    export TeamIdentifierPrefix\=S2QF6L6CJZ.
    export UID\=501
    export UNINSTALLED_PRODUCTS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/UninstalledProducts
    export UNLOCALIZED_RESOURCES_FOLDER_PATH\=iosApp.app
    export UNLOCALIZED_RESOURCES_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/Resources
    export UNLOCALIZED_RESOURCES_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app
    export UNSTRIPPED_PRODUCT\=NO
    export USER\=mood
    export USER_APPS_DIR\=/Users/mood/Applications
    export USER_LIBRARY_DIR\=/Users/mood/Library
    export USE_DYNAMIC_NO_PIC\=YES
    export USE_HEADERMAP\=YES
    export USE_HEADER_SYMLINKS\=NO
    export VALIDATE_DEVELOPMENT_ASSET_PATHS\=YES_ERROR
    export VALIDATE_PRODUCT\=NO
    export VALID_ARCHS\=arm64\ x86_64
    export VERBOSE_PBXCP\=NO
    export VERSIONPLIST_PATH\=iosApp.app/version.plist
    export VERSION_INFO_BUILDER\=mood
    export VERSION_INFO_FILE\=iosApp_vers.c
    export VERSION_INFO_STRING\=\"@\(\#\)PROGRAM:iosApp\ \ PROJECT:iosApp-\"
    export WORKSPACE_DIR\=/Users/mood/POSE/android-poc/iosApp
    export WRAPPER_EXTENSION\=app
    export WRAPPER_NAME\=iosApp.app
    export WRAPPER_SUFFIX\=.app
    export WRAP_ASSET_PACKS_IN_SEPARATE_DIRECTORIES\=NO
    export XCODE_APP_SUPPORT_DIR\=/Applications/Xcode.app/Contents/Developer/Library/Xcode
    export XCODE_PRODUCT_BUILD_VERSION\=17F42
    export XCODE_VERSION_ACTUAL\=2650
    export XCODE_VERSION_MAJOR\=2600
    export XCODE_VERSION_MINOR\=2650
    export XPCSERVICES_FOLDER_PATH\=iosApp.app/XPCServices
    export YACC\=yacc
    export _DISCOVER_COMMAND_LINE_LINKER_INPUTS\=YES
    export _DISCOVER_COMMAND_LINE_LINKER_INPUTS_INCLUDE_WL\=YES
    export _LD_MULTIARCH\=YES
    export _WRAPPER_CONTENTS_DIR_SHALLOW_BUNDLE_NO\=/Contents
    export _WRAPPER_PARENT_PATH_SHALLOW_BUNDLE_NO\=/..
    export _WRAPPER_RESOURCES_DIR_SHALLOW_BUNDLE_NO\=/Resources
    export __DIAGNOSE_DEPRECATED_ARCHS\=YES
    export __IS_NOT_MACOS\=YES
    export __IS_NOT_MACOS_macosx\=NO
    export __IS_NOT_SIMULATOR\=NO
    export __IS_NOT_SIMULATOR_simulator\=NO
    export __ORIGINAL_SDK_DEFINED_LLVM_TARGET_TRIPLE_SYS\=ios
    export arch\=undefined_arch
    export variant\=normal
    /bin/sh -c /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Script-49A0C7495C36D488C55314C3.sh

PhaseScriptExecution Build\ MovitApp\ Kotlin\ framework /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Script-FA434838CD8F99D705F78E11.sh (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    export ACTION\=build
    export AD_HOC_CODE_SIGNING_ALLOWED\=YES
    export AGGREGATE_TRACKED_DOMAINS\=YES
    export ALLOW_BUILD_REQUEST_OVERRIDES\=NO
    export ALLOW_TARGET_PLATFORM_SPECIALIZATION\=NO
    export ALTERNATE_GROUP\=staff
    export ALTERNATE_MODE\=u+w,go-w,a+rX
    export ALTERNATE_OWNER\=mood
    export ALTERNATIVE_DISTRIBUTION_WEB\=NO
    export ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES\=NO
    export ALWAYS_SEARCH_USER_PATHS\=NO
    export ALWAYS_USE_SEPARATE_HEADERMAPS\=NO
    export APPLICATION_EXTENSION_API_ONLY\=NO
    export APPLY_RULES_IN_COPY_FILES\=NO
    export APPLY_RULES_IN_COPY_HEADERS\=NO
    export APP_SHORTCUTS_ENABLE_FLEXIBLE_MATCHING\=YES
    export ARCHS\=arm64
    export ARCHS_BASE\=arm64
    export ARCHS_STANDARD\=arm64\ x86_64
    export ARCHS_STANDARD_32_64_BIT\=arm64\ x86_64
    export ARCHS_STANDARD_64_BIT\=arm64\ x86_64
    export ARCHS_STANDARD_INCLUDING_64_BIT\=arm64\ x86_64
    export ARCHS_UNIVERSAL_IPHONE_OS\=arm64\ x86_64
    export ASSETCATALOG_COMPILER_APPICON_NAME\=AppIcon
    export ASSETCATALOG_FILTER_FOR_DEVICE_MODEL\=iPhone18,1
    export ASSETCATALOG_FILTER_FOR_DEVICE_OS_VERSION\=26.5
    export ASSETCATALOG_FILTER_FOR_THINNING_DEVICE_CONFIGURATION\=iPhone18,1
    export AUTOMATICALLY_MERGE_DEPENDENCIES\=NO
    export AUTOMATION_APPLE_EVENTS\=NO
    export AVAILABLE_PLATFORMS\=android\ appletvos\ appletvsimulator\ driverkit\ freebsd\ iphoneos\ iphonesimulator\ linux\ macosx\ none\ openbsd\ qnx\ watchos\ watchsimulator\ webassembly\ xros\ xrsimulator
    export AppIdentifierPrefix\=S2QF6L6CJZ.
    export BUILD_ACTIVE_RESOURCES_ONLY\=YES
    export BUILD_COMPONENTS\=headers\ build
    export BUILD_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products
    export BUILD_LIBRARY_FOR_DISTRIBUTION\=NO
    export BUILD_ONLY_KNOWN_LOCALIZATIONS\=NO
    export BUILD_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products
    export BUILD_STYLE\=
    export BUILD_VARIANTS\=normal
    export BUILT_PRODUCTS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export BUNDLE_CONTENTS_FOLDER_PATH_deep\=Contents/
    export BUNDLE_EXECUTABLE_FOLDER_NAME_deep\=MacOS
    export BUNDLE_EXTENSIONS_FOLDER_PATH\=Extensions
    export BUNDLE_FORMAT\=shallow
    export BUNDLE_FRAMEWORKS_FOLDER_PATH\=Frameworks
    export BUNDLE_PLUGINS_FOLDER_PATH\=PlugIns
    export BUNDLE_PRIVATE_HEADERS_FOLDER_PATH\=PrivateHeaders
    export BUNDLE_PUBLIC_HEADERS_FOLDER_PATH\=Headers
    export CACHE_ROOT\=/var/folders/xz/9yr8chc564x0w_mj7j122k6m0000gn/C/com.apple.DeveloperTools/26.5-17F42/Xcode
    export CCHROOT\=/var/folders/xz/9yr8chc564x0w_mj7j122k6m0000gn/C/com.apple.DeveloperTools/26.5-17F42/Xcode
    export CHMOD\=/bin/chmod
    export CHOWN\=chown
    export CLANG_ANALYZER_NONNULL\=YES
    export CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION\=YES_AGGRESSIVE
    export CLANG_CACHE_FINE_GRAINED_OUTPUTS\=YES
    export CLANG_CXX_LANGUAGE_STANDARD\=gnu++14
    export CLANG_CXX_LIBRARY\=libc++
    export CLANG_ENABLE_EXPLICIT_MODULES\=YES
    export CLANG_ENABLE_MODULES\=YES
    export CLANG_ENABLE_OBJC_ARC\=YES
    export CLANG_ENABLE_OBJC_WEAK\=YES
    export CLANG_MODULES_BUILD_SESSION_FILE\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation
    export CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING\=YES
    export CLANG_WARN_BOOL_CONVERSION\=YES
    export CLANG_WARN_COMMA\=YES
    export CLANG_WARN_CONSTANT_CONVERSION\=YES
    export CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS\=YES
    export CLANG_WARN_DIRECT_OBJC_ISA_USAGE\=YES_ERROR
    export CLANG_WARN_DOCUMENTATION_COMMENTS\=YES
    export CLANG_WARN_EMPTY_BODY\=YES
    export CLANG_WARN_ENUM_CONVERSION\=YES
    export CLANG_WARN_INFINITE_RECURSION\=YES
    export CLANG_WARN_INT_CONVERSION\=YES
    export CLANG_WARN_NON_LITERAL_NULL_CONVERSION\=YES
    export CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF\=YES
    export CLANG_WARN_OBJC_LITERAL_CONVERSION\=YES
    export CLANG_WARN_OBJC_ROOT_CLASS\=YES_ERROR
    export CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER\=YES
    export CLANG_WARN_RANGE_LOOP_ANALYSIS\=YES
    export CLANG_WARN_STRICT_PROTOTYPES\=YES
    export CLANG_WARN_SUSPICIOUS_MOVE\=YES
    export CLANG_WARN_UNGUARDED_AVAILABILITY\=YES_AGGRESSIVE
    export CLANG_WARN_UNREACHABLE_CODE\=YES
    export CLANG_WARN__DUPLICATE_METHOD_MATCH\=YES
    export CLASS_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/JavaClasses
    export CLEAN_PRECOMPS\=YES
    export CLONE_HEADERS\=NO
    export CODESIGNING_FOLDER_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app
    export CODE_SIGNING_ALLOWED\=YES
    export CODE_SIGNING_REQUIRED\=YES
    export CODE_SIGN_CONTEXT_CLASS\=XCiPhoneSimulatorCodeSignContext
    export CODE_SIGN_ENTITLEMENTS\=iosApp/iosApp.entitlements
    export CODE_SIGN_IDENTITY\=iPhone\ Developer
    export CODE_SIGN_INJECT_BASE_ENTITLEMENTS\=YES
    export COLOR_DIAGNOSTICS\=NO
    export COMBINE_HIDPI_IMAGES\=NO
    export COMPILATION_CACHE_CAS_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/CompilationCache.noindex
    export COMPILATION_CACHE_KEEP_CAS_DIRECTORY\=YES
    export COMPILER_INDEX_STORE_ENABLE\=Default
    export COMPOSITE_SDK_DIRS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/CompositeSDKs
    export COMPRESS_PNG_FILES\=YES
    export CONFIGURATION\=Debug
    export CONFIGURATION_BUILD_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export CONFIGURATION_TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator
    export CONTENTS_FOLDER_PATH\=iosApp.app
    export CONTENTS_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/Contents
    export CONTENTS_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app
    export COPYING_PRESERVES_HFS_DATA\=NO
    export COPY_HEADERS_RUN_UNIFDEF\=NO
    export COPY_PHASE_STRIP\=NO
    export CORRESPONDING_DEVICE_PLATFORM_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform
    export CORRESPONDING_DEVICE_PLATFORM_NAME\=iphoneos
    export CORRESPONDING_DEVICE_SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS26.5.sdk
    export CORRESPONDING_DEVICE_SDK_NAME\=iphoneos26.5
    export CP\=/bin/cp
    export CREATE_INFOPLIST_SECTION_IN_BINARY\=NO
    export CURRENT_ARCH\=undefined_arch
    export CURRENT_VARIANT\=normal
    export DEAD_CODE_STRIPPING\=YES
    export DEBUGGING_SYMBOLS\=YES
    export DEBUG_INFORMATION_FORMAT\=dwarf
    export DEBUG_INFORMATION_VERSION\=compiler-default
    export DEFAULT_COMPILER\=com.apple.compilers.llvm.clang.1_0
    export DEFAULT_DEXT_INSTALL_PATH\=/System/Library/DriverExtensions
    export DEFAULT_KEXT_INSTALL_PATH\=/System/Library/Extensions
    export DEFINES_MODULE\=NO
    export DEPLOYMENT_LOCATION\=NO
    export DEPLOYMENT_POSTPROCESSING\=NO
    export DEPLOYMENT_TARGET_SETTING_NAME\=IPHONEOS_DEPLOYMENT_TARGET
    export DEPLOYMENT_TARGET_SUGGESTED_VALUES\=12.0\ 12.1\ 12.2\ 12.3\ 12.4\ 13.0\ 13.1\ 13.2\ 13.3\ 13.4\ 13.5\ 13.6\ 14.0\ 14.1\ 14.2\ 14.3\ 14.4\ 14.5\ 14.6\ 14.7\ 15.0\ 15.1\ 15.2\ 15.3\ 15.4\ 15.5\ 15.6\ 16.0\ 16.1\ 16.2\ 16.3\ 16.4\ 16.5\ 16.6\ 17.0\ 17.1\ 17.2\ 17.3\ 17.4\ 17.5\ 17.6\ 18.0\ 18.1\ 18.2\ 18.3\ 18.4\ 18.5\ 18.6\ 26.0\ 26.2\ 26.3\ 26.4\ 26.5
    export DERIVED_FILES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources
    export DERIVED_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources
    export DERIVED_SOURCES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources
    export DEVELOPER_APPLICATIONS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications
    export DEVELOPER_BIN_DIR\=/Applications/Xcode.app/Contents/Developer/usr/bin
    export DEVELOPER_DIR\=/Applications/Xcode.app/Contents/Developer
    export DEVELOPER_FRAMEWORKS_DIR\=/Applications/Xcode.app/Contents/Developer/Library/Frameworks
    export DEVELOPER_FRAMEWORKS_DIR_QUOTED\=/Applications/Xcode.app/Contents/Developer/Library/Frameworks
    export DEVELOPER_LIBRARY_DIR\=/Applications/Xcode.app/Contents/Developer/Library
    export DEVELOPER_SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs
    export DEVELOPER_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Tools
    export DEVELOPER_USR_DIR\=/Applications/Xcode.app/Contents/Developer/usr
    export DEVELOPMENT_LANGUAGE\=en
    export DIAGNOSE_MISSING_TARGET_DEPENDENCIES\=YES
    export DIFF\=/usr/bin/diff
    export DOCUMENTATION_FOLDER_PATH\=iosApp.app/en.lproj/Documentation
    export DONT_GENERATE_INFOPLIST_FILE\=NO
    export DSTROOT\=/tmp/iosApp.dst
    export DT_TOOLCHAIN_DIR\=/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain
    export DUMP_DEPENDENCIES\=NO
    export DUMP_DEPENDENCIES_OUTPUT_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-BuildDependencyInfo.json
    export DWARF_DSYM_FILE_NAME\=iosApp.app.dSYM
    export DWARF_DSYM_FILE_SHOULD_ACCOMPANY_PRODUCT\=NO
    export DWARF_DSYM_FOLDER_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export DYNAMIC_LIBRARY_EXTENSION\=dylib
    export EAGER_COMPILATION_ALLOW_SCRIPTS\=NO
    export EAGER_LINKING\=NO
    export EFFECTIVE_PLATFORM_NAME\=-iphonesimulator
    export EFFECTIVE_SWIFT_VERSION\=5
    export EMBEDDED_CONTENT_CONTAINS_SWIFT\=NO
    export EMBED_ASSET_PACKS_IN_PRODUCT_BUNDLE\=NO
    export ENABLE_APP_SANDBOX\=NO
    export ENABLE_CODE_COVERAGE\=YES
    export ENABLE_COHORT_ARCHS\=NO
    export ENABLE_CPLUSPLUS_BOUNDS_SAFE_BUFFERS\=NO
    export ENABLE_C_BOUNDS_SAFETY\=NO
    export ENABLE_DEBUG_DYLIB\=YES
    export ENABLE_DEFAULT_HEADER_SEARCH_PATHS\=YES
    export ENABLE_DEFAULT_SEARCH_PATHS\=YES
    export ENABLE_ENHANCED_SECURITY\=NO
    export ENABLE_HARDENED_RUNTIME\=NO
    export ENABLE_HEADER_DEPENDENCIES\=YES
    export ENABLE_INCOMING_NETWORK_CONNECTIONS\=NO
    export ENABLE_ON_DEMAND_RESOURCES\=YES
    export ENABLE_OUTGOING_NETWORK_CONNECTIONS\=NO
    export ENABLE_POINTER_AUTHENTICATION\=NO
    export ENABLE_PREVIEWS\=NO
    export ENABLE_RESOURCE_ACCESS_AUDIO_INPUT\=NO
    export ENABLE_RESOURCE_ACCESS_BLUETOOTH\=NO
    export ENABLE_RESOURCE_ACCESS_CALENDARS\=NO
    export ENABLE_RESOURCE_ACCESS_CAMERA\=NO
    export ENABLE_RESOURCE_ACCESS_CONTACTS\=NO
    export ENABLE_RESOURCE_ACCESS_LOCATION\=NO
    export ENABLE_RESOURCE_ACCESS_PHOTO_LIBRARY\=NO
    export ENABLE_RESOURCE_ACCESS_PRINTING\=NO
    export ENABLE_RESOURCE_ACCESS_USB\=NO
    export ENABLE_SDK_IMPORTS\=NO
    export ENABLE_SECURITY_COMPILER_WARNINGS\=NO
    export ENABLE_STRICT_OBJC_MSGSEND\=YES
    export ENABLE_TESTABILITY\=YES
    export ENABLE_TESTING_SEARCH_PATHS\=NO
    export ENABLE_USER_SCRIPT_SANDBOXING\=NO
    export ENABLE_XOJIT_PREVIEWS\=YES
    export ENFORCE_VALID_ARCHS\=YES
    export ENTITLEMENTS_ALLOWED\=NO
    export ENTITLEMENTS_DESTINATION\=__entitlements
    export ENTITLEMENTS_REQUIRED\=NO
    export EXCLUDED_ARCHS\=x86_64
    export EXCLUDED_INSTALLSRC_SUBDIRECTORY_PATTERNS\=.DS_Store\ .svn\ .git\ .hg\ CVS
    export EXCLUDED_RECURSIVE_SEARCH_PATH_SUBDIRECTORIES\=\*.nib\ \*.lproj\ \*.framework\ \*.gch\ \*.xcode\*\ \*.xcassets\ \*.icon\ \(\*\)\ .DS_Store\ CVS\ .svn\ .git\ .hg\ \*.pbproj\ \*.pbxproj
    export EXECUTABLES_FOLDER_PATH\=iosApp.app/Executables
    export EXECUTABLE_BLANK_INJECTION_DYLIB_PATH\=iosApp.app/__preview.dylib
    export EXECUTABLE_DEBUG_DYLIB_INSTALL_NAME\=@rpath/iosApp.debug.dylib
    export EXECUTABLE_DEBUG_DYLIB_PATH\=iosApp.app/iosApp.debug.dylib
    export EXECUTABLE_FOLDER_PATH\=iosApp.app
    export EXECUTABLE_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/MacOS
    export EXECUTABLE_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app
    export EXECUTABLE_NAME\=iosApp
    export EXECUTABLE_PATH\=iosApp.app/iosApp
    export EXPANDED_CODE_SIGN_IDENTITY\=-
    export EXPANDED_CODE_SIGN_IDENTITY_NAME\=Sign\ to\ Run\ Locally
    export EXTENSIONS_FOLDER_PATH\=iosApp.app/Extensions
    export FILE_LIST\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects/LinkFileList
    export FIXED_FILES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/FixedFiles
    export FRAMEWORKS_FOLDER_PATH\=iosApp.app/Frameworks
    export FRAMEWORK_FLAG_PREFIX\=-framework
    export FRAMEWORK_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator\ \ /Users/mood/POSE/android-poc/iosApp/../feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5
    export FRAMEWORK_VERSION\=A
    export FULL_PRODUCT_NAME\=iosApp.app
    export FUSE_BUILD_PHASES\=YES
    export FUSE_BUILD_SCRIPT_PHASES\=NO
    export GCC3_VERSION\=3.3
    export GCC_C_LANGUAGE_STANDARD\=gnu11
    export GCC_DYNAMIC_NO_PIC\=NO
    export GCC_INLINES_ARE_PRIVATE_EXTERN\=YES
    export GCC_NO_COMMON_BLOCKS\=YES
    export GCC_OBJC_LEGACY_DISPATCH\=YES
    export GCC_OPTIMIZATION_LEVEL\=0
    export GCC_PFE_FILE_C_DIALECTS\=c\ objective-c\ c++\ objective-c++
    export GCC_PREPROCESSOR_DEFINITIONS\=\ DEBUG\=1
    export GCC_SYMBOLS_PRIVATE_EXTERN\=NO
    export GCC_TREAT_WARNINGS_AS_ERRORS\=NO
    export GCC_VERSION\=com.apple.compilers.llvm.clang.1_0
    export GCC_VERSION_IDENTIFIER\=com_apple_compilers_llvm_clang_1_0
    export GCC_WARN_64_TO_32_BIT_CONVERSION\=YES
    export GCC_WARN_ABOUT_RETURN_TYPE\=YES_ERROR
    export GCC_WARN_UNDECLARED_SELECTOR\=YES
    export GCC_WARN_UNINITIALIZED_AUTOS\=YES_AGGRESSIVE
    export GCC_WARN_UNUSED_FUNCTION\=YES
    export GCC_WARN_UNUSED_VARIABLE\=YES
    export GENERATED_MODULEMAP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/GeneratedModuleMaps-iphonesimulator
    export GENERATE_INFOPLIST_FILE\=NO
    export GENERATE_INTERMEDIATE_TEXT_BASED_STUBS\=YES
    export GENERATE_PKGINFO_FILE\=YES
    export GENERATE_PRELINK_OBJECT_FILE\=NO
    export GENERATE_PROFILING_CODE\=NO
    export GENERATE_TEXT_BASED_STUBS\=NO
    export GID\=20
    export GROUP\=staff
    export HEADERMAP_INCLUDES_FLAT_ENTRIES_FOR_TARGET_BEING_BUILT\=YES
    export HEADERMAP_INCLUDES_FRAMEWORK_ENTRIES_FOR_ALL_PRODUCT_TYPES\=YES
    export HEADERMAP_INCLUDES_FRAMEWORK_ENTRIES_FOR_TARGETS_NOT_BEING_BUILT\=YES
    export HEADERMAP_INCLUDES_NONPUBLIC_NONPRIVATE_HEADERS\=YES
    export HEADERMAP_INCLUDES_PROJECT_HEADERS\=YES
    export HEADERMAP_USES_FRAMEWORK_PREFIX_ENTRIES\=YES
    export HEADERMAP_USES_VFS\=NO
    export HEADER_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/include\ 
    export HOME\=/Users/mood
    export HOST_ARCH\=arm64
    export HOST_PLATFORM\=macosx
    export ICONV\=/usr/bin/iconv
    export IMPLICIT_DEPENDENCY_DOMAIN\=default
    export INDEX_STORE_COMPRESS\=NO
    export INDEX_STORE_ONLY_PROJECT_FILES\=NO
    export INFOPLIST_ENABLE_CFBUNDLEICONS_MERGE\=YES
    export INFOPLIST_EXPAND_BUILD_SETTINGS\=YES
    export INFOPLIST_FILE\=iosApp/Info.plist
    export INFOPLIST_OUTPUT_FORMAT\=binary
    export INFOPLIST_PATH\=iosApp.app/Info.plist
    export INFOPLIST_PREPROCESS\=NO
    export INFOSTRINGS_PATH\=iosApp.app/en.lproj/InfoPlist.strings
    export INLINE_PRIVATE_FRAMEWORKS\=NO
    export INSTALLAPI_IGNORE_SKIP_INSTALL\=YES
    export INSTALLHDRS_COPY_PHASE\=NO
    export INSTALLHDRS_SCRIPT_PHASE\=NO
    export INSTALL_DIR\=/tmp/iosApp.dst/Applications
    export INSTALL_GROUP\=staff
    export INSTALL_MODE_FLAG\=u+w,go-w,a+rX
    export INSTALL_OWNER\=mood
    export INSTALL_PATH\=/Applications
    export INSTALL_ROOT\=/tmp/iosApp.dst
    export IPHONEOS_DEPLOYMENT_TARGET\=18.0
    export IS_UNOPTIMIZED_BUILD\=YES
    export JAVAC_DEFAULT_FLAGS\=-J-Xms64m\ -J-XX:NewSize\=4M\ -J-Dfile.encoding\=UTF8
    export JAVA_APP_STUB\=/System/Library/Frameworks/JavaVM.framework/Resources/MacOS/JavaApplicationStub
    export JAVA_ARCHIVE_CLASSES\=YES
    export JAVA_ARCHIVE_TYPE\=JAR
    export JAVA_COMPILER\=/usr/bin/javac
    export JAVA_FOLDER_PATH\=iosApp.app/Java
    export JAVA_FRAMEWORK_RESOURCES_DIRS\=Resources
    export JAVA_JAR_FLAGS\=cv
    export JAVA_SOURCE_SUBDIR\=.
    export JAVA_USE_DEPENDENCIES\=YES
    export JAVA_ZIP_FLAGS\=-urg
    export JIKES_DEFAULT_FLAGS\=+E\ +OLDCSO
    export KEEP_PRIVATE_EXTERNS\=NO
    export LD_DEPENDENCY_INFO_FILE\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch/iosApp_dependency_info.dat
    export LD_ENTITLEMENTS_SECTION\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.app-Simulated.xcent
    export LD_ENTITLEMENTS_SECTION_DER\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.app-Simulated.xcent.der
    export LD_EXPORT_SYMBOLS\=YES
    export LD_GENERATE_MAP_FILE\=NO
    export LD_MAP_FILE_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-LinkMap-normal-undefined_arch.txt
    export LD_NO_PIE\=NO
    export LD_QUOTE_LINKER_ARGUMENTS_FOR_COMPILER_DRIVER\=YES
    export LD_RUNPATH_SEARCH_PATHS\=\ @executable_path/Frameworks
    export LD_RUNPATH_SEARCH_PATHS_YES\=@loader_path/../Frameworks
    export LD_SHARED_CACHE_ELIGIBLE\=Automatic
    export LD_WARN_DUPLICATE_LIBRARIES\=NO
    export LD_WARN_UNUSED_DYLIBS\=NO
    export LEGACY_DEVELOPER_DIR\=/Applications/Xcode.app/Contents/PlugIns/Xcode3Core.ideplugin/Contents/SharedSupport/Developer
    export LEX\=lex
    export LIBRARY_DEXT_INSTALL_PATH\=/Library/DriverExtensions
    export LIBRARY_FLAG_NOSPACE\=YES
    export LIBRARY_FLAG_PREFIX\=-l
    export LIBRARY_KEXT_INSTALL_PATH\=/Library/Extensions
    export LIBRARY_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator\ 
    export LINKER_DISPLAYS_MANGLED_NAMES\=NO
    export LINK_FILE_LIST_normal_arm64\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.LinkFileList
    export LINK_OBJC_RUNTIME\=YES
    export LINK_WITH_STANDARD_LIBRARIES\=YES
    export LLVM_TARGET_TRIPLE_OS_VERSION\=ios18.0
    export LLVM_TARGET_TRIPLE_SUFFIX\=-simulator
    export LLVM_TARGET_TRIPLE_VENDOR\=apple
    export LM_AUX_CONST_METADATA_LIST_PATH_normal_arm64\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftConstValuesFileList
    export LOCALIZATION_EXPORT_SUPPORTED\=YES
    export LOCALIZATION_PREFERS_STRING_CATALOGS\=NO
    export LOCALIZED_RESOURCES_FOLDER_PATH\=iosApp.app/en.lproj
    export LOCALIZED_STRING_CODE_COMMENTS\=NO
    export LOCALIZED_STRING_MACRO_NAMES\=NSLocalizedString\ CFCopyLocalizedString
    export LOCALIZED_STRING_SWIFTUI_SUPPORT\=YES
    export LOCAL_ADMIN_APPS_DIR\=/Applications/Utilities
    export LOCAL_APPS_DIR\=/Applications
    export LOCAL_DEVELOPER_DIR\=/Library/Developer
    export LOCAL_LIBRARY_DIR\=/Library
    export LOCROOT\=/Users/mood/POSE/android-poc/iosApp
    export LOCSYMROOT\=/Users/mood/POSE/android-poc/iosApp
    export MACH_O_TYPE\=mh_execute
    export MAC_OS_X_PRODUCT_BUILD_VERSION\=25F80
    export MAC_OS_X_VERSION_ACTUAL\=260501
    export MAC_OS_X_VERSION_MAJOR\=260000
    export MAC_OS_X_VERSION_MINOR\=260500
    export MAKE_MERGEABLE\=NO
    export MERGEABLE_LIBRARY\=NO
    export MERGED_BINARY_TYPE\=none
    export MERGE_LINKED_LIBRARIES\=NO
    export METAL_LIBRARY_FILE_BASE\=default
    export METAL_LIBRARY_OUTPUT_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app
    export MODULES_FOLDER_PATH\=iosApp.app/Modules
    export MODULE_CACHE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex
    export MTL_ENABLE_DEBUG_INFO\=INCLUDE_SOURCE
    export MTL_FAST_MATH\=YES
    export NATIVE_ARCH\=arm64
    export NATIVE_ARCH_32_BIT\=arm
    export NATIVE_ARCH_64_BIT\=arm64
    export NATIVE_ARCH_ACTUAL\=arm64
    export NO_COMMON\=YES
    export OBJC_ABI_VERSION\=2
    export OBJECT_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects
    export OBJECT_FILE_DIR_normal\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal
    export OBJROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex
    export ONLY_ACTIVE_ARCH\=YES
    export OS\=MACOS
    export OSAC\=/usr/bin/osacompile
    export OTHER_LDFLAGS\=\ -ObjC\ -framework\ MovitApp\ -lsqlite3
    export PACKAGE_TYPE\=com.apple.package-type.wrapper.application
    export PASCAL_STRINGS\=YES
    export PATH\=/Applications/Xcode.app/Contents/SharedFrameworks/SwiftBuild.framework/Versions/A/PlugIns/SWBBuildService.bundle/Contents/PlugIns/SWBUniversalPlatformPlugin.bundle/Contents/Frameworks/SWBUniversalPlatform.framework/Resources:/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin:/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/local/bin:/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/libexec:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/usr/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/usr/local/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/local/bin:/Applications/Xcode.app/Contents/Developer/usr/bin:/Applications/Xcode.app/Contents/Developer/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
    export PATH_PREFIXES_EXCLUDED_FROM_HEADER_DEPENDENCIES\=/usr/include\ /usr/local/include\ /System/Library/Frameworks\ /System/Library/PrivateFrameworks\ /Applications/Xcode.app/Contents/Developer/Headers\ /Applications/Xcode.app/Contents/Developer/SDKs\ /Applications/Xcode.app/Contents/Developer/Platforms
    export PBDEVELOPMENTPLIST_PATH\=iosApp.app/pbdevelopment.plist
    export PER_ARCH_MODULE_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch
    export PER_ARCH_OBJECT_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch
    export PER_VARIANT_OBJECT_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal
    export PKGINFO_FILE_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/PkgInfo
    export PKGINFO_PATH\=iosApp.app/PkgInfo
    export PLATFORM_DEVELOPER_APPLICATIONS_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Applications
    export PLATFORM_DEVELOPER_BIN_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/bin
    export PLATFORM_DEVELOPER_LIBRARY_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Library
    export PLATFORM_DEVELOPER_SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs
    export PLATFORM_DEVELOPER_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Tools
    export PLATFORM_DEVELOPER_USR_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr
    export PLATFORM_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform
    export PLATFORM_DISPLAY_NAME\=iOS\ Simulator
    export PLATFORM_FAMILY_NAME\=iOS
    export PLATFORM_NAME\=iphonesimulator
    export PLATFORM_PREFERRED_ARCH\=x86_64
    export PLATFORM_PRODUCT_BUILD_VERSION\=23F73
    export PLATFORM_REQUIRES_SWIFT_AUTOLINK_EXTRACT\=NO
    export PLATFORM_REQUIRES_SWIFT_MODULEWRAP\=NO
    export PLATFORM_USES_DSYMS\=YES
    export PLIST_FILE_OUTPUT_FORMAT\=binary
    export PLUGINS_FOLDER_PATH\=iosApp.app/PlugIns
    export PRECOMPS_INCLUDE_HEADERS_FROM_BUILT_PRODUCTS_DIR\=YES
    export PRECOMP_DESTINATION_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/PrefixHeaders
    export PRIVATE_HEADERS_FOLDER_PATH\=iosApp.app/PrivateHeaders
    export PROCESSED_INFOPLIST_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch/Processed-Info.plist
    export PRODUCT_BUNDLE_IDENTIFIER\=com.movit.iosApp
    export PRODUCT_BUNDLE_PACKAGE_TYPE\=APPL
    export PRODUCT_MODULE_NAME\=iosApp
    export PRODUCT_NAME\=iosApp
    export PRODUCT_SETTINGS_PATH\=/Users/mood/POSE/android-poc/iosApp/iosApp/Info.plist
    export PRODUCT_TYPE\=com.apple.product-type.application
    export PROFILING_CODE\=NO
    export PROJECT\=iosApp
    export PROJECT_DERIVED_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/DerivedSources
    export PROJECT_DIR\=/Users/mood/POSE/android-poc/iosApp
    export PROJECT_FILE_PATH\=/Users/mood/POSE/android-poc/iosApp/iosApp.xcodeproj
    export PROJECT_GUID\=7b79493a8b7fc1f2fff3c51cd12ba024
    export PROJECT_NAME\=iosApp
    export PROJECT_TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build
    export PROJECT_TEMP_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex
    export PROVISIONING_PROFILE_REQUIRED\=NO
    export PROVISIONING_PROFILE_REQUIRED_YES_YES\=YES
    export PROVISIONING_PROFILE_SUPPORTED\=YES
    export PUBLIC_HEADERS_FOLDER_PATH\=iosApp.app/Headers
    export RECOMMENDED_IPHONEOS_DEPLOYMENT_TARGET\=15.0
    export RECURSIVE_SEARCH_PATHS_FOLLOW_SYMLINKS\=YES
    export REMOVE_CVS_FROM_RESOURCES\=YES
    export REMOVE_GIT_FROM_RESOURCES\=YES
    export REMOVE_HEADERS_FROM_EMBEDDED_BUNDLES\=YES
    export REMOVE_HG_FROM_RESOURCES\=YES
    export REMOVE_STATIC_EXECUTABLES_FROM_EMBEDDED_BUNDLES\=YES
    export REMOVE_SVN_FROM_RESOURCES\=YES
    export RESCHEDULE_INDEPENDENT_HEADERS_PHASES\=YES
    export REZ_COLLECTOR_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/ResourceManagerResources
    export REZ_OBJECTS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/ResourceManagerResources/Objects
    export REZ_SEARCH_PATHS\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator\ 
    export RPATH_ORIGIN\=@loader_path
    export RUNTIME_EXCEPTION_ALLOW_DYLD_ENVIRONMENT_VARIABLES\=NO
    export RUNTIME_EXCEPTION_ALLOW_JIT\=NO
    export RUNTIME_EXCEPTION_ALLOW_UNSIGNED_EXECUTABLE_MEMORY\=NO
    export RUNTIME_EXCEPTION_DEBUGGING_TOOL\=NO
    export RUNTIME_EXCEPTION_DISABLE_EXECUTABLE_PAGE_PROTECTION\=NO
    export RUNTIME_EXCEPTION_DISABLE_LIBRARY_VALIDATION\=NO
    export SCANNING_PCM_KEEP_CACHE_DIRECTORY\=YES
    export SCAN_ALL_SOURCE_FILES_FOR_INCLUDES\=NO
    export SCRIPTS_FOLDER_PATH\=iosApp.app/Scripts
    export SCRIPT_INPUT_FILE_COUNT\=0
    export SCRIPT_INPUT_FILE_LIST_COUNT\=0
    export SCRIPT_OUTPUT_FILE_COUNT\=0
    export SCRIPT_OUTPUT_FILE_LIST_COUNT\=0
    export SDKROOT\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_DIR\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_DIR_iphonesimulator\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_DIR_iphonesimulator26_5\=/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk
    export SDK_NAME\=iphonesimulator26.5
    export SDK_NAMES\=iphonesimulator26.5
    export SDK_PRODUCT_BUILD_VERSION\=23F73
    export SDK_STAT_CACHE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData
    export SDK_STAT_CACHE_ENABLE\=YES
    export SDK_STAT_CACHE_PATH\=/Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache
    export SDK_VERSION\=26.5
    export SDK_VERSION_ACTUAL\=260500
    export SDK_VERSION_MAJOR\=260000
    export SDK_VERSION_MINOR\=260500
    export SED\=/usr/bin/sed
    export SEPARATE_STRIP\=NO
    export SEPARATE_SYMBOL_EDIT\=NO
    export SET_DIR_MODE_OWNER_GROUP\=YES
    export SET_FILE_MODE_OWNER_GROUP\=NO
    export SHALLOW_BUNDLE\=YES
    export SHALLOW_BUNDLE_TRIPLE\=ios-simulator
    export SHALLOW_BUNDLE_ios_macabi\=NO
    export SHALLOW_BUNDLE_macos\=NO
    export SHARED_DERIVED_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/DerivedSources
    export SHARED_FRAMEWORKS_FOLDER_PATH\=iosApp.app/SharedFrameworks
    export SHARED_PRECOMPS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/PrecompiledHeaders
    export SHARED_SUPPORT_FOLDER_PATH\=iosApp.app/SharedSupport
    export SKIP_INSTALL\=NO
    export SKIP_MERGEABLE_LIBRARY_BUNDLE_HOOK\=NO
    export SOURCE_ROOT\=/Users/mood/POSE/android-poc/iosApp
    export SRCROOT\=/Users/mood/POSE/android-poc/iosApp
    export STRINGSDATA_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/undefined_arch
    export STRINGSDATA_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export STRINGS_FILE_INFOPLIST_RENAME\=YES
    export STRINGS_FILE_OUTPUT_ENCODING\=binary
    export STRING_CATALOG_GENERATE_SYMBOLS\=NO
    export STRIP_BITCODE_FROM_COPIED_FILES\=NO
    export STRIP_INSTALLED_PRODUCT\=NO
    export STRIP_STYLE\=all
    export STRIP_SWIFT_SYMBOLS\=YES
    export SUPPORTED_DEVICE_FAMILIES\=1,2
    export SUPPORTED_PLATFORMS\=iphoneos\ iphonesimulator
    export SUPPORTS_MACCATALYST\=NO
    export SUPPORTS_ON_DEMAND_RESOURCES\=YES
    export SUPPORTS_TEXT_BASED_API\=NO
    export SUPPRESS_WARNINGS\=NO
    export SWIFT_ACTIVE_COMPILATION_CONDITIONS\=DEBUG
    export SWIFT_EMIT_CONST_VALUE_PROTOCOLS\=AnyResolverProviding\ AppEntity\ AppEnum\ AppExtension\ AppIntent\ AppIntentsPackage\ AppShortcutProviding\ AppShortcutsProvider\ AppUnionValue\ AppUnionValueCasesProviding\ DynamicOptionsProvider\ EntityQuery\ ExtensionPointDefining\ IntentValueQuery\ Resolver\ TransientEntity\ _AssistantIntentsProvider\ _GenerativeFunctionExtractable\ _IntentValueRepresentable
    export SWIFT_EMIT_LOC_STRINGS\=NO
    export SWIFT_ENABLE_EXPLICIT_MODULES\=YES
    export SWIFT_OPTIMIZATION_LEVEL\=-Onone
    export SWIFT_PLATFORM_TARGET_PREFIX\=ios
    export SWIFT_RESPONSE_FILE_PATH_normal_arm64\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftFileList
    export SWIFT_VERSION\=5.0
    export SYMROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products
    export SYSTEM_ADMIN_APPS_DIR\=/Applications/Utilities
    export SYSTEM_APPS_DIR\=/Applications
    export SYSTEM_CORE_SERVICES_DIR\=/System/Library/CoreServices
    export SYSTEM_DEMOS_DIR\=/Applications/Extras
    export SYSTEM_DEVELOPER_APPS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications
    export SYSTEM_DEVELOPER_BIN_DIR\=/Applications/Xcode.app/Contents/Developer/usr/bin
    export SYSTEM_DEVELOPER_DEMOS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Utilities/Built\ Examples
    export SYSTEM_DEVELOPER_DIR\=/Applications/Xcode.app/Contents/Developer
    export SYSTEM_DEVELOPER_DOC_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library
    export SYSTEM_DEVELOPER_GRAPHICS_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Graphics\ Tools
    export SYSTEM_DEVELOPER_JAVA_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Java\ Tools
    export SYSTEM_DEVELOPER_PERFORMANCE_TOOLS_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Performance\ Tools
    export SYSTEM_DEVELOPER_RELEASENOTES_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library/releasenotes
    export SYSTEM_DEVELOPER_TOOLS\=/Applications/Xcode.app/Contents/Developer/Tools
    export SYSTEM_DEVELOPER_TOOLS_DOC_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library/documentation/DeveloperTools
    export SYSTEM_DEVELOPER_TOOLS_RELEASENOTES_DIR\=/Applications/Xcode.app/Contents/Developer/ADC\ Reference\ Library/releasenotes/DeveloperTools
    export SYSTEM_DEVELOPER_USR_DIR\=/Applications/Xcode.app/Contents/Developer/usr
    export SYSTEM_DEVELOPER_UTILITIES_DIR\=/Applications/Xcode.app/Contents/Developer/Applications/Utilities
    export SYSTEM_DEXT_INSTALL_PATH\=/System/Library/DriverExtensions
    export SYSTEM_DOCUMENTATION_DIR\=/Library/Documentation
    export SYSTEM_EXTENSIONS_FOLDER_PATH\=iosApp.app/SystemExtensions
    export SYSTEM_EXTENSIONS_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/Library/SystemExtensions
    export SYSTEM_EXTENSIONS_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app/SystemExtensions
    export SYSTEM_KEXT_INSTALL_PATH\=/System/Library/Extensions
    export SYSTEM_LIBRARY_DIR\=/System/Library
    export TAPI_DEMANGLE\=YES
    export TAPI_ENABLE_PROJECT_HEADERS\=NO
    export TAPI_LANGUAGE\=objective-c
    export TAPI_LANGUAGE_STANDARD\=compiler-default
    export TAPI_USE_SRCROOT\=YES
    export TAPI_VERIFY_MODE\=Pedantic
    export TARGETED_DEVICE_FAMILY\=1,2
    export TARGETNAME\=iosApp
    export TARGET_BUILD_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator
    export TARGET_DEVICE_IDENTIFIER\=31C834BE-271E-46D7-A53C-5338368FBF89
    export TARGET_DEVICE_MODEL\=iPhone18,1
    export TARGET_DEVICE_OS_VERSION\=26.5
    export TARGET_DEVICE_PLATFORM_NAME\=iphonesimulator
    export TARGET_NAME\=iosApp
    export TARGET_TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_FILES_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_FILE_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build
    export TEMP_ROOT\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex
    export TEMP_SANDBOX_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/TemporaryTaskSandboxes
    export TEST_FRAMEWORK_SEARCH_PATHS\=\ /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Library/Frameworks\ /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/Developer/Library/Frameworks
    export TEST_LIBRARY_SEARCH_PATHS\=\ /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/lib
    export TOOLCHAINS\=com.apple.dt.toolchain.XcodeDefault
    export TOOLCHAIN_DIR\=/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain
    export TREAT_MISSING_BASELINES_AS_TEST_FAILURES\=NO
    export TREAT_MISSING_SCRIPT_PHASE_OUTPUTS_AS_ERRORS\=NO
    export TeamIdentifierPrefix\=S2QF6L6CJZ.
    export UID\=501
    export UNINSTALLED_PRODUCTS_DIR\=/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/UninstalledProducts
    export UNLOCALIZED_RESOURCES_FOLDER_PATH\=iosApp.app
    export UNLOCALIZED_RESOURCES_FOLDER_PATH_SHALLOW_BUNDLE_NO\=iosApp.app/Resources
    export UNLOCALIZED_RESOURCES_FOLDER_PATH_SHALLOW_BUNDLE_YES\=iosApp.app
    export UNSTRIPPED_PRODUCT\=NO
    export USER\=mood
    export USER_APPS_DIR\=/Users/mood/Applications
    export USER_LIBRARY_DIR\=/Users/mood/Library
    export USE_DYNAMIC_NO_PIC\=YES
    export USE_HEADERMAP\=YES
    export USE_HEADER_SYMLINKS\=NO
    export VALIDATE_DEVELOPMENT_ASSET_PATHS\=YES_ERROR
    export VALIDATE_PRODUCT\=NO
    export VALID_ARCHS\=arm64\ x86_64
    export VERBOSE_PBXCP\=NO
    export VERSIONPLIST_PATH\=iosApp.app/version.plist
    export VERSION_INFO_BUILDER\=mood
    export VERSION_INFO_FILE\=iosApp_vers.c
    export VERSION_INFO_STRING\=\"@\(\#\)PROGRAM:iosApp\ \ PROJECT:iosApp-\"
    export WORKSPACE_DIR\=/Users/mood/POSE/android-poc/iosApp
    export WRAPPER_EXTENSION\=app
    export WRAPPER_NAME\=iosApp.app
    export WRAPPER_SUFFIX\=.app
    export WRAP_ASSET_PACKS_IN_SEPARATE_DIRECTORIES\=NO
    export XCODE_APP_SUPPORT_DIR\=/Applications/Xcode.app/Contents/Developer/Library/Xcode
    export XCODE_PRODUCT_BUILD_VERSION\=17F42
    export XCODE_VERSION_ACTUAL\=2650
    export XCODE_VERSION_MAJOR\=2600
    export XCODE_VERSION_MINOR\=2650
    export XPCSERVICES_FOLDER_PATH\=iosApp.app/XPCServices
    export YACC\=yacc
    export _DISCOVER_COMMAND_LINE_LINKER_INPUTS\=YES
    export _DISCOVER_COMMAND_LINE_LINKER_INPUTS_INCLUDE_WL\=YES
    export _LD_MULTIARCH\=YES
    export _WRAPPER_CONTENTS_DIR_SHALLOW_BUNDLE_NO\=/Contents
    export _WRAPPER_PARENT_PATH_SHALLOW_BUNDLE_NO\=/..
    export _WRAPPER_RESOURCES_DIR_SHALLOW_BUNDLE_NO\=/Resources
    export __DIAGNOSE_DEPRECATED_ARCHS\=YES
    export __IS_NOT_MACOS\=YES
    export __IS_NOT_MACOS_macosx\=NO
    export __IS_NOT_SIMULATOR\=NO
    export __IS_NOT_SIMULATOR_simulator\=NO
    export __ORIGINAL_SDK_DEFINED_LLVM_TARGET_TRIPLE_SYS\=ios
    export arch\=undefined_arch
    export variant\=normal
    /bin/sh -c /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Script-FA434838CD8F99D705F78E11.sh

Using JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
> Task :build-logic:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :build-logic:compileKotlin UP-TO-DATE
> Task :build-logic:compileJava NO-SOURCE
> Task :build-logic:pluginDescriptors UP-TO-DATE
> Task :build-logic:processResources UP-TO-DATE
> Task :build-logic:classes UP-TO-DATE
> Task :build-logic:jar UP-TO-DATE
> Task :feature:shell:checkSandboxAndWriteProtection
> Task :shared:kmpPartiallyResolvedDependenciesChecker
> Task :shared:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :shared:downloadKotlinNativeDistribution UP-TO-DATE
> Task :shared:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :core:model:kmpPartiallyResolvedDependenciesChecker
> Task :core:model:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:model:downloadKotlinNativeDistribution UP-TO-DATE
> Task :core:model:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :core:training-engine:kmpPartiallyResolvedDependenciesChecker
> Task :core:training-engine:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:training-engine:downloadKotlinNativeDistribution UP-TO-DATE
> Task :core:training-engine:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:account:checkIosSimulatorArm64MainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0
    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :feature:account:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :feature:home:checkIosSimulatorArm64MainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0
    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :feature:home:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :feature:explore:checkIosSimulatorArm64MainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0
    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :feature:explore:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :feature:library:checkIosSimulatorArm64MainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0
    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :feature:library:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :core:resources:checkIosSimulatorArm64MainComposeLibrariesCompatibility

> Task :core:designsystem:checkIosSimulatorArm64MainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0
    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :core:designsystem:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :core:network:kmpPartiallyResolvedDependenciesChecker
> Task :core:network:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:network:downloadKotlinNativeDistribution UP-TO-DATE
> Task :core:network:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:reports:checkIosSimulatorArm64MainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0
    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :feature:reports:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :core:resources:kmpPartiallyResolvedDependenciesChecker
> Task :core:resources:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:pose-capture:kmpPartiallyResolvedDependenciesChecker
> Task :core:pose-capture:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:resources:downloadKotlinNativeDistribution UP-TO-DATE
> Task :core:resources:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :core:resources:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :core:resources:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :core:resources:generateResourceAccessorsForAppleMain NO-SOURCE
> Task :shared:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :core:resources:convertXmlValueResourcesForCommonMain UP-TO-DATE
> Task :core:pose-capture:downloadKotlinNativeDistribution UP-TO-DATE
> Task :core:resources:copyNonXmlValueResourcesForCommonMain UP-TO-DATE
> Task :core:model:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :core:resources:prepareComposeResourcesTaskForCommonMain UP-TO-DATE
> Task :core:resources:generateResourceAccessorsForCommonMain UP-TO-DATE
> Task :core:resources:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :core:resources:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :core:resources:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :core:resources:generateResourceAccessorsForIosMain NO-SOURCE
> Task :core:resources:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :core:resources:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :core:resources:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE
> Task :core:resources:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE
> Task :core:resources:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :core:resources:copyNonXmlValueResourcesForNativeMain NO-SOURCE
> Task :core:resources:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :core:resources:generateResourceAccessorsForNativeMain NO-SOURCE
> Task :core:resources:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE
> Task :core:resources:generateComposeResClass UP-TO-DATE
> Task :core:resources:generateExpectResourceCollectorsForCommonMain UP-TO-DATE
> Task :core:resources:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :core:resources:generateMovitEnglishStrings UP-TO-DATE
> Task :feature:library:kmpPartiallyResolvedDependenciesChecker
> Task :feature:home:kmpPartiallyResolvedDependenciesChecker
> Task :core:pose-capture:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :feature:home:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :feature:reports:kmpPartiallyResolvedDependenciesChecker
> Task :feature:library:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :feature:explore:kmpPartiallyResolvedDependenciesChecker
> Task :feature:explore:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :feature:account:kmpPartiallyResolvedDependenciesChecker
> Task :core:data:kmpPartiallyResolvedDependenciesChecker
> Task :feature:account:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:data:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :feature:reports:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :core:designsystem:kmpPartiallyResolvedDependenciesChecker
> Task :core:designsystem:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :feature:library:downloadKotlinNativeDistribution UP-TO-DATE
> Task :feature:library:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:library:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:library:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :feature:library:generateResourceAccessorsForAppleMain SKIPPED
> Task :core:training-engine:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:home:downloadKotlinNativeDistribution UP-TO-DATE
> Task :feature:library:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:explore:downloadKotlinNativeDistribution UP-TO-DATE
> Task :feature:home:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:home:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:home:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :feature:account:downloadKotlinNativeDistribution UP-TO-DATE
> Task :feature:home:generateResourceAccessorsForAppleMain NO-SOURCE
> Task :core:data:downloadKotlinNativeDistribution UP-TO-DATE
> Task :feature:explore:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:account:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:reports:downloadKotlinNativeDistribution UP-TO-DATE
> Task :feature:library:copyNonXmlValueResourcesForCommonMain NO-SOURCE
> Task :core:network:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:explore:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:home:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:reports:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:account:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:home:copyNonXmlValueResourcesForCommonMain NO-SOURCE
> Task :core:designsystem:downloadKotlinNativeDistribution UP-TO-DATE
> Task :feature:library:prepareComposeResourcesTaskForCommonMain NO-SOURCE
> Task :feature:library:generateResourceAccessorsForCommonMain SKIPPED
> Task :feature:explore:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :feature:reports:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:account:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :core:designsystem:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:home:prepareComposeResourcesTaskForCommonMain NO-SOURCE
> Task :feature:library:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:account:generateResourceAccessorsForAppleMain NO-SOURCE
> Task :core:designsystem:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:explore:generateResourceAccessorsForAppleMain NO-SOURCE
> Task :feature:reports:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :core:designsystem:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :feature:explore:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:home:generateResourceAccessorsForCommonMain NO-SOURCE
> Task :core:designsystem:generateResourceAccessorsForAppleMain NO-SOURCE
> Task :feature:home:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :core:pose-capture:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:account:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:explore:copyNonXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:library:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:reports:generateResourceAccessorsForAppleMain NO-SOURCE
> Task :feature:home:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:account:copyNonXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:library:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :feature:library:generateResourceAccessorsForIosMain SKIPPED
> Task :feature:explore:prepareComposeResourcesTaskForCommonMain NO-SOURCE
> Task :core:resources:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :core:designsystem:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:account:prepareComposeResourcesTaskForCommonMain NO-SOURCE
> Task :feature:home:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :feature:library:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:explore:generateResourceAccessorsForCommonMain NO-SOURCE
> Task :feature:reports:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:library:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:explore:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:home:generateResourceAccessorsForIosMain NO-SOURCE
> Task :core:designsystem:copyNonXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:account:generateResourceAccessorsForCommonMain NO-SOURCE
> Task :feature:explore:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:library:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE
> Task :feature:reports:copyNonXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:library:generateResourceAccessorsForIosSimulatorArm64Main SKIPPED
> Task :feature:account:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :core:designsystem:prepareComposeResourcesTaskForCommonMain NO-SOURCE
> Task :feature:home:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:explore:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :feature:account:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:library:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:reports:prepareComposeResourcesTaskForCommonMain NO-SOURCE
> Task :core:designsystem:generateResourceAccessorsForCommonMain NO-SOURCE
> Task :feature:explore:generateResourceAccessorsForIosMain NO-SOURCE
> Task :feature:home:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:library:copyNonXmlValueResourcesForNativeMain NO-SOURCE
> Task :core:designsystem:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:reports:generateResourceAccessorsForCommonMain NO-SOURCE
> Task :feature:account:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :core:data:generateCommonMainMovitDatabaseInterface UP-TO-DATE

> Task :feature:train:checkIosSimulatorArm64MainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0
    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :feature:train:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :feature:explore:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :core:data:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :feature:library:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :feature:library:generateResourceAccessorsForNativeMain SKIPPED
> Task :feature:library:generateActualResourceCollectorsForIosSimulatorArm64Main SKIPPED
> Task :feature:library:generateComposeResClass SKIPPED
> Task :feature:library:generateExpectResourceCollectorsForCommonMain SKIPPED
> Task :feature:library:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :core:designsystem:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:home:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :feature:training:checkIosSimulatorArm64MainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0
    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :feature:training:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :feature:reports:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:account:generateResourceAccessorsForIosMain NO-SOURCE
> Task :core:designsystem:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :feature:home:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE
> Task :feature:explore:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:reports:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:account:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :core:designsystem:generateResourceAccessorsForIosMain NO-SOURCE
> Task :feature:home:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:explore:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE
> Task :feature:account:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:home:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:training-debug:checkIosSimulatorArm64MainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0
    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :feature:training-debug:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :core:designsystem:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:explore:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE
> Task :feature:reports:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :feature:account:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE
> Task :feature:train:kmpPartiallyResolvedDependenciesChecker
> Task :feature:train:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :feature:home:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :core:designsystem:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:explore:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:reports:generateResourceAccessorsForIosMain NO-SOURCE
> Task :feature:account:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE
> Task :feature:home:generateResourceAccessorsForNativeMain NO-SOURCE
> Task :core:designsystem:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE
> Task :feature:explore:copyNonXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:home:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE
> Task :feature:reports:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:home:generateComposeResClass UP-TO-DATE
> Task :feature:account:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:home:generateExpectResourceCollectorsForCommonMain UP-TO-DATE
> Task :core:designsystem:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE
> Task :feature:explore:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :feature:home:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :feature:reports:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:account:copyNonXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:train:downloadKotlinNativeDistribution UP-TO-DATE
> Task :core:designsystem:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:explore:generateResourceAccessorsForNativeMain NO-SOURCE
> Task :core:resources:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:account:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :feature:reports:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE
> Task :core:designsystem:copyNonXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:reports:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE
> Task :feature:explore:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE
> Task :feature:account:generateResourceAccessorsForNativeMain NO-SOURCE
> Task :core:designsystem:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :feature:reports:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :core:resources:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:train:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:explore:generateComposeResClass UP-TO-DATE
> Task :feature:reports:copyNonXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:train:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :core:designsystem:generateResourceAccessorsForNativeMain NO-SOURCE
> Task :feature:account:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE
> Task :feature:explore:generateExpectResourceCollectorsForCommonMain UP-TO-DATE
> Task :feature:train:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :feature:reports:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :feature:account:generateComposeResClass UP-TO-DATE
> Task :core:resources:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :feature:train:generateResourceAccessorsForAppleMain NO-SOURCE
> Task :core:designsystem:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE
> Task :feature:account:generateExpectResourceCollectorsForCommonMain UP-TO-DATE
> Task :feature:reports:generateResourceAccessorsForNativeMain NO-SOURCE
> Task :core:designsystem:generateComposeResClass UP-TO-DATE
> Task :feature:train:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:explore:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :core:designsystem:generateExpectResourceCollectorsForCommonMain UP-TO-DATE
> Task :feature:train:copyNonXmlValueResourcesForCommonMain NO-SOURCE
> Task :core:resources:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:train:prepareComposeResourcesTaskForCommonMain NO-SOURCE
> Task :core:data:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:account:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :core:designsystem:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :feature:explore:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:reports:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE
> Task :feature:explore:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:reports:generateComposeResClass UP-TO-DATE
> Task :feature:explore:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :feature:account:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :core:resources:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:home:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:train:generateResourceAccessorsForCommonMain NO-SOURCE
> Task :feature:explore:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:account:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:explore:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:home:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :core:resources:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:train:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:reports:generateExpectResourceCollectorsForCommonMain UP-TO-DATE
> Task :feature:account:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :feature:home:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :feature:explore:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:train:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:reports:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :feature:training-debug:kmpPartiallyResolvedDependenciesChecker
> Task :feature:training-debug:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :feature:library:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:account:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:reports:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:library:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:train:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :feature:home:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:train:generateResourceAccessorsForIosMain NO-SOURCE
> Task :feature:account:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:train:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:home:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:library:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :feature:train:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:home:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:reports:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :core:resources:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :feature:account:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:library:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:train:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE
> Task :core:resources:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:reports:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :feature:account:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :feature:explore:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :feature:train:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE
> Task :core:resources:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:train:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:account:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:train:copyNonXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:library:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:account:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:reports:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:explore:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:home:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :feature:library:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:train:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :feature:reports:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:train:generateResourceAccessorsForNativeMain NO-SOURCE
> Task :feature:library:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :feature:home:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:train:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE
> Task :feature:explore:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:home:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:reports:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:train:generateComposeResClass UP-TO-DATE
> Task :feature:train:generateExpectResourceCollectorsForCommonMain UP-TO-DATE
> Task :feature:reports:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :feature:library:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:train:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :feature:train:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:train:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:library:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:train:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :feature:reports:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:train:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:training-debug:downloadKotlinNativeDistribution UP-TO-DATE
> Task :feature:reports:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:train:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:training-debug:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:train:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:training-debug:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:train:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :core:designsystem:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:training:kmpPartiallyResolvedDependenciesChecker
> Task :feature:training:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :feature:training-debug:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :feature:training-debug:generateResourceAccessorsForAppleMain SKIPPED
> Task :feature:training-debug:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :core:designsystem:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:training-debug:copyNonXmlValueResourcesForCommonMain NO-SOURCE
> Task :core:designsystem:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:training-debug:prepareComposeResourcesTaskForCommonMain NO-SOURCE
> Task :feature:training-debug:generateResourceAccessorsForCommonMain SKIPPED
> Task :feature:training-debug:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :core:designsystem:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :core:designsystem:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:training-debug:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:training:downloadKotlinNativeDistribution UP-TO-DATE
> Task :feature:training-debug:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :feature:training-debug:generateResourceAccessorsForIosMain SKIPPED
> Task :core:designsystem:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :core:designsystem:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:training:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:training-debug:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:training:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:training-debug:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :core:designsystem:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :feature:training-debug:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE
> Task :feature:training-debug:generateResourceAccessorsForIosSimulatorArm64Main SKIPPED
> Task :core:designsystem:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:training:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :feature:training:generateResourceAccessorsForAppleMain SKIPPED
> Task :feature:training-debug:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :core:designsystem:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:training:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:library:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:training-debug:copyNonXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:training:copyNonXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:training-debug:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :feature:train:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:train:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:training:prepareComposeResourcesTaskForCommonMain NO-SOURCE
> Task :feature:training:generateResourceAccessorsForCommonMain SKIPPED
> Task :feature:training-debug:generateResourceAccessorsForNativeMain SKIPPED
> Task :feature:explore:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:training:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:home:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:training-debug:generateActualResourceCollectorsForIosSimulatorArm64Main SKIPPED
> Task :feature:training-debug:generateComposeResClass SKIPPED
> Task :feature:training-debug:generateExpectResourceCollectorsForCommonMain SKIPPED
> Task :feature:training:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:train:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:training-debug:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :feature:training:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :feature:training:generateResourceAccessorsForIosMain SKIPPED
> Task :feature:training-debug:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:training:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:training-debug:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:training-debug:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :feature:training:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:reports:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:training-debug:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:training:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE
> Task :feature:training-debug:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:training:generateResourceAccessorsForIosSimulatorArm64Main SKIPPED
> Task :feature:training-debug:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:training:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:training:copyNonXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:training-debug:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :feature:training:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :feature:training:generateResourceAccessorsForNativeMain SKIPPED
> Task :feature:training-debug:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:training:generateActualResourceCollectorsForIosSimulatorArm64Main SKIPPED
> Task :feature:training:generateComposeResClass SKIPPED
> Task :feature:account:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:training-debug:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:training:generateExpectResourceCollectorsForCommonMain SKIPPED
> Task :feature:training:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :feature:shell:kmpPartiallyResolvedDependenciesChecker
> Task :feature:shell:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :feature:shell:checkIosSimulatorArm64MainComposeLibrariesCompatibility
w: Skiko dependencies' versions are incompatible.
    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0
    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6

This may lead to compilation errors or unexpected behavior at runtime.
Such version mismatch might be caused by dependency constraints in one of the included libraries.
You can inspect resulted dependencies tree via `./gradlew :feature:shell:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.
See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies

Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.
Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.


> Task :feature:shell:downloadKotlinNativeDistribution UP-TO-DATE
> Task :feature:shell:convertXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:shell:copyNonXmlValueResourcesForAppleMain NO-SOURCE
> Task :feature:shell:prepareComposeResourcesTaskForAppleMain NO-SOURCE
> Task :feature:shell:generateResourceAccessorsForAppleMain NO-SOURCE
> Task :feature:shell:convertXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:shell:copyNonXmlValueResourcesForCommonMain NO-SOURCE
> Task :feature:training:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:shell:prepareComposeResourcesTaskForCommonMain NO-SOURCE
> Task :feature:shell:generateResourceAccessorsForCommonMain NO-SOURCE
> Task :feature:shell:convertXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:training:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:shell:copyNonXmlValueResourcesForIosMain NO-SOURCE
> Task :feature:training:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:shell:prepareComposeResourcesTaskForIosMain NO-SOURCE
> Task :feature:training:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :feature:shell:generateResourceAccessorsForIosMain NO-SOURCE
> Task :feature:training:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:training:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:shell:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:training:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:training:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :feature:training:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:shell:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE
> Task :feature:training:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE
> Task :feature:shell:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE
> Task :feature:shell:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE
> Task :feature:shell:convertXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:shell:copyNonXmlValueResourcesForNativeMain NO-SOURCE
> Task :feature:shell:prepareComposeResourcesTaskForNativeMain NO-SOURCE
> Task :feature:shell:generateResourceAccessorsForNativeMain NO-SOURCE
> Task :feature:shell:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE
> Task :feature:shell:generateComposeResClass UP-TO-DATE
> Task :feature:shell:generateExpectResourceCollectorsForCommonMain UP-TO-DATE
> Task :feature:shell:generateMovitAndroidBuildMetadata UP-TO-DATE
> Task :feature:shell:createBuildSystemDirectory
> Task :feature:shell:symbolicLinkToAssembleDebugAppleFrameworkForXcodeIosSimulatorArm64 UP-TO-DATE
> Task :feature:shell:checkCanSyncComposeResourcesForIos
> Task :feature:shell:iosArm64ResolveResourcesFromDependencies UP-TO-DATE
> Task :feature:shell:convertXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:shell:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE
> Task :feature:training-debug:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:shell:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE
> Task :feature:shell:compileKotlinIosSimulatorArm64 UP-TO-DATE
> Task :feature:shell:linkDebugFrameworkIosSimulatorArm64
warning: Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-uikit-iossimulatorarm64/1.11.0/aeea22c7c26752dbb9c58e301abddbdaa388ac18/ui-uikit-iosSimulatorArm64Cinterop-utilsMain-1.11.0 can't be exported with -Xexport-library
warning: Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.skiko/skiko-iossimulatorarm64/0.144.6/92634069df9422761235b7d233f7255336ee42f6/skiko-iosSimulatorArm64Cinterop-uikitMain-0.144.6 can't be exported with -Xexport-library
warning: Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-iossimulatorarm64/3.1.2/5624eb603a7aac50ca163947b5225ab40959b6b2/ktor-network-iosSimulatorArm64Cinterop-networkMain-3.1.2 can't be exported with -Xexport-library
warning: Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-iossimulatorarm64/3.1.2/753736eeda055f67ac2dfa590b243a1fff9435e1/ktor-network-iosSimulatorArm64Cinterop-unMain-3.1.2 can't be exported with -Xexport-library
warning: Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-utils-iossimulatorarm64/3.1.2/192389250198de1d59013163cbfde26c9a08dd57/ktor-utils-iosSimulatorArm64Cinterop-threadUtilsMain-3.1.2 can't be exported with -Xexport-library
warning: Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-io-iossimulatorarm64/3.1.2/9da2d679c2b1a62073fe64a375385a2a37c1114a/ktor-io-iosSimulatorArm64Cinterop-mutexMain-3.1.2 can't be exported with -Xexport-library
warning: Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/sqliter-driver-iossimulatorarm64/1.3.3/36df21ed52cc81daea5ef283b62882d04368953c/sqliter-driver-cinterop-sqlite3 can't be exported with -Xexport-library
warning: Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/atomicfu-iossimulatorarm64/0.31.0/561273c851e78a473b948c9d91a297bd514aca94/atomicfu-iosSimulatorArm64Cinterop-interopMain-0.31.0 can't be exported with -Xexport-library
warning: Following libraries are specified to be exported with -Xexport-library, but not included to the build:
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.navigationevent/navigationevent-iossimulatorarm64/1.0.1/78e44ac5a1e73b6cd937c579f1f3ab0789d68f1d/navigationevent-iosSimulatorArm64Main-1.0.1.klib

Included libraries:
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/common/stdlib
/Users/mood/POSE/android-poc/feature/account/build/classes/kotlin/iosSimulatorArm64/main/klib/account
/Users/mood/POSE/android-poc/feature/explore/build/classes/kotlin/iosSimulatorArm64/main/klib/explore
/Users/mood/POSE/android-poc/feature/home/build/classes/kotlin/iosSimulatorArm64/main/klib/home
/Users/mood/POSE/android-poc/feature/train/build/classes/kotlin/iosSimulatorArm64/main/klib/train
/Users/mood/POSE/android-poc/feature/training-debug/build/classes/kotlin/iosSimulatorArm64/main/klib/training-debug
/Users/mood/POSE/android-poc/feature/training/build/classes/kotlin/iosSimulatorArm64/main/klib/training
/Users/mood/POSE/android-poc/feature/reports/build/classes/kotlin/iosSimulatorArm64/main/klib/reports
/Users/mood/POSE/android-poc/feature/library/build/classes/kotlin/iosSimulatorArm64/main/klib/library
/Users/mood/POSE/android-poc/core/data/build/classes/kotlin/iosSimulatorArm64/main/klib/data
/Users/mood/POSE/android-poc/core/designsystem/build/classes/kotlin/iosSimulatorArm64/main/klib/designsystem
/Users/mood/POSE/android-poc/core/model/build/classes/kotlin/iosSimulatorArm64/main/klib/model
/Users/mood/POSE/android-poc/core/network/build/classes/kotlin/iosSimulatorArm64/main/klib/network
/Users/mood/POSE/android-poc/core/pose-capture/build/classes/kotlin/iosSimulatorArm64/main/klib/pose-capture
/Users/mood/POSE/android-poc/core/training-engine/build/classes/kotlin/iosSimulatorArm64/main/klib/training-engine
/Users/mood/POSE/android-poc/shared/build/classes/kotlin/iosSimulatorArm64/main/klib/shared
/Users/mood/POSE/android-poc/core/resources/build/classes/kotlin/iosSimulatorArm64/main/klib/resources
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material3/material3-uikitsimarm64/1.9.0/ed530723d6e01374ad405d5bd88404bd3164c0a5/material3-uikitSimArm64Main-1.9.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.components/components-resources-iosSimulatorArm64/1.11.0/9b5310a9f968d5b393b1195b0e8129cdee93383d/library-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material/material-ripple-uikitsimarm64/1.9.1/2da4a0d1b8e9d24d4ea47aa639365231ffe1f873/material-ripple-uikitSimArm64Main-1.9.1.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.animation/animation-core-iossimulatorarm64/1.11.0/c2339745e105c2b4634c1770a3fcb0824f6808be/animation-core-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.animation/animation-iossimulatorarm64/1.11.0/d6457a82457b297e7d40344014cd49c82fd69920/animation-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.foundation/foundation-layout-iossimulatorarm64/1.11.0/696a9c09ea51410a76c50bf299b7f80ea50d4918/foundation-layout-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-compose-iossimulatorarm64/3.4.0/3bb1232d9cfbc8c38ca22b0e10127b3e518dc11f/coil-compose-iosSimulatorArm64Main-3.4.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-compose-core-iossimulatorarm64/3.4.0/f7180306005b748757c5fa9b141a991043419ee1/coil-compose-core-iosSimulatorArm64Main-3.4.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.foundation/foundation-iossimulatorarm64/1.11.0/f87a58657b2404ffd782aa23c2e190e78ad37fb1/foundation-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material/material-icons-extended-uikitsimarm64/1.7.3/480b5a73fc240e91b4c6a194542aaf152d354948/material-icons-extended.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.androidx.navigationevent/navigationevent-compose-uikitsimarm64/1.0.1/96d82f05d741f45a571da03ada256a50ce04db64/navigationevent-compose-uikitSimArm64Main-1.0.1.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material/material-icons-core-uikitsimarm64/1.7.3/3c0863fb50d05945a767705714bf4235c9a26c86/material-icons-core.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-unit-iossimulatorarm64/1.11.0/6f5ca544ae8b394bbee26b0520fcfaf477cbe135/ui-unit-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-geometry-iossimulatorarm64/1.11.0/49097ab3b785d76efde0452bdf558113cbe5a7cb/ui-geometry-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-graphics-iossimulatorarm64/1.11.0/3a89b4a23fca3e10bc37aa5a78cdb3d86719e653/ui-graphics-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-backhandler-iossimulatorarm64/1.11.0/21cecdc4c04327c28aab5414cb403f3753dcaa92/ui-backhandler-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-util-iossimulatorarm64/1.11.0/f24afd7715863d67c11b24a9f2380c03591df2d5/ui-util-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-uikit-iossimulatorarm64/1.11.0/ebea9e9e2a20bdf1a9948937a78f2d247e18c93b/ui-uikit-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-uikit-iossimulatorarm64/1.11.0/aeea22c7c26752dbb9c58e301abddbdaa388ac18/ui-uikit-iosSimulatorArm64Cinterop-utilsMain-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-text-iossimulatorarm64/1.11.0/3a31968d481da9fec01f5502ace752225a6ed823/ui-text-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-iossimulatorarm64/1.11.0/3d9b500dc1bb86312b45f223ffd30e1060d26554/ui-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.navigationevent/navigationevent-iossimulatorarm64/1.0.2/78e44ac5a1e73b6cd937c579f1f3ab0789d68f1d/navigationevent-iosSimulatorArm64Main-1.0.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-runtime-iossimulatorarm64/2.11.0-beta01/39c80f51c066007e0123ba281503b7b9cd498d62/lifecycle-runtime-iosSimulatorArm64Main-2.11.0-beta01.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.androidx.savedstate/savedstate-compose-uikitsimarm64/1.3.6/e7a851331135e0f28b36ace3f4db253e8cabb2d1/savedstate-compose-uikitSimArm64Main-1.3.6.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.savedstate/savedstate-compose-iossimulatorarm64/1.4.0/6d093b163c8a71ac0d8684a2fe6dbaf79f1e450/savedstate-compose-iosSimulatorArm64Main-1.4.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.savedstate/savedstate-iossimulatorarm64/1.4.0/559d3801e3f2e99c3ad28af4dbfc9a732fba7499/savedstate-iosSimulatorArm64Main-1.4.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-common-iossimulatorarm64/2.11.0-beta01/1196fe43a0b67fa121233423790654ef453113a/lifecycle-common-iosSimulatorArm64Main-2.11.0-beta01.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-savedstate-iossimulatorarm64/2.11.0-beta01/576ed846acb8d14b49af41748686ce2b7b7be282/lifecycle-viewmodel-savedstate-iosSimulatorArm64Main-2.11.0-beta01.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-runtime-compose-iossimulatorarm64/2.11.0-beta01/bff7c3f7778716c200ecd3689820a2e8de9d6229/lifecycle-runtime-compose-iosSimulatorArm64Main-2.11.0-beta01.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-iossimulatorarm64/2.11.0-beta01/147f984a1e0d3721dc129fbdfb430cb86b4c0af9/lifecycle-viewmodel-iosSimulatorArm64Main-2.11.0-beta01.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-compose-iossimulatorarm64/2.11.0-beta01/4cc65bd91c2cf34e6a7b5c98f791ff66bfa3ec/lifecycle-viewmodel-compose-iosSimulatorArm64Main-2.11.0-beta01.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-saveable-iossimulatorarm64/1.11.1/85c76f454cd428c1b698585b9c42db86d2dca247/runtime-saveable-iosSimulatorArm64Main-1.11.1.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-annotation-iossimulatorarm64/1.11.1/7c02fce3da8d3de4b84a67784537fbee9509d121/runtime-annotation-iosSimulatorArm64Main-1.11.1.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-retain-iossimulatorarm64/1.11.1/344000c978daec8831e73596dc211e0380389446/runtime-retain-iosSimulatorArm64Main-1.11.1.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-iossimulatorarm64/1.11.1/a05d7729f7cc5e9a0101e1d79a676b48e94c0fa7/runtime-iosSimulatorArm64Main-1.11.1.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.runtime/runtime-iossimulatorarm64/1.11.0/6759c45fc47fe0381292bf8cc941f9f2756f238d/runtime-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.runtime/runtime-saveable-iossimulatorarm64/1.11.0/53f68eba65bb87cc9b3274d8e2609c0b2c7fda8d/runtime-saveable-iosSimulatorArm64Main-1.11.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.androidx.lifecycle/lifecycle-runtime-compose-iossimulatorarm64/2.11.0-beta01/ed4a543af5c8bf09f38a765377908fd8478c6db8/lifecycle-runtime-compose-iosSimulatorArm64Main-2.11.0-beta01.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.androidx.lifecycle/lifecycle-viewmodel-compose-iossimulatorarm64/2.11.0-beta01/2ddad151cabdf37a397b2c1cd24a5c76f0aa867f/lifecycle-viewmodel-compose-iosSimulatorArm64Main-2.11.0-beta01.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-client-auth-iossimulatorarm64/3.1.2/2ac62b8075e149111555ac0fc70e4ed01abe348c/ktor-client-auth-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-client-content-negotiation-iossimulatorarm64/3.1.2/bacf76a3256356c57be44c5be24ff18b5995cef8/ktor-client-content-negotiation-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-client-logging-iossimulatorarm64/3.1.2/e01f00334a22ac6b4fa44eeb50a1d95cfacaa6fa/ktor-client-logging-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-client-darwin-iossimulatorarm64/3.1.2/e23a3ec972745bbc0664d9fb55bd0c63c468cd70/ktor-client-darwin-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-network-ktor3-iossimulatorarm64/3.4.0/20cef39e452f6bd2eb5290621de595528fbbb0ac/coil-network-ktor3-iosSimulatorArm64Main-3.4.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-client-core-iossimulatorarm64/3.1.2/ddbb3ca85274e498bab13eecd867a896cf0d16de/ktor-client-core-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/app.cash.sqldelight/coroutines-extensions-iossimulatorarm64/2.1.0/c2b54db8a82f85aa44b6d753e470c0168679fd60/coroutines-extensions.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-serialization-kotlinx-json-iossimulatorarm64/3.1.2/669fa4eeb48a57cd80b8516dbce822e812418c6c/ktor-serialization-kotlinx-json-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-iossimulatorarm64/3.4.0/72ccfbc53607909285ae5730306b16f0a8055712/coil-iosSimulatorArm64Main-3.4.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-network-core-iossimulatorarm64/3.4.0/5ee2cef964d406d5009e46035fc5f9eac8b11af1/coil-network-core-iosSimulatorArm64Main-3.4.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-core-iossimulatorarm64/3.4.0/2ba1bc203c5ad319b1041c0db2d3c9fd72afb5b1/coil-core-iosSimulatorArm64Main-3.4.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.skiko/skiko-iossimulatorarm64/0.144.6/9427f7a68fe50664d4523f89d17ff683487f7406/skiko-iosSimulatorArm64Main-0.144.6.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.skiko/skiko-iossimulatorarm64/0.144.6/92634069df9422761235b7d233f7255336ee42f6/skiko-iosSimulatorArm64Cinterop-uikitMain-0.144.6.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-http-cio-iossimulatorarm64/3.1.2/12c92ef35519cb2455344339b4810efcc54d9f90/ktor-http-cio-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-tls-iossimulatorarm64/3.1.2/45232f6e96951d75938b5e04d4ea6e7e153125ad/ktor-network-tls-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-websocket-serialization-iossimulatorarm64/3.1.2/ae6e1806693a9dbc0238523501eb0407a99cdaf2/ktor-websocket-serialization-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-serialization-kotlinx-iossimulatorarm64/3.1.2/e0fdfdf5f8be00327b1bf6a4514a0a6201a9ef01/ktor-serialization-kotlinx-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-serialization-iossimulatorarm64/3.1.2/2ef6bf10bd50253fc4194f18f7f4878c018548bb/ktor-serialization-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-websockets-iossimulatorarm64/3.1.2/d0d2f6c65042812c5bfe8a4af0952a44418483db/ktor-websockets-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-http-iossimulatorarm64/3.1.2/5a320bc7d792979dbb7953f7be5425384b4ac2b4/ktor-http-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-events-iossimulatorarm64/3.1.2/687e5e2b0f029b59cae1748b5a86cb4bbf22578d/ktor-events-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-sse-iossimulatorarm64/3.1.2/6ecb0fb7947d52c2cb483c2352fce42df6db4595/ktor-sse-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/app.cash.sqldelight/async-extensions-iossimulatorarm64/2.1.0/29f396bc3669b98f75bf6384c2d5c85b6610e0d/async-extensions.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-iossimulatorarm64/3.1.2/b8553b7be847319b69c37dace28cae8f8c886c42/ktor-network-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-iossimulatorarm64/3.1.2/5624eb603a7aac50ca163947b5225ab40959b6b2/ktor-network-iosSimulatorArm64Cinterop-networkMain-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-iossimulatorarm64/3.1.2/753736eeda055f67ac2dfa590b243a1fff9435e1/ktor-network-iosSimulatorArm64Cinterop-unMain-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-utils-iossimulatorarm64/3.1.2/ee03c5cca0bfb24cc21043e774ef3de52804ec7/ktor-utils-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-utils-iossimulatorarm64/3.1.2/192389250198de1d59013163cbfde26c9a08dd57/ktor-utils-iosSimulatorArm64Cinterop-threadUtilsMain-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-io-iossimulatorarm64/3.1.2/b1461cf8e9ef0f72007105042756b1500f97db21/ktor-io-iosSimulatorArm64Main-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-io-iossimulatorarm64/3.1.2/9da2d679c2b1a62073fe64a375385a2a37c1114a/ktor-io-iosSimulatorArm64Cinterop-mutexMain-3.1.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-iossimulatorarm64/1.10.2/dde92bb9e9950e96494c2787302c0cb66c7cd8c8/kotlinx-coroutines-core-iosSimulatorArm64Main-1.10.2.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-json-io-iossimulatorarm64/1.8.0/f93c0e7900c567cc3c775f1966f198661a9e33f1/kotlinx-serialization-json-io-iosSimulatorArm64Main-1.8.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-json-iossimulatorarm64/1.8.1/26cd88ed3bb275d7bbdf9828c6887d02205db0fb/kotlinx-serialization-json-iosSimulatorArm64Main-1.8.1.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/app.cash.sqldelight/native-driver-iossimulatorarm64/2.1.0/7fa12be3694ad6dfea49608b0f65094bc00a37e1/native-driver.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/app.cash.sqldelight/runtime-iossimulatorarm64/2.1.0/ab8403c1270a29534650ba555ed7bc91fd425e3f/runtime.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/io.insert-koin/koin-core-iossimulatorarm64/4.2.1/7239f69f35e160176c394cfc81937316ab64c53c/koin-core-iosSimulatorArm64Main-4.2.1.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.collection/collection-iossimulatorarm64/1.5.0/f80a98cb05dafb0368759210aa8510c029613bca/collection-iosSimulatorArm64Main-1.5.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-datetime-iossimulatorarm64/0.7.1/bb22d3b01ccd535827f88d99d1cbb337dc5cae4b/kotlinx-datetime-iosSimulatorArm64Main-0.7.1.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation-iossimulatorarm64/1.9.1/b879ab97d24ac69dda460a6f19f70762840583b2/annotation.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-core-iossimulatorarm64/1.8.1/6321d2e707cf075a97f53c9406635ff3d0e4fc0d/kotlinx-serialization-core-iosSimulatorArm64Main-1.8.1.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/stately-concurrent-collections-iossimulatorarm64/2.1.0/c81ec9f587fc7085a4eaf8d932471e7092c2a135/stately-concurrent-collections.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/stately-concurrency-iossimulatorarm64/2.1.0/7cc64802f88bad744e3a523d52e71734ef8eefaa/stately-concurrency.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/sqliter-driver-iossimulatorarm64/1.3.3/550ff0a78efdb784d1e73ee1e11729fdac7f3d50/sqliter-driver.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/sqliter-driver-iossimulatorarm64/1.3.3/36df21ed52cc81daea5ef283b62882d04368953c/sqliter-driver-cinterop-sqlite3.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/atomicfu-iossimulatorarm64/0.31.0/13553e91893f061792e08cfdb1d7f97254aad5a8/atomicfu-iosSimulatorArm64Main-0.31.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/atomicfu-iossimulatorarm64/0.31.0/561273c851e78a473b948c9d91a297bd514aca94/atomicfu-iosSimulatorArm64Cinterop-interopMain-0.31.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-io-okio-iossimulatorarm64/0.9.0/d89fb2cea5ab5c0f093b4a52ed5da9083ba9aae8/kotlinx-io-okio-iosSimulatorArm64Main-0.9.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/stately-strict-iossimulatorarm64/2.1.0/4e81855e04e1fe2617d9a520a927507b97e29393/stately-strict.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/com.squareup.okio/okio-iossimulatorarm64/3.16.4/4d6219ffbca29647f22f110cda55c03d833392e2/okio-iosSimulatorArm64Main-3.16.4.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-io-core-iossimulatorarm64/0.9.0/38e19d259efa80b6a8f8713a61f58a89ff06ab6b/kotlinx-io-core-iosSimulatorArm64Main-0.9.0.klib
/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-io-bytestring-iossimulatorarm64/0.9.0/e86999f3d612b2cedf620944a91f1d597c816f4c/kotlinx-io-bytestring-iosSimulatorArm64Main-0.9.0.klib
/Users/mood/POSE/android-poc/feature/shell/build/classes/kotlin/iosSimulatorArm64/main/klib/shell
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreFoundation
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Network
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.QuartzCore
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MLCompute
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SafetyKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Security
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CloudKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.VideoSubscriberAccount
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.LockedCameraCapture
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.OpenGLES3
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Accessibility
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GameKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AVFoundation
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CFNetwork
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.WebKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Metal
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreMotion
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.iconv
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Messages
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.LocalAuthenticationEmbeddedUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.OpenGLES2
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MediaToolbox
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ThreadNetwork
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SensitiveContentAnalysis
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.posix
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreHaptics
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ExtensionFoundation
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.UniformTypeIdentifiers
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AppTrackingTransparency
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.EventKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PHASE
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreAudio
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.StoreKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ContactsUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BrowserKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MultipeerConnectivity
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SafariServices
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MetricKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AddressBook
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform._CoreData_CloudKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SpriteKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreAudioKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreText
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Twitter
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.LocalAuthentication
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AutomaticAssessmentConfiguration
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreGraphics
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreData
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SceneKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.OpenGLESCommon
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ShazamKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ReplayKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.DataDetection
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SoundAnalysis
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Cinematic
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.QuickLookThumbnailing
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreMedia
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BackgroundAssets
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.iAd
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GameController
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ClockKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreSpotlight
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.zlib
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CFCGTypes
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PDFKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.UIKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.FileProviderUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MetalPerformanceShaders
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CarPlay
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Matter
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AppClip
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AlarmKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Contacts
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.OpenAL
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GameplayKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CommonCrypto
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PassKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AudioToolbox
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GLKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MediaAccessibility
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Vision
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MetalPerformanceShadersGraph
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.HomeKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreServices
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Speech
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ExposureNotification
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BackgroundTasks
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreAudioTypes
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SharedWithYou
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CallKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MediaSetup
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreML
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AVRouting
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AdSupport
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AVKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BrowserEngineCore
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ModelIO
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Social
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.HealthKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.NaturalLanguage
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CryptoTokenKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MapKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreLocationUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GameSave
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Intents
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.IOSurface
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MessageUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AssetsLibrary
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.EventKitUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.NetworkExtension
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AuthenticationServices
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ARKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.UserNotificationsUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.UserNotifications
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ClassKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Accounts
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BusinessChat
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AddressBookUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.VideoToolbox
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PushKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AccessorySetupKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Accelerate
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ImageCaptureCore
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.EAGL
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.DeviceCheck
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.builtin
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreMIDI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AVFAudio
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreVideo
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform._LocationEssentials
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.IntentsUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.WatchConnectivity
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.DeviceDiscoveryExtension
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.darwin
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PencilKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MobileCoreServices
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreLocation
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SecurityUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.objc
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.IdentityLookupUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreTelephony
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.NotificationCenter
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.FileProvider
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PushToTalk
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ExtensionKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ImageIO
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GSS
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.LinkPresentation
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ExternalAccessory
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AdServices
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SensorKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Foundation
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.IdentityLookup
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SystemConfiguration
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Photos
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreImage
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.OpenGLES
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.NearbyInteraction
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.UIUtilities
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreFoundationBase
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.TouchController
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ColorSync
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.JavaScriptCore
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreBluetooth
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Symbols
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MetalFX
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.HealthKitUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PhotosUI
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.QuickLook
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SharedWithYouCore
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreNFC
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MediaPlayer
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BrowserEngineKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MetalKit
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ScreenTime
/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.VisionKit

warning: Exposed type 'ComposableLambda' is 'Function2<Composer, Int, Any?>' and 'Function3<Any?, Composer, Int, Any?>' at the same time. This most likely wouldn't work as expected.
warning: Cannot infer a bundle ID from packages of source files and exported dependencies, use the bundle name instead: MovitApp. Please specify the bundle ID explicitly using the -Xbinary=bundleId=<id> compiler flag.

> Task :feature:shell:assembleDebugAppleFrameworkForXcodeIosSimulatorArm64
> Task :feature:shell:copyDsymForEmbedAndSignAppleFrameworkForXcode SKIPPED
> Task :feature:shell:assembleIosArm64MainResources UP-TO-DATE
> Task :feature:shell:iosArm64ResolveSelfResourcesCopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:shell:iosArm64AggregateResources UP-TO-DATE
> Task :feature:shell:iosSimulatorArm64ResolveResourcesFromDependencies UP-TO-DATE
> Task :feature:shell:assembleIosSimulatorArm64MainResources UP-TO-DATE
> Task :feature:shell:iosSimulatorArm64ResolveSelfResourcesCopyHierarchicalMultiplatformResources UP-TO-DATE
> Task :feature:shell:iosSimulatorArm64AggregateResources UP-TO-DATE
> Task :feature:shell:syncComposeResourcesForIos UP-TO-DATE
> Task :feature:shell:embedAndSignAppleFrameworkForXcode SKIPPED

BUILD SUCCESSFUL in 3m 7s
188 actionable tasks: 33 executed, 155 up-to-date

Using JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

> Task :build-logic:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :build-logic:compileKotlin UP-TO-DATE

> Task :build-logic:compileJava NO-SOURCE

> Task :build-logic:pluginDescriptors UP-TO-DATE

> Task :build-logic:processResources UP-TO-DATE

> Task :build-logic:classes UP-TO-DATE

> Task :build-logic:jar UP-TO-DATE

> Task :feature:shell:checkSandboxAndWriteProtection

> Task :shared:kmpPartiallyResolvedDependenciesChecker

> Task :shared:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :shared:downloadKotlinNativeDistribution UP-TO-DATE

> Task :shared:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :core:model:kmpPartiallyResolvedDependenciesChecker

> Task :core:model:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :core:model:downloadKotlinNativeDistribution UP-TO-DATE

> Task :core:model:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :core:training-engine:kmpPartiallyResolvedDependenciesChecker

> Task :core:training-engine:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :core:training-engine:downloadKotlinNativeDistribution UP-TO-DATE

> Task :core:training-engine:generateMovitAndroidBuildMetadata UP-TO-DATE



> Task :feature:account:checkIosSimulatorArm64MainComposeLibrariesCompatibility

w: Skiko dependencies' versions are incompatible.

    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0

    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6



This may lead to compilation errors or unexpected behavior at runtime.

Such version mismatch might be caused by dependency constraints in one of the included libraries.

You can inspect resulted dependencies tree via `./gradlew :feature:account:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.

See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies



Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.

Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.





> Task :feature:home:checkIosSimulatorArm64MainComposeLibrariesCompatibility

w: Skiko dependencies' versions are incompatible.

    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0

    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6



This may lead to compilation errors or unexpected behavior at runtime.

Such version mismatch might be caused by dependency constraints in one of the included libraries.

You can inspect resulted dependencies tree via `./gradlew :feature:home:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.

See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies



Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.

Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.





> Task :feature:explore:checkIosSimulatorArm64MainComposeLibrariesCompatibility

w: Skiko dependencies' versions are incompatible.

    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0

    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6



This may lead to compilation errors or unexpected behavior at runtime.

Such version mismatch might be caused by dependency constraints in one of the included libraries.

You can inspect resulted dependencies tree via `./gradlew :feature:explore:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.

See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies



Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.

Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.





> Task :feature:library:checkIosSimulatorArm64MainComposeLibrariesCompatibility

w: Skiko dependencies' versions are incompatible.

    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0

    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6



This may lead to compilation errors or unexpected behavior at runtime.

Such version mismatch might be caused by dependency constraints in one of the included libraries.

You can inspect resulted dependencies tree via `./gradlew :feature:library:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.

See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies



Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.

Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.





> Task :core:resources:checkIosSimulatorArm64MainComposeLibrariesCompatibility



> Task :core:designsystem:checkIosSimulatorArm64MainComposeLibrariesCompatibility

w: Skiko dependencies' versions are incompatible.

    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0

    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6



This may lead to compilation errors or unexpected behavior at runtime.

Such version mismatch might be caused by dependency constraints in one of the included libraries.

You can inspect resulted dependencies tree via `./gradlew :core:designsystem:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.

See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies



Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.

Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.





> Task :core:network:kmpPartiallyResolvedDependenciesChecker

> Task :core:network:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :core:network:downloadKotlinNativeDistribution UP-TO-DATE

> Task :core:network:generateMovitAndroidBuildMetadata UP-TO-DATE



> Task :feature:reports:checkIosSimulatorArm64MainComposeLibrariesCompatibility

w: Skiko dependencies' versions are incompatible.

    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0

    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6



This may lead to compilation errors or unexpected behavior at runtime.

Such version mismatch might be caused by dependency constraints in one of the included libraries.

You can inspect resulted dependencies tree via `./gradlew :feature:reports:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.

See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies



Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.

Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.





> Task :core:resources:kmpPartiallyResolvedDependenciesChecker

> Task :core:resources:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :core:pose-capture:kmpPartiallyResolvedDependenciesChecker

> Task :core:pose-capture:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :core:resources:downloadKotlinNativeDistribution UP-TO-DATE

> Task :core:resources:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :core:resources:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :core:resources:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :core:resources:generateResourceAccessorsForAppleMain NO-SOURCE

> Task :shared:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :core:resources:convertXmlValueResourcesForCommonMain UP-TO-DATE

> Task :core:pose-capture:downloadKotlinNativeDistribution UP-TO-DATE

> Task :core:resources:copyNonXmlValueResourcesForCommonMain UP-TO-DATE

> Task :core:model:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :core:resources:prepareComposeResourcesTaskForCommonMain UP-TO-DATE

> Task :core:resources:generateResourceAccessorsForCommonMain UP-TO-DATE

> Task :core:resources:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :core:resources:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :core:resources:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :core:resources:generateResourceAccessorsForIosMain NO-SOURCE

> Task :core:resources:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :core:resources:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :core:resources:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :core:resources:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE

> Task :core:resources:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :core:resources:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :core:resources:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :core:resources:generateResourceAccessorsForNativeMain NO-SOURCE

> Task :core:resources:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE

> Task :core:resources:generateComposeResClass UP-TO-DATE

> Task :core:resources:generateExpectResourceCollectorsForCommonMain UP-TO-DATE

> Task :core:resources:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :core:resources:generateMovitEnglishStrings UP-TO-DATE

> Task :feature:library:kmpPartiallyResolvedDependenciesChecker

> Task :feature:home:kmpPartiallyResolvedDependenciesChecker

> Task :core:pose-capture:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:home:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :feature:reports:kmpPartiallyResolvedDependenciesChecker

> Task :feature:library:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :feature:explore:kmpPartiallyResolvedDependenciesChecker

> Task :feature:explore:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :feature:account:kmpPartiallyResolvedDependenciesChecker

> Task :core:data:kmpPartiallyResolvedDependenciesChecker

> Task :feature:account:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :core:data:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :feature:reports:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :core:designsystem:kmpPartiallyResolvedDependenciesChecker

> Task :core:designsystem:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :feature:library:downloadKotlinNativeDistribution UP-TO-DATE

> Task :feature:library:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:library:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:library:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :feature:library:generateResourceAccessorsForAppleMain SKIPPED

> Task :core:training-engine:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:home:downloadKotlinNativeDistribution UP-TO-DATE

> Task :feature:library:convertXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:explore:downloadKotlinNativeDistribution UP-TO-DATE

> Task :feature:home:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:home:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:home:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :feature:account:downloadKotlinNativeDistribution UP-TO-DATE

> Task :feature:home:generateResourceAccessorsForAppleMain NO-SOURCE

> Task :core:data:downloadKotlinNativeDistribution UP-TO-DATE

> Task :feature:explore:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:account:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:reports:downloadKotlinNativeDistribution UP-TO-DATE

> Task :feature:library:copyNonXmlValueResourcesForCommonMain NO-SOURCE

> Task :core:network:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:explore:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:home:convertXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:reports:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:account:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:home:copyNonXmlValueResourcesForCommonMain NO-SOURCE

> Task :core:designsystem:downloadKotlinNativeDistribution UP-TO-DATE

> Task :feature:library:prepareComposeResourcesTaskForCommonMain NO-SOURCE

> Task :feature:library:generateResourceAccessorsForCommonMain SKIPPED

> Task :feature:explore:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :feature:reports:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:account:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :core:designsystem:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:home:prepareComposeResourcesTaskForCommonMain NO-SOURCE

> Task :feature:library:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:account:generateResourceAccessorsForAppleMain NO-SOURCE

> Task :core:designsystem:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:explore:generateResourceAccessorsForAppleMain NO-SOURCE

> Task :feature:reports:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :core:designsystem:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :feature:explore:convertXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:home:generateResourceAccessorsForCommonMain NO-SOURCE

> Task :core:designsystem:generateResourceAccessorsForAppleMain NO-SOURCE

> Task :feature:home:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :core:pose-capture:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:account:convertXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:explore:copyNonXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:library:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:reports:generateResourceAccessorsForAppleMain NO-SOURCE

> Task :feature:home:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:account:copyNonXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:library:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :feature:library:generateResourceAccessorsForIosMain SKIPPED

> Task :feature:explore:prepareComposeResourcesTaskForCommonMain NO-SOURCE

> Task :core:resources:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :core:designsystem:convertXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:account:prepareComposeResourcesTaskForCommonMain NO-SOURCE

> Task :feature:home:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :feature:library:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:explore:generateResourceAccessorsForCommonMain NO-SOURCE

> Task :feature:reports:convertXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:library:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:explore:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:home:generateResourceAccessorsForIosMain NO-SOURCE

> Task :core:designsystem:copyNonXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:account:generateResourceAccessorsForCommonMain NO-SOURCE

> Task :feature:explore:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:library:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :feature:reports:copyNonXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:library:generateResourceAccessorsForIosSimulatorArm64Main SKIPPED

> Task :feature:account:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :core:designsystem:prepareComposeResourcesTaskForCommonMain NO-SOURCE

> Task :feature:home:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:explore:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :feature:account:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:library:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:reports:prepareComposeResourcesTaskForCommonMain NO-SOURCE

> Task :core:designsystem:generateResourceAccessorsForCommonMain NO-SOURCE

> Task :feature:explore:generateResourceAccessorsForIosMain NO-SOURCE

> Task :feature:home:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:library:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :core:designsystem:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:reports:generateResourceAccessorsForCommonMain NO-SOURCE

> Task :feature:account:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :core:data:generateCommonMainMovitDatabaseInterface UP-TO-DATE



> Task :feature:train:checkIosSimulatorArm64MainComposeLibrariesCompatibility

w: Skiko dependencies' versions are incompatible.

    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0

    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6



This may lead to compilation errors or unexpected behavior at runtime.

Such version mismatch might be caused by dependency constraints in one of the included libraries.

You can inspect resulted dependencies tree via `./gradlew :feature:train:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.

See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies



Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.

Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.





> Task :feature:explore:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :core:data:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:library:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :feature:library:generateResourceAccessorsForNativeMain SKIPPED

> Task :feature:library:generateActualResourceCollectorsForIosSimulatorArm64Main SKIPPED

> Task :feature:library:generateComposeResClass SKIPPED

> Task :feature:library:generateExpectResourceCollectorsForCommonMain SKIPPED

> Task :feature:library:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :core:designsystem:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:home:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE



> Task :feature:training:checkIosSimulatorArm64MainComposeLibrariesCompatibility

w: Skiko dependencies' versions are incompatible.

    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0

    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6



This may lead to compilation errors or unexpected behavior at runtime.

Such version mismatch might be caused by dependency constraints in one of the included libraries.

You can inspect resulted dependencies tree via `./gradlew :feature:training:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.

See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies



Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.

Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.





> Task :feature:reports:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:account:generateResourceAccessorsForIosMain NO-SOURCE

> Task :core:designsystem:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :feature:home:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE

> Task :feature:explore:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:reports:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:account:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :core:designsystem:generateResourceAccessorsForIosMain NO-SOURCE

> Task :feature:home:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:explore:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :feature:account:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:home:copyNonXmlValueResourcesForNativeMain NO-SOURCE



> Task :feature:training-debug:checkIosSimulatorArm64MainComposeLibrariesCompatibility

w: Skiko dependencies' versions are incompatible.

    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0

    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6



This may lead to compilation errors or unexpected behavior at runtime.

Such version mismatch might be caused by dependency constraints in one of the included libraries.

You can inspect resulted dependencies tree via `./gradlew :feature:training-debug:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.

See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies



Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.

Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.





> Task :core:designsystem:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:explore:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE

> Task :feature:reports:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :feature:account:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :feature:train:kmpPartiallyResolvedDependenciesChecker

> Task :feature:train:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :feature:home:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :core:designsystem:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:explore:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:reports:generateResourceAccessorsForIosMain NO-SOURCE

> Task :feature:account:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE

> Task :feature:home:generateResourceAccessorsForNativeMain NO-SOURCE

> Task :core:designsystem:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :feature:explore:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:home:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE

> Task :feature:reports:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:home:generateComposeResClass UP-TO-DATE

> Task :feature:account:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:home:generateExpectResourceCollectorsForCommonMain UP-TO-DATE

> Task :core:designsystem:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE

> Task :feature:explore:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :feature:home:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:reports:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:account:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:train:downloadKotlinNativeDistribution UP-TO-DATE

> Task :core:designsystem:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:explore:generateResourceAccessorsForNativeMain NO-SOURCE

> Task :core:resources:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:account:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :feature:reports:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :core:designsystem:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:reports:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE

> Task :feature:explore:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE

> Task :feature:account:generateResourceAccessorsForNativeMain NO-SOURCE

> Task :core:designsystem:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :feature:reports:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :core:resources:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:train:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:explore:generateComposeResClass UP-TO-DATE

> Task :feature:reports:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:train:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :core:designsystem:generateResourceAccessorsForNativeMain NO-SOURCE

> Task :feature:account:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE

> Task :feature:explore:generateExpectResourceCollectorsForCommonMain UP-TO-DATE

> Task :feature:train:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :feature:reports:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :feature:account:generateComposeResClass UP-TO-DATE

> Task :core:resources:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :feature:train:generateResourceAccessorsForAppleMain NO-SOURCE

> Task :core:designsystem:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE

> Task :feature:account:generateExpectResourceCollectorsForCommonMain UP-TO-DATE

> Task :feature:reports:generateResourceAccessorsForNativeMain NO-SOURCE

> Task :core:designsystem:generateComposeResClass UP-TO-DATE

> Task :feature:train:convertXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:explore:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :core:designsystem:generateExpectResourceCollectorsForCommonMain UP-TO-DATE

> Task :feature:train:copyNonXmlValueResourcesForCommonMain NO-SOURCE

> Task :core:resources:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:train:prepareComposeResourcesTaskForCommonMain NO-SOURCE

> Task :core:data:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:account:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :core:designsystem:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:explore:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:reports:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE

> Task :feature:explore:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:reports:generateComposeResClass UP-TO-DATE

> Task :feature:explore:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :feature:account:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :core:resources:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:home:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:train:generateResourceAccessorsForCommonMain NO-SOURCE

> Task :feature:explore:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:account:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:explore:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:home:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :core:resources:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:train:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:reports:generateExpectResourceCollectorsForCommonMain UP-TO-DATE

> Task :feature:account:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :feature:home:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :feature:explore:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:train:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:reports:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:training-debug:kmpPartiallyResolvedDependenciesChecker

> Task :feature:training-debug:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :feature:library:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:account:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:reports:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:library:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:train:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :feature:home:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:train:generateResourceAccessorsForIosMain NO-SOURCE

> Task :feature:account:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:train:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:home:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:library:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :feature:train:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:home:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:reports:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :core:resources:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :feature:account:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:library:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:train:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :core:resources:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:reports:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :feature:account:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :feature:explore:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :feature:train:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE

> Task :core:resources:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:train:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:account:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:train:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:library:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:account:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:reports:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:explore:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:home:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :feature:library:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:train:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :feature:reports:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:train:generateResourceAccessorsForNativeMain NO-SOURCE

> Task :feature:library:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :feature:home:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:train:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE

> Task :feature:explore:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:home:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:reports:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:train:generateComposeResClass UP-TO-DATE

> Task :feature:train:generateExpectResourceCollectorsForCommonMain UP-TO-DATE

> Task :feature:reports:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :feature:library:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:train:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:train:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:train:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:library:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:train:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :feature:reports:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:train:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:training-debug:downloadKotlinNativeDistribution UP-TO-DATE

> Task :feature:reports:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:train:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:training-debug:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:train:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:training-debug:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:train:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :core:designsystem:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:training:kmpPartiallyResolvedDependenciesChecker

> Task :feature:training:checkKotlinGradlePluginConfigurationErrors SKIPPED

> Task :feature:training-debug:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :feature:training-debug:generateResourceAccessorsForAppleMain SKIPPED

> Task :feature:training-debug:convertXmlValueResourcesForCommonMain NO-SOURCE

> Task :core:designsystem:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:training-debug:copyNonXmlValueResourcesForCommonMain NO-SOURCE

> Task :core:designsystem:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:training-debug:prepareComposeResourcesTaskForCommonMain NO-SOURCE

> Task :feature:training-debug:generateResourceAccessorsForCommonMain SKIPPED

> Task :feature:training-debug:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :core:designsystem:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :core:designsystem:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:training-debug:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:training:downloadKotlinNativeDistribution UP-TO-DATE

> Task :feature:training-debug:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :feature:training-debug:generateResourceAccessorsForIosMain SKIPPED

> Task :core:designsystem:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :core:designsystem:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:training:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:training-debug:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:training:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:training-debug:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :core:designsystem:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :feature:training-debug:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :feature:training-debug:generateResourceAccessorsForIosSimulatorArm64Main SKIPPED

> Task :core:designsystem:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:training:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :feature:training:generateResourceAccessorsForAppleMain SKIPPED

> Task :feature:training-debug:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :core:designsystem:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:training:convertXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:library:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:training-debug:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:training:copyNonXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:training-debug:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :feature:train:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:train:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:training:prepareComposeResourcesTaskForCommonMain NO-SOURCE

> Task :feature:training:generateResourceAccessorsForCommonMain SKIPPED

> Task :feature:training-debug:generateResourceAccessorsForNativeMain SKIPPED

> Task :feature:explore:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:training:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:home:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:training-debug:generateActualResourceCollectorsForIosSimulatorArm64Main SKIPPED

> Task :feature:training-debug:generateComposeResClass SKIPPED

> Task :feature:training-debug:generateExpectResourceCollectorsForCommonMain SKIPPED

> Task :feature:training:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:train:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:training-debug:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:training:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :feature:training:generateResourceAccessorsForIosMain SKIPPED

> Task :feature:training-debug:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:training:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:training-debug:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:training-debug:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :feature:training:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:reports:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:training-debug:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:training:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :feature:training-debug:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:training:generateResourceAccessorsForIosSimulatorArm64Main SKIPPED

> Task :feature:training-debug:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:training:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:training:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:training-debug:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :feature:training:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :feature:training:generateResourceAccessorsForNativeMain SKIPPED

> Task :feature:training-debug:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:training:generateActualResourceCollectorsForIosSimulatorArm64Main SKIPPED

> Task :feature:training:generateComposeResClass SKIPPED

> Task :feature:account:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:training-debug:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:training:generateExpectResourceCollectorsForCommonMain SKIPPED

> Task :feature:training:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:shell:kmpPartiallyResolvedDependenciesChecker

> Task :feature:shell:checkKotlinGradlePluginConfigurationErrors SKIPPED



> Task :feature:shell:checkIosSimulatorArm64MainComposeLibrariesCompatibility

w: Skiko dependencies' versions are incompatible.

    io.coil-kt.coil3:coil-core-iossimulatorarm64:3.4.0

    \--- org.jetbrains.skiko:skiko:0.9.22.2 -> 0.144.6



This may lead to compilation errors or unexpected behavior at runtime.

Such version mismatch might be caused by dependency constraints in one of the included libraries.

You can inspect resulted dependencies tree via `./gradlew :feature:shell:dependencies  --configuration iosSimulatorArm64CompileKlibraries`.

See more details in Gradle documentation: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing-dependencies



Note: Skiko is considered implementation detail in Compose Multiplatform and might be incompatible across versions.

Please align Skiko dependencies to the same version. If possible, avoid direct Skiko references and use Compose APIs instead.





> Task :feature:shell:downloadKotlinNativeDistribution UP-TO-DATE

> Task :feature:shell:convertXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:shell:copyNonXmlValueResourcesForAppleMain NO-SOURCE

> Task :feature:shell:prepareComposeResourcesTaskForAppleMain NO-SOURCE

> Task :feature:shell:generateResourceAccessorsForAppleMain NO-SOURCE

> Task :feature:shell:convertXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:shell:copyNonXmlValueResourcesForCommonMain NO-SOURCE

> Task :feature:training:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:shell:prepareComposeResourcesTaskForCommonMain NO-SOURCE

> Task :feature:shell:generateResourceAccessorsForCommonMain NO-SOURCE

> Task :feature:shell:convertXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:training:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:shell:copyNonXmlValueResourcesForIosMain NO-SOURCE

> Task :feature:training:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:shell:prepareComposeResourcesTaskForIosMain NO-SOURCE

> Task :feature:training:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :feature:shell:generateResourceAccessorsForIosMain NO-SOURCE

> Task :feature:training:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:training:iosArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:shell:convertXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:training:iosArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:training:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :feature:training:iosSimulatorArm64CopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:shell:copyNonXmlValueResourcesForIosSimulatorArm64Main NO-SOURCE

> Task :feature:training:iosSimulatorArm64ZipMultiplatformResourcesForPublication UP-TO-DATE

> Task :feature:shell:prepareComposeResourcesTaskForIosSimulatorArm64Main NO-SOURCE

> Task :feature:shell:generateResourceAccessorsForIosSimulatorArm64Main NO-SOURCE

> Task :feature:shell:convertXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:shell:copyNonXmlValueResourcesForNativeMain NO-SOURCE

> Task :feature:shell:prepareComposeResourcesTaskForNativeMain NO-SOURCE

> Task :feature:shell:generateResourceAccessorsForNativeMain NO-SOURCE

> Task :feature:shell:generateActualResourceCollectorsForIosSimulatorArm64Main UP-TO-DATE

> Task :feature:shell:generateComposeResClass UP-TO-DATE

> Task :feature:shell:generateExpectResourceCollectorsForCommonMain UP-TO-DATE

> Task :feature:shell:generateMovitAndroidBuildMetadata UP-TO-DATE

> Task :feature:shell:createBuildSystemDirectory

> Task :feature:shell:symbolicLinkToAssembleDebugAppleFrameworkForXcodeIosSimulatorArm64 UP-TO-DATE

> Task :feature:shell:checkCanSyncComposeResourcesForIos

> Task :feature:shell:iosArm64ResolveResourcesFromDependencies UP-TO-DATE

> Task :feature:shell:convertXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:shell:copyNonXmlValueResourcesForIosArm64Main NO-SOURCE

> Task :feature:training-debug:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:shell:prepareComposeResourcesTaskForIosArm64Main NO-SOURCE

> Task :feature:shell:compileKotlinIosSimulatorArm64 UP-TO-DATE

> Task :feature:shell:linkDebugFrameworkIosSimulatorArm64

Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-uikit-iossimulatorarm64/1.11.0/aeea22c7c26752dbb9c58e301abddbdaa388ac18/ui-uikit-iosSimulatorArm64Cinterop-utilsMain-1.11.0 can't be exported with -Xexport-library

Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.skiko/skiko-iossimulatorarm64/0.144.6/92634069df9422761235b7d233f7255336ee42f6/skiko-iosSimulatorArm64Cinterop-uikitMain-0.144.6 can't be exported with -Xexport-library

Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-iossimulatorarm64/3.1.2/5624eb603a7aac50ca163947b5225ab40959b6b2/ktor-network-iosSimulatorArm64Cinterop-networkMain-3.1.2 can't be exported with -Xexport-library

Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-iossimulatorarm64/3.1.2/753736eeda055f67ac2dfa590b243a1fff9435e1/ktor-network-iosSimulatorArm64Cinterop-unMain-3.1.2 can't be exported with -Xexport-library

Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-utils-iossimulatorarm64/3.1.2/192389250198de1d59013163cbfde26c9a08dd57/ktor-utils-iosSimulatorArm64Cinterop-threadUtilsMain-3.1.2 can't be exported with -Xexport-library

Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-io-iossimulatorarm64/3.1.2/9da2d679c2b1a62073fe64a375385a2a37c1114a/ktor-io-iosSimulatorArm64Cinterop-mutexMain-3.1.2 can't be exported with -Xexport-library

Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/sqliter-driver-iossimulatorarm64/1.3.3/36df21ed52cc81daea5ef283b62882d04368953c/sqliter-driver-cinterop-sqlite3 can't be exported with -Xexport-library

Interop library /Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/atomicfu-iossimulatorarm64/0.31.0/561273c851e78a473b948c9d91a297bd514aca94/atomicfu-iosSimulatorArm64Cinterop-interopMain-0.31.0 can't be exported with -Xexport-library

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.navigationevent/navigationevent-iossimulatorarm64/1.0.1/78e44ac5a1e73b6cd937c579f1f3ab0789d68f1d/navigationevent-iosSimulatorArm64Main-1.0.1.klib



Included libraries:

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/common/stdlib

/Users/mood/POSE/android-poc/feature/account/build/classes/kotlin/iosSimulatorArm64/main/klib/account

/Users/mood/POSE/android-poc/feature/explore/build/classes/kotlin/iosSimulatorArm64/main/klib/explore

/Users/mood/POSE/android-poc/feature/home/build/classes/kotlin/iosSimulatorArm64/main/klib/home

/Users/mood/POSE/android-poc/feature/train/build/classes/kotlin/iosSimulatorArm64/main/klib/train

/Users/mood/POSE/android-poc/feature/training-debug/build/classes/kotlin/iosSimulatorArm64/main/klib/training-debug

/Users/mood/POSE/android-poc/feature/training/build/classes/kotlin/iosSimulatorArm64/main/klib/training

/Users/mood/POSE/android-poc/feature/reports/build/classes/kotlin/iosSimulatorArm64/main/klib/reports

/Users/mood/POSE/android-poc/feature/library/build/classes/kotlin/iosSimulatorArm64/main/klib/library

/Users/mood/POSE/android-poc/core/data/build/classes/kotlin/iosSimulatorArm64/main/klib/data

/Users/mood/POSE/android-poc/core/designsystem/build/classes/kotlin/iosSimulatorArm64/main/klib/designsystem

/Users/mood/POSE/android-poc/core/model/build/classes/kotlin/iosSimulatorArm64/main/klib/model

/Users/mood/POSE/android-poc/core/network/build/classes/kotlin/iosSimulatorArm64/main/klib/network

/Users/mood/POSE/android-poc/core/pose-capture/build/classes/kotlin/iosSimulatorArm64/main/klib/pose-capture

/Users/mood/POSE/android-poc/core/training-engine/build/classes/kotlin/iosSimulatorArm64/main/klib/training-engine

/Users/mood/POSE/android-poc/shared/build/classes/kotlin/iosSimulatorArm64/main/klib/shared

/Users/mood/POSE/android-poc/core/resources/build/classes/kotlin/iosSimulatorArm64/main/klib/resources

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material3/material3-uikitsimarm64/1.9.0/ed530723d6e01374ad405d5bd88404bd3164c0a5/material3-uikitSimArm64Main-1.9.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.components/components-resources-iosSimulatorArm64/1.11.0/9b5310a9f968d5b393b1195b0e8129cdee93383d/library-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material/material-ripple-uikitsimarm64/1.9.1/2da4a0d1b8e9d24d4ea47aa639365231ffe1f873/material-ripple-uikitSimArm64Main-1.9.1.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.animation/animation-core-iossimulatorarm64/1.11.0/c2339745e105c2b4634c1770a3fcb0824f6808be/animation-core-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.animation/animation-iossimulatorarm64/1.11.0/d6457a82457b297e7d40344014cd49c82fd69920/animation-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.foundation/foundation-layout-iossimulatorarm64/1.11.0/696a9c09ea51410a76c50bf299b7f80ea50d4918/foundation-layout-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-compose-iossimulatorarm64/3.4.0/3bb1232d9cfbc8c38ca22b0e10127b3e518dc11f/coil-compose-iosSimulatorArm64Main-3.4.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-compose-core-iossimulatorarm64/3.4.0/f7180306005b748757c5fa9b141a991043419ee1/coil-compose-core-iosSimulatorArm64Main-3.4.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.foundation/foundation-iossimulatorarm64/1.11.0/f87a58657b2404ffd782aa23c2e190e78ad37fb1/foundation-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material/material-icons-extended-uikitsimarm64/1.7.3/480b5a73fc240e91b4c6a194542aaf152d354948/material-icons-extended.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.androidx.navigationevent/navigationevent-compose-uikitsimarm64/1.0.1/96d82f05d741f45a571da03ada256a50ce04db64/navigationevent-compose-uikitSimArm64Main-1.0.1.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.material/material-icons-core-uikitsimarm64/1.7.3/3c0863fb50d05945a767705714bf4235c9a26c86/material-icons-core.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-unit-iossimulatorarm64/1.11.0/6f5ca544ae8b394bbee26b0520fcfaf477cbe135/ui-unit-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-geometry-iossimulatorarm64/1.11.0/49097ab3b785d76efde0452bdf558113cbe5a7cb/ui-geometry-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-graphics-iossimulatorarm64/1.11.0/3a89b4a23fca3e10bc37aa5a78cdb3d86719e653/ui-graphics-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-backhandler-iossimulatorarm64/1.11.0/21cecdc4c04327c28aab5414cb403f3753dcaa92/ui-backhandler-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-util-iossimulatorarm64/1.11.0/f24afd7715863d67c11b24a9f2380c03591df2d5/ui-util-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-uikit-iossimulatorarm64/1.11.0/ebea9e9e2a20bdf1a9948937a78f2d247e18c93b/ui-uikit-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-uikit-iossimulatorarm64/1.11.0/aeea22c7c26752dbb9c58e301abddbdaa388ac18/ui-uikit-iosSimulatorArm64Cinterop-utilsMain-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-text-iossimulatorarm64/1.11.0/3a31968d481da9fec01f5502ace752225a6ed823/ui-text-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.ui/ui-iossimulatorarm64/1.11.0/3d9b500dc1bb86312b45f223ffd30e1060d26554/ui-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.navigationevent/navigationevent-iossimulatorarm64/1.0.2/78e44ac5a1e73b6cd937c579f1f3ab0789d68f1d/navigationevent-iosSimulatorArm64Main-1.0.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-runtime-iossimulatorarm64/2.11.0-beta01/39c80f51c066007e0123ba281503b7b9cd498d62/lifecycle-runtime-iosSimulatorArm64Main-2.11.0-beta01.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.androidx.savedstate/savedstate-compose-uikitsimarm64/1.3.6/e7a851331135e0f28b36ace3f4db253e8cabb2d1/savedstate-compose-uikitSimArm64Main-1.3.6.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.savedstate/savedstate-compose-iossimulatorarm64/1.4.0/6d093b163c8a71ac0d8684a2fe6dbaf79f1e450/savedstate-compose-iosSimulatorArm64Main-1.4.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.savedstate/savedstate-iossimulatorarm64/1.4.0/559d3801e3f2e99c3ad28af4dbfc9a732fba7499/savedstate-iosSimulatorArm64Main-1.4.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-common-iossimulatorarm64/2.11.0-beta01/1196fe43a0b67fa121233423790654ef453113a/lifecycle-common-iosSimulatorArm64Main-2.11.0-beta01.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-savedstate-iossimulatorarm64/2.11.0-beta01/576ed846acb8d14b49af41748686ce2b7b7be282/lifecycle-viewmodel-savedstate-iosSimulatorArm64Main-2.11.0-beta01.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-runtime-compose-iossimulatorarm64/2.11.0-beta01/bff7c3f7778716c200ecd3689820a2e8de9d6229/lifecycle-runtime-compose-iosSimulatorArm64Main-2.11.0-beta01.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-iossimulatorarm64/2.11.0-beta01/147f984a1e0d3721dc129fbdfb430cb86b4c0af9/lifecycle-viewmodel-iosSimulatorArm64Main-2.11.0-beta01.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel-compose-iossimulatorarm64/2.11.0-beta01/4cc65bd91c2cf34e6a7b5c98f791ff66bfa3ec/lifecycle-viewmodel-compose-iosSimulatorArm64Main-2.11.0-beta01.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-saveable-iossimulatorarm64/1.11.1/85c76f454cd428c1b698585b9c42db86d2dca247/runtime-saveable-iosSimulatorArm64Main-1.11.1.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-annotation-iossimulatorarm64/1.11.1/7c02fce3da8d3de4b84a67784537fbee9509d121/runtime-annotation-iosSimulatorArm64Main-1.11.1.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-retain-iossimulatorarm64/1.11.1/344000c978daec8831e73596dc211e0380389446/runtime-retain-iosSimulatorArm64Main-1.11.1.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.compose.runtime/runtime-iossimulatorarm64/1.11.1/a05d7729f7cc5e9a0101e1d79a676b48e94c0fa7/runtime-iosSimulatorArm64Main-1.11.1.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.runtime/runtime-iossimulatorarm64/1.11.0/6759c45fc47fe0381292bf8cc941f9f2756f238d/runtime-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.compose.runtime/runtime-saveable-iossimulatorarm64/1.11.0/53f68eba65bb87cc9b3274d8e2609c0b2c7fda8d/runtime-saveable-iosSimulatorArm64Main-1.11.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.androidx.lifecycle/lifecycle-runtime-compose-iossimulatorarm64/2.11.0-beta01/ed4a543af5c8bf09f38a765377908fd8478c6db8/lifecycle-runtime-compose-iosSimulatorArm64Main-2.11.0-beta01.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.androidx.lifecycle/lifecycle-viewmodel-compose-iossimulatorarm64/2.11.0-beta01/2ddad151cabdf37a397b2c1cd24a5c76f0aa867f/lifecycle-viewmodel-compose-iosSimulatorArm64Main-2.11.0-beta01.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-client-auth-iossimulatorarm64/3.1.2/2ac62b8075e149111555ac0fc70e4ed01abe348c/ktor-client-auth-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-client-content-negotiation-iossimulatorarm64/3.1.2/bacf76a3256356c57be44c5be24ff18b5995cef8/ktor-client-content-negotiation-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-client-logging-iossimulatorarm64/3.1.2/e01f00334a22ac6b4fa44eeb50a1d95cfacaa6fa/ktor-client-logging-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-client-darwin-iossimulatorarm64/3.1.2/e23a3ec972745bbc0664d9fb55bd0c63c468cd70/ktor-client-darwin-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-network-ktor3-iossimulatorarm64/3.4.0/20cef39e452f6bd2eb5290621de595528fbbb0ac/coil-network-ktor3-iosSimulatorArm64Main-3.4.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-client-core-iossimulatorarm64/3.1.2/ddbb3ca85274e498bab13eecd867a896cf0d16de/ktor-client-core-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/app.cash.sqldelight/coroutines-extensions-iossimulatorarm64/2.1.0/c2b54db8a82f85aa44b6d753e470c0168679fd60/coroutines-extensions.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-serialization-kotlinx-json-iossimulatorarm64/3.1.2/669fa4eeb48a57cd80b8516dbce822e812418c6c/ktor-serialization-kotlinx-json-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-iossimulatorarm64/3.4.0/72ccfbc53607909285ae5730306b16f0a8055712/coil-iosSimulatorArm64Main-3.4.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-network-core-iossimulatorarm64/3.4.0/5ee2cef964d406d5009e46035fc5f9eac8b11af1/coil-network-core-iosSimulatorArm64Main-3.4.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.coil-kt.coil3/coil-core-iossimulatorarm64/3.4.0/2ba1bc203c5ad319b1041c0db2d3c9fd72afb5b1/coil-core-iosSimulatorArm64Main-3.4.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.skiko/skiko-iossimulatorarm64/0.144.6/9427f7a68fe50664d4523f89d17ff683487f7406/skiko-iosSimulatorArm64Main-0.144.6.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.skiko/skiko-iossimulatorarm64/0.144.6/92634069df9422761235b7d233f7255336ee42f6/skiko-iosSimulatorArm64Cinterop-uikitMain-0.144.6.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-http-cio-iossimulatorarm64/3.1.2/12c92ef35519cb2455344339b4810efcc54d9f90/ktor-http-cio-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-tls-iossimulatorarm64/3.1.2/45232f6e96951d75938b5e04d4ea6e7e153125ad/ktor-network-tls-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-websocket-serialization-iossimulatorarm64/3.1.2/ae6e1806693a9dbc0238523501eb0407a99cdaf2/ktor-websocket-serialization-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-serialization-kotlinx-iossimulatorarm64/3.1.2/e0fdfdf5f8be00327b1bf6a4514a0a6201a9ef01/ktor-serialization-kotlinx-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-serialization-iossimulatorarm64/3.1.2/2ef6bf10bd50253fc4194f18f7f4878c018548bb/ktor-serialization-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-websockets-iossimulatorarm64/3.1.2/d0d2f6c65042812c5bfe8a4af0952a44418483db/ktor-websockets-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-http-iossimulatorarm64/3.1.2/5a320bc7d792979dbb7953f7be5425384b4ac2b4/ktor-http-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-events-iossimulatorarm64/3.1.2/687e5e2b0f029b59cae1748b5a86cb4bbf22578d/ktor-events-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-sse-iossimulatorarm64/3.1.2/6ecb0fb7947d52c2cb483c2352fce42df6db4595/ktor-sse-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/app.cash.sqldelight/async-extensions-iossimulatorarm64/2.1.0/29f396bc3669b98f75bf6384c2d5c85b6610e0d/async-extensions.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-iossimulatorarm64/3.1.2/b8553b7be847319b69c37dace28cae8f8c886c42/ktor-network-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-iossimulatorarm64/3.1.2/5624eb603a7aac50ca163947b5225ab40959b6b2/ktor-network-iosSimulatorArm64Cinterop-networkMain-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-network-iossimulatorarm64/3.1.2/753736eeda055f67ac2dfa590b243a1fff9435e1/ktor-network-iosSimulatorArm64Cinterop-unMain-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-utils-iossimulatorarm64/3.1.2/ee03c5cca0bfb24cc21043e774ef3de52804ec7/ktor-utils-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-utils-iossimulatorarm64/3.1.2/192389250198de1d59013163cbfde26c9a08dd57/ktor-utils-iosSimulatorArm64Cinterop-threadUtilsMain-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-io-iossimulatorarm64/3.1.2/b1461cf8e9ef0f72007105042756b1500f97db21/ktor-io-iosSimulatorArm64Main-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.ktor/ktor-io-iossimulatorarm64/3.1.2/9da2d679c2b1a62073fe64a375385a2a37c1114a/ktor-io-iosSimulatorArm64Cinterop-mutexMain-3.1.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-iossimulatorarm64/1.10.2/dde92bb9e9950e96494c2787302c0cb66c7cd8c8/kotlinx-coroutines-core-iosSimulatorArm64Main-1.10.2.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-json-io-iossimulatorarm64/1.8.0/f93c0e7900c567cc3c775f1966f198661a9e33f1/kotlinx-serialization-json-io-iosSimulatorArm64Main-1.8.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-json-iossimulatorarm64/1.8.1/26cd88ed3bb275d7bbdf9828c6887d02205db0fb/kotlinx-serialization-json-iosSimulatorArm64Main-1.8.1.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/app.cash.sqldelight/native-driver-iossimulatorarm64/2.1.0/7fa12be3694ad6dfea49608b0f65094bc00a37e1/native-driver.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/app.cash.sqldelight/runtime-iossimulatorarm64/2.1.0/ab8403c1270a29534650ba555ed7bc91fd425e3f/runtime.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/io.insert-koin/koin-core-iossimulatorarm64/4.2.1/7239f69f35e160176c394cfc81937316ab64c53c/koin-core-iosSimulatorArm64Main-4.2.1.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.collection/collection-iossimulatorarm64/1.5.0/f80a98cb05dafb0368759210aa8510c029613bca/collection-iosSimulatorArm64Main-1.5.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-datetime-iossimulatorarm64/0.7.1/bb22d3b01ccd535827f88d99d1cbb337dc5cae4b/kotlinx-datetime-iosSimulatorArm64Main-0.7.1.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation-iossimulatorarm64/1.9.1/b879ab97d24ac69dda460a6f19f70762840583b2/annotation.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-core-iossimulatorarm64/1.8.1/6321d2e707cf075a97f53c9406635ff3d0e4fc0d/kotlinx-serialization-core-iosSimulatorArm64Main-1.8.1.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/stately-concurrent-collections-iossimulatorarm64/2.1.0/c81ec9f587fc7085a4eaf8d932471e7092c2a135/stately-concurrent-collections.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/stately-concurrency-iossimulatorarm64/2.1.0/7cc64802f88bad744e3a523d52e71734ef8eefaa/stately-concurrency.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/sqliter-driver-iossimulatorarm64/1.3.3/550ff0a78efdb784d1e73ee1e11729fdac7f3d50/sqliter-driver.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/sqliter-driver-iossimulatorarm64/1.3.3/36df21ed52cc81daea5ef283b62882d04368953c/sqliter-driver-cinterop-sqlite3.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/atomicfu-iossimulatorarm64/0.31.0/13553e91893f061792e08cfdb1d7f97254aad5a8/atomicfu-iosSimulatorArm64Main-0.31.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/atomicfu-iossimulatorarm64/0.31.0/561273c851e78a473b948c9d91a297bd514aca94/atomicfu-iosSimulatorArm64Cinterop-interopMain-0.31.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-io-okio-iossimulatorarm64/0.9.0/d89fb2cea5ab5c0f093b4a52ed5da9083ba9aae8/kotlinx-io-okio-iosSimulatorArm64Main-0.9.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/co.touchlab/stately-strict-iossimulatorarm64/2.1.0/4e81855e04e1fe2617d9a520a927507b97e29393/stately-strict.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/com.squareup.okio/okio-iossimulatorarm64/3.16.4/4d6219ffbca29647f22f110cda55c03d833392e2/okio-iosSimulatorArm64Main-3.16.4.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-io-core-iossimulatorarm64/0.9.0/38e19d259efa80b6a8f8713a61f58a89ff06ab6b/kotlinx-io-core-iosSimulatorArm64Main-0.9.0.klib

/Users/mood/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-io-bytestring-iossimulatorarm64/0.9.0/e86999f3d612b2cedf620944a91f1d597c816f4c/kotlinx-io-bytestring-iosSimulatorArm64Main-0.9.0.klib

/Users/mood/POSE/android-poc/feature/shell/build/classes/kotlin/iosSimulatorArm64/main/klib/shell

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreFoundation

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Network

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.QuartzCore

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MLCompute

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SafetyKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Security

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CloudKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.VideoSubscriberAccount

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.LockedCameraCapture

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.OpenGLES3

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Accessibility

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GameKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AVFoundation

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CFNetwork

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.WebKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Metal

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreMotion

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.iconv

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Messages

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.LocalAuthenticationEmbeddedUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.OpenGLES2

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MediaToolbox

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ThreadNetwork

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SensitiveContentAnalysis

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.posix

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreHaptics

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ExtensionFoundation

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.UniformTypeIdentifiers

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AppTrackingTransparency

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.EventKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PHASE

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreAudio

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.StoreKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ContactsUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BrowserKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MultipeerConnectivity

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SafariServices

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MetricKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AddressBook

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform._CoreData_CloudKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SpriteKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreAudioKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreText

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Twitter

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.LocalAuthentication

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AutomaticAssessmentConfiguration

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreGraphics

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreData

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SceneKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.OpenGLESCommon

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ShazamKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ReplayKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.DataDetection

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SoundAnalysis

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Cinematic

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.QuickLookThumbnailing

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreMedia

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BackgroundAssets

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.iAd

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GameController

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ClockKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreSpotlight

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.zlib

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CFCGTypes

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PDFKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.UIKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.FileProviderUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MetalPerformanceShaders

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CarPlay

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Matter

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AppClip

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AlarmKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Contacts

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.OpenAL

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GameplayKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CommonCrypto

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PassKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AudioToolbox

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GLKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MediaAccessibility

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Vision

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MetalPerformanceShadersGraph

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.HomeKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreServices

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Speech

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ExposureNotification

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BackgroundTasks

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreAudioTypes

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SharedWithYou

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CallKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MediaSetup

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreML

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AVRouting

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AdSupport

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AVKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BrowserEngineCore

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ModelIO

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Social

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.HealthKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.NaturalLanguage

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CryptoTokenKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MapKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreLocationUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GameSave

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Intents

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.IOSurface

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MessageUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AssetsLibrary

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.EventKitUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.NetworkExtension

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AuthenticationServices

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ARKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.UserNotificationsUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.UserNotifications

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ClassKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Accounts

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BusinessChat

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AddressBookUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.VideoToolbox

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PushKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AccessorySetupKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Accelerate

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ImageCaptureCore

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.EAGL

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.DeviceCheck

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.builtin

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreMIDI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AVFAudio

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreVideo

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform._LocationEssentials

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.IntentsUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.WatchConnectivity

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.DeviceDiscoveryExtension

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.darwin

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PencilKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MobileCoreServices

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreLocation

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SecurityUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.objc

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.IdentityLookupUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreTelephony

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.NotificationCenter

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.FileProvider

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PushToTalk

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ExtensionKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ImageIO

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.GSS

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.LinkPresentation

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ExternalAccessory

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.AdServices

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SensorKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Foundation

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.IdentityLookup

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SystemConfiguration

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Photos

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreImage

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.OpenGLES

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.NearbyInteraction

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.UIUtilities

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreFoundationBase

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.TouchController

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ColorSync

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.JavaScriptCore

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreBluetooth

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Symbols

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MetalFX

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.HealthKitUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.PhotosUI

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.QuickLook

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.SharedWithYouCore

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.CoreNFC

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MediaPlayer

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.BrowserEngineKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.MetalKit

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.ScreenTime

/Users/mood/.konan/kotlin-native-prebuilt-macos-aarch64-2.3.0/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.VisionKit



Following libraries are specified to be exported with -Xexport-library, but not included to the build:

Exposed type 'ComposableLambda' is 'Function2<Composer, Int, Any?>' and 'Function3<Any?, Composer, Int, Any?>' at the same time. This most likely wouldn't work as expected.



> Task :feature:shell:assembleDebugAppleFrameworkForXcodeIosSimulatorArm64

> Task :feature:shell:copyDsymForEmbedAndSignAppleFrameworkForXcode SKIPPED

> Task :feature:shell:assembleIosArm64MainResources UP-TO-DATE

> Task :feature:shell:iosArm64ResolveSelfResourcesCopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:shell:iosArm64AggregateResources UP-TO-DATE

> Task :feature:shell:iosSimulatorArm64ResolveResourcesFromDependencies UP-TO-DATE

> Task :feature:shell:assembleIosSimulatorArm64MainResources UP-TO-DATE

> Task :feature:shell:iosSimulatorArm64ResolveSelfResourcesCopyHierarchicalMultiplatformResources UP-TO-DATE

> Task :feature:shell:iosSimulatorArm64AggregateResources UP-TO-DATE

> Task :feature:shell:syncComposeResourcesForIos UP-TO-DATE

> Task :feature:shell:embedAndSignAppleFrameworkForXcode SKIPPED



BUILD SUCCESSFUL in 3m 7s

188 actionable tasks: 33 executed, 155 up-to-date

Cannot infer a bundle ID from packages of source files and exported dependencies, use the bundle name instead: MovitApp. Please specify the bundle ID explicitly using the -Xbinary=bundleId=<id> compiler flag.

SwiftDriver iosApp normal arm64 com.apple.xcode.tools.swift.compiler (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    builtin-SwiftDriver -- /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/swiftc -module-name iosApp -Onone @/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftFileList -DDEBUG -enable-bare-slash-regex -enable-experimental-feature DebugDescriptionMacro -sdk /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -target arm64-apple-ios18.0-simulator -g -module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex -Xfrontend -serialize-debugging-options -enable-testing -index-store-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Index.noindex/DataStore -Xcc -D_LIBCPP_HARDENING_MODE\=_LIBCPP_HARDENING_MODE_DEBUG -swift-version 5 -I /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator -F /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator -F /Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5 -c -j10 -enable-batch-mode -incremental -Xcc -ivfsstatcache -Xcc /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache -output-file-map /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp-OutputFileMap.json -use-frontend-parseable-output -save-temps -no-color-diagnostics -explicit-module-build -module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/SwiftExplicitPrecompiledModules -clang-scanner-module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex -sdk-module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex -serialize-diagnostics -emit-dependencies -emit-module -emit-module-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.swiftmodule -validate-clang-modules-once -clang-build-session-file /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/swift-overrides.hmap -emit-const-values -Xfrontend -const-gather-protocols-file -Xfrontend /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp_const_extract_protocols.json -Xcc -iquote -Xcc /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-generated-files.hmap -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-own-target-headers.hmap -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-all-target-headers.hmap -Xcc -iquote -Xcc /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-project-headers.hmap -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/include -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources-normal/arm64 -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources/arm64 -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources -Xcc -DDEBUG\=1 -emit-objc-header -emit-objc-header-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp-Swift.h -working-directory /Users/mood/POSE/android-poc/iosApp -experimental-emit-module-separately -disable-cmo

SwiftCompile normal arm64 Compiling\ MovitGoogleSignInBridge.swift /Users/mood/POSE/android-poc/iosApp/iosApp/MovitGoogleSignInBridge.swift (in target 'iosApp' from project 'iosApp')

SwiftCompile normal arm64 /Users/mood/POSE/android-poc/iosApp/iosApp/MovitGoogleSignInBridge.swift (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    

SwiftEmitModule normal arm64 Emitting\ module\ for\ iosApp (in target 'iosApp' from project 'iosApp')

EmitSwiftModule normal arm64 (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    

SwiftCompile normal arm64 Compiling\ GeneratedAssetSymbols.swift /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources/GeneratedAssetSymbols.swift (in target 'iosApp' from project 'iosApp')

SwiftCompile normal arm64 /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources/GeneratedAssetSymbols.swift (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    

SwiftCompile normal arm64 Compiling\ MovitStoreKitBridge.swift /Users/mood/POSE/android-poc/iosApp/iosApp/MovitStoreKitBridge.swift (in target 'iosApp' from project 'iosApp')

SwiftCompile normal arm64 /Users/mood/POSE/android-poc/iosApp/iosApp/MovitStoreKitBridge.swift (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    

SwiftCompile normal arm64 Compiling\ MovitCameraPermissionBridge.swift /Users/mood/POSE/android-poc/iosApp/iosApp/MovitCameraPermissionBridge.swift (in target 'iosApp' from project 'iosApp')

SwiftCompile normal arm64 /Users/mood/POSE/android-poc/iosApp/iosApp/MovitCameraPermissionBridge.swift (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    

SwiftCompile normal arm64 Compiling\ MovitPoseLandmarkerBridge.swift /Users/mood/POSE/android-poc/iosApp/iosApp/MovitPoseLandmarkerBridge.swift (in target 'iosApp' from project 'iosApp')

SwiftCompile normal arm64 /Users/mood/POSE/android-poc/iosApp/iosApp/MovitPoseLandmarkerBridge.swift (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    

/Users/mood/POSE/android-poc/iosApp/iosApp/MovitPoseLandmarkerBridge.swift:192:48: warning: 'init(_:)' is deprecated: replaced by 'init(truncating:)'
        KotlinByte(char: Int8(bitPattern: data[Int(index)]))
                                               ^
/Users/mood/POSE/android-poc/iosApp/iosApp/MovitPoseLandmarkerBridge.swift:192:48: note: use 'init(truncating:)' instead
        KotlinByte(char: Int8(bitPattern: data[Int(index)]))
                                               ^
                                                   truncating: 

/Users/mood/POSE/android-poc/iosApp/iosApp/MovitPoseLandmarkerBridge.swift:192:48: 'init(_:)' is deprecated: replaced by 'init(truncating:)'

SwiftCompile normal arm64 Compiling\ iOSApp.swift /Users/mood/POSE/android-poc/iosApp/iosApp/iOSApp.swift (in target 'iosApp' from project 'iosApp')

SwiftCompile normal arm64 /Users/mood/POSE/android-poc/iosApp/iosApp/iOSApp.swift (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    

SwiftCompile normal arm64 Compiling\ MovitGoogleSignInConfig.swift /Users/mood/POSE/android-poc/iosApp/iosApp/MovitGoogleSignInConfig.swift (in target 'iosApp' from project 'iosApp')

SwiftCompile normal arm64 /Users/mood/POSE/android-poc/iosApp/iosApp/MovitGoogleSignInConfig.swift (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    

SwiftDriverJobDiscovery normal arm64 Compiling GeneratedAssetSymbols.swift (in target 'iosApp' from project 'iosApp')

SwiftDriverJobDiscovery normal arm64 Compiling MovitGoogleSignInConfig.swift (in target 'iosApp' from project 'iosApp')

SwiftDriverJobDiscovery normal arm64 Compiling MovitGoogleSignInBridge.swift (in target 'iosApp' from project 'iosApp')

SwiftDriverJobDiscovery normal arm64 Emitting module for iosApp (in target 'iosApp' from project 'iosApp')

SwiftDriver\ Compilation\ Requirements iosApp normal arm64 com.apple.xcode.tools.swift.compiler (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    builtin-Swift-Compilation-Requirements -- /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/swiftc -module-name iosApp -Onone @/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftFileList -DDEBUG -enable-bare-slash-regex -enable-experimental-feature DebugDescriptionMacro -sdk /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -target arm64-apple-ios18.0-simulator -g -module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex -Xfrontend -serialize-debugging-options -enable-testing -index-store-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Index.noindex/DataStore -Xcc -D_LIBCPP_HARDENING_MODE\=_LIBCPP_HARDENING_MODE_DEBUG -swift-version 5 -I /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator -F /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator -F /Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5 -c -j10 -enable-batch-mode -incremental -Xcc -ivfsstatcache -Xcc /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache -output-file-map /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp-OutputFileMap.json -use-frontend-parseable-output -save-temps -no-color-diagnostics -explicit-module-build -module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/SwiftExplicitPrecompiledModules -clang-scanner-module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex -sdk-module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex -serialize-diagnostics -emit-dependencies -emit-module -emit-module-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.swiftmodule -validate-clang-modules-once -clang-build-session-file /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/swift-overrides.hmap -emit-const-values -Xfrontend -const-gather-protocols-file -Xfrontend /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp_const_extract_protocols.json -Xcc -iquote -Xcc /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-generated-files.hmap -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-own-target-headers.hmap -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-all-target-headers.hmap -Xcc -iquote -Xcc /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-project-headers.hmap -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/include -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources-normal/arm64 -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources/arm64 -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources -Xcc -DDEBUG\=1 -emit-objc-header -emit-objc-header-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp-Swift.h -working-directory /Users/mood/POSE/android-poc/iosApp -experimental-emit-module-separately -disable-cmo

Copy /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.swiftmodule/arm64-apple-ios-simulator.abi.json /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.abi.json (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    builtin-copy -exclude .DS_Store -exclude CVS -exclude .svn -exclude .git -exclude .hg -resolve-src-symlinks -rename /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.abi.json /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.swiftmodule/arm64-apple-ios-simulator.abi.json

SwiftDriverJobDiscovery normal arm64 Compiling iOSApp.swift (in target 'iosApp' from project 'iosApp')

SwiftDriverJobDiscovery normal arm64 Compiling MovitCameraPermissionBridge.swift (in target 'iosApp' from project 'iosApp')

SwiftDriverJobDiscovery normal arm64 Compiling MovitStoreKitBridge.swift (in target 'iosApp' from project 'iosApp')

SwiftDriverJobDiscovery normal arm64 Compiling MovitPoseLandmarkerBridge.swift (in target 'iosApp' from project 'iosApp')

SwiftDriver\ Compilation iosApp normal arm64 com.apple.xcode.tools.swift.compiler (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    builtin-Swift-Compilation -- /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/swiftc -module-name iosApp -Onone @/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftFileList -DDEBUG -enable-bare-slash-regex -enable-experimental-feature DebugDescriptionMacro -sdk /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -target arm64-apple-ios18.0-simulator -g -module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex -Xfrontend -serialize-debugging-options -enable-testing -index-store-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Index.noindex/DataStore -Xcc -D_LIBCPP_HARDENING_MODE\=_LIBCPP_HARDENING_MODE_DEBUG -swift-version 5 -I /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator -F /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator -F /Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5 -c -j10 -enable-batch-mode -incremental -Xcc -ivfsstatcache -Xcc /Users/mood/Library/Developer/Xcode/DerivedData/SDKStatCaches.noindex/iphonesimulator26.5-23F73-6cfe768891a92b912361537c460fe42b.sdkstatcache -output-file-map /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp-OutputFileMap.json -use-frontend-parseable-output -save-temps -no-color-diagnostics -explicit-module-build -module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/SwiftExplicitPrecompiledModules -clang-scanner-module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex -sdk-module-cache-path /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex -serialize-diagnostics -emit-dependencies -emit-module -emit-module-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.swiftmodule -validate-clang-modules-once -clang-build-session-file /Users/mood/Library/Developer/Xcode/DerivedData/ModuleCache.noindex/Session.modulevalidation -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/swift-overrides.hmap -emit-const-values -Xfrontend -const-gather-protocols-file -Xfrontend /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp_const_extract_protocols.json -Xcc -iquote -Xcc /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-generated-files.hmap -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-own-target-headers.hmap -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-all-target-headers.hmap -Xcc -iquote -Xcc /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-project-headers.hmap -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/include -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources-normal/arm64 -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources/arm64 -Xcc -I/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/DerivedSources -Xcc -DDEBUG\=1 -emit-objc-header -emit-objc-header-path /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp-Swift.h -working-directory /Users/mood/POSE/android-poc/iosApp -experimental-emit-module-separately -disable-cmo

Ld /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/iosApp.debug.dylib normal (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -Xlinker -reproducible -target arm64-apple-ios18.0-simulator -dynamiclib -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -O0 -L/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/EagerLinkingTBDs/Debug-iphonesimulator -L/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator -F/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/EagerLinkingTBDs/Debug-iphonesimulator -F/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator -F/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5 -filelist /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.LinkFileList -install_name @rpath/iosApp.debug.dylib -Xlinker -rpath -Xlinker /usr/lib/swift -Xlinker -rpath -Xlinker @executable_path/Frameworks -Xlinker -dead_strip -Xlinker -object_path_lto -Xlinker /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp_lto.o -rdynamic -Xlinker -no_deduplicate -Xlinker -objc_abi_version -Xlinker 2 -Xlinker -dependency_info -Xlinker /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp_dependency_info.dat -fobjc-link-runtime -L/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/iphonesimulator -L/usr/lib/swift -Xlinker -add_ast_path -Xlinker /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.swiftmodule @/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp-linker-args.resp -ObjC -framework MovitApp -lsqlite3 -Xlinker -alias -Xlinker _main -Xlinker ___debug_main_executable_dylib_entry_point -framework StoreKit -Xlinker -no_adhoc_codesign -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/iosApp.debug.dylib

/Users/mood/POSE/android-poc/iosApp/iosApp.xcodeproj: iosApp: ld: warning: object file (/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/MovitApp[1777](libicu.icudtl_dat.o)) was built for newer 'iOS-simulator' version (18.5) than being linked (18.0)

/Users/mood/POSE/android-poc/iosApp/iosApp.xcodeproj: Object file (/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5/MovitApp.framework/MovitApp[1777](libicu.icudtl_dat.o)) was built for newer 'iOS-simulator' version (18.5) than being linked (18.0)

ConstructStubExecutorLinkFileList /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-ExecutorLinkFileList-normal-arm64.txt (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    construct-stub-executor-link-file-list /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/iosApp.debug.dylib /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/lib/libPreviewsJITStubExecutor_no_swift_entry_point.a /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/lib/libPreviewsJITStubExecutor.a --output /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-ExecutorLinkFileList-normal-arm64.txt

note: Using stub executor library with Swift entry point. (in target 'iosApp' from project 'iosApp')

Using stub executor library with Swift entry point.

Ld /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/iosApp normal (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/clang -Xlinker -reproducible -target arm64-apple-ios18.0-simulator -isysroot /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk -O0 -L/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator -F/Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator -F/Users/mood/POSE/android-poc/feature/shell/build/xcode-frameworks/Debug/iphonesimulator26.5 -Xlinker -rpath -Xlinker @executable_path -Xlinker -rpath -Xlinker @executable_path/Frameworks -rdynamic -Xlinker -no_deduplicate -Xlinker -objc_abi_version -Xlinker 2 -e ___debug_blank_executor_main -Xlinker -sectcreate -Xlinker __TEXT -Xlinker __debug_dylib -Xlinker /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-DebugDylibPath-normal-arm64.txt -Xlinker -sectcreate -Xlinker __TEXT -Xlinker __debug_instlnm -Xlinker /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-DebugDylibInstallName-normal-arm64.txt -Xlinker -filelist -Xlinker /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp-ExecutorLinkFileList-normal-arm64.txt -Xlinker -sectcreate -Xlinker __TEXT -Xlinker __entitlements -Xlinker /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.app-Simulated.xcent -Xlinker -sectcreate -Xlinker __TEXT -Xlinker __ents_der -Xlinker /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.app-Simulated.xcent.der /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/iosApp.debug.dylib -Xlinker -no_adhoc_codesign -o /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/iosApp

CopySwiftLibs /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    builtin-swiftStdLibTool --copy --verbose --sign - --scan-executable /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/iosApp.debug.dylib --scan-folder /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/Frameworks --scan-folder /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/PlugIns --scan-folder /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/SystemExtensions --scan-folder /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/Extensions --scan-folder /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk/System/Library/Frameworks/StoreKit.framework --platform iphonesimulator --toolchain /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain --destination /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/Frameworks --strip-bitcode --strip-bitcode-tool /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/bitcode_strip --emit-dependency-info /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/SwiftStdLibToolInputDependencies.dep --filter-for-swift-os

ExtractAppIntentsMetadata (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/appintentsmetadataprocessor --toolchain-dir /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain --module-name iosApp --sdk-root /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator26.5.sdk --xcode-version 17F42 --platform-family iOS --deployment-target 18.0 --bundle-identifier com.movit.iosApp --output /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app --target-triple arm64-apple-ios18.0-simulator --binary-file /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/iosApp --dependency-file /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp_dependency_info.dat --stringsdata-file /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/ExtractedAppShortcutsMetadata.stringsdata --source-file-list /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftFileList --metadata-file-list /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.DependencyMetadataFileList --static-metadata-file-list /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.DependencyStaticMetadataFileList --swift-const-vals-list /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/Objects-normal/arm64/iosApp.SwiftConstValuesFileList --compile-time-extraction --deployment-aware-processing --validate-assistant-intents --no-app-shortcuts-localization

2026-06-18 19:10:35.441 appintentsmetadataprocessor[29031:604610] Starting appintentsmetadataprocessor export
2026-06-18 19:10:35.445 appintentsmetadataprocessor[29031:604610] warning: Metadata extraction skipped. No AppIntents.framework dependency found.

CodeSign /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/iosApp.debug.dylib (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    
    Signing Identity:     "Sign to Run Locally"
    
    /usr/bin/codesign --force --sign - --timestamp\=none --generate-entitlement-der /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app/iosApp.debug.dylib

CodeSign /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    
    Signing Identity:     "Sign to Run Locally"
    
    /usr/bin/codesign --force --sign - --entitlements /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator/iosApp.build/iosApp.app.xcent --timestamp\=none --generate-entitlement-der /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app

Validate /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app (in target 'iosApp' from project 'iosApp')
    cd /Users/mood/POSE/android-poc/iosApp
    builtin-validationUtility /Users/mood/Library/Developer/Xcode/DerivedData/iosApp-emjewudycffbjugbcsysqmxgrcmo/Build/Products/Debug-iphonesimulator/iosApp.app -shallow-bundle -infoplist-subpath Info.plist



Build succeeded    18/06/2026, 7:10 PM    192.7 seconds
