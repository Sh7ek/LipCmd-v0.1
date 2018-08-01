cmake_minimum_required(VERSION 3.4.1)

get_filename_component(SAMPLE_SRC_DIR  ${CMAKE_SOURCE_DIR}/..  ABSOLUTE)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfloat-abi=softfp -mfpu=neon")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -fno-exceptions -fno-rtti -O2 -Wno-narrowing -fPIE")

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} \
                              -Wl,--allow-multiple-definition \
                              -Wl,--whole-archive -fPIE -v")

file(GLOB_RECURSE image_utils_sources ${SAMPLE_SRC_DIR}/jni/*.*)

add_library(image_utils SHARED
            ${image_utils_sources})

target_include_directories(image_utils PRIVATE
                           ${CMAKE_SOURCE_DIR})

target_link_libraries(image_utils
                      android
                      log)