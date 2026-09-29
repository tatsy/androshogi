cmake_minimum_required(VERSION 3.18)
project("androshogi" LANGUAGES C CXX)

include(CheckCXXCompilerFlag)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

message(STATUS "CMake System Processor: ${CMAKE_SYSTEM_PROCESSOR}")

add_library(${CMAKE_PROJECT_NAME} SHARED)

if (UNIX)
    # Unix common compiler flags
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
            "-Wno-enum-constexpr-conversion")

#    if (CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|x86")
#        # Unix SSE 4.2
#        check_cxx_compiler_flag("-msse4.2" HAVE_SSE42)
#        if (HAVE_SSE42)
#            add_definitions("-DHAVE_SSE4" "-DHAVE_SSE42")
#            target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "-msse4.2")
#        endif()
#
#        # Unix AVX2
#        check_cxx_compiler_flag("-mavx2" HAVE_AVX2)
#        if (HAVE_AVX2)
#            add_definitions("-DHAVE_AVX2")
#            target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "-mavx2")
#        endif()
#    endif()
#
#    if (CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64|arm")
#        # Unix ARMv8-A
#        check_cxx_compiler_flag("-march=armv8-a" HAVE_ARMV8A)
#        if (HAVE_ARMV8A)
#            add_definitions("-DHAVE_ARMV8A")
#            target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "-march=armv8-a")
#        endif()
#    endif()
elseif (MSVC)
    # Visual Studio SSE 4.2
    target_compile_options("/arch:SSE42" HAVE_SSE42)
    if (HAVE_SSE42)
        add_definitions("-DHAVE_SSE4" "-DHAVE_SSE42")
        target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "/arch:SSE42")
    endif()

    # Visual Studio AVX2
    target_compile_options("/arch:AVX2" HAVE_AVX2)
    if (HAVE_AVX2)
        add_definitions("-DHAVE_AVX2")
        target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE "/arch:AVX2")
    endif()
endif()

# Source files
target_sources(${CMAKE_PROJECT_NAME} PUBLIC
    cshogi/jni.cpp
    cshogi/bitboard.cpp
    cshogi/bitboard.hpp
    cshogi/book.cpp
    cshogi/book.hpp
    cshogi/color.hpp
    cshogi/common.cpp
    cshogi/common.hpp
    cshogi/cshogi.h
    cshogi/dfpn.cpp
    cshogi/dfpn.h
    cshogi/generateMoves.cpp
    cshogi/generateMoves.hpp
    cshogi/hand.cpp
    cshogi/hand.hpp
    cshogi/ifdef.hpp
    cshogi/init.cpp
    cshogi/init.hpp
    cshogi/mate.cpp
    cshogi/mate.h
    cshogi/move.cpp
    cshogi/move.hpp
    cshogi/mt64bit.cpp
    cshogi/mt64bit.hpp
    cshogi/overloadEnumOperators.hpp
    cshogi/parser.h
    cshogi/piece.hpp
    cshogi/position.cpp
    cshogi/position.hpp
    cshogi/search.cpp
    cshogi/square.cpp
    cshogi/square.hpp
    cshogi/usi.cpp
    cshogi/usi.hpp)

# Link android libraries
target_link_libraries(${CMAKE_PROJECT_NAME} PUBLIC android log)
