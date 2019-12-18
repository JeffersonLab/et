## Get rid of rpath warning on OSX/MacOS
if(${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
  set(CMAKE_MACOSX_RPATH 1)
endif()

## Ensure the correct library for the "experimental" filesystem features is
## loaded: 
##    * Linux: libstdc++fs
##    * MacOS (XCode) for some reason still doesn't include the filesystem
##      features (this might change in XCode 10.0). Currently, a good way to get
##      them is by installing llvm directly through homebrew. The correct
##      library will then be:
##      /usr/local/opt/lib/libc++experimental.a
if(${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
  set(CXX_FILESYSTEM_LIB "/usr/local/opt/llvm/lib/libc++experimental.a")
else()
  set(CXX_FILESYSTEM_LIB "stdc++fs")
endif()

