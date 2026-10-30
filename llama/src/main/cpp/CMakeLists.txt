cmake_minimum_required(VERSION 3.22.1)

project("aisendiri_llama" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Mirrors llama.cpp's own Android example: every CPU variant is built as a separate
# library and the best one for the phone is picked at runtime.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
    set(GGML_OPENMP ON CACHE BOOL "" FORCE)
else()
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(aisendiri_llama SHARED aisendiri_llama.cpp)
target_link_libraries(aisendiri_llama llama ggml android log)
