cmake_minimum_required(VERSION 3.22.1)
project(audiocore_tests CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG        v1.14.0
)
# Keep GoogleTest from overriding our compiler/linker flags on Windows hosts.
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

# Path to the app's production native sources.
set(APP_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp)

add_executable(audiocore_tests
    SanityTest.cpp
    ${APP_CPP_DIR}/dsp/Decibel.cpp
)

# So tests can #include "dsp/Decibel.h".
target_include_directories(audiocore_tests PRIVATE ${APP_CPP_DIR})

target_link_libraries(audiocore_tests PRIVATE gtest_main)
