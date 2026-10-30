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

# Speech recognition: whisper.cpp's source compiled against the same ggml as llama.cpp,
# so both share one set of CPU backends and the app ships ggml only once.
set(WHISPER_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp)
add_library(whisper STATIC
        ${WHISPER_SRC}/src/whisper.cpp
        ${WHISPER_SRC}/src/aneforge/whisper-aneforge.cpp)
target_include_directories(whisper PUBLIC ${WHISPER_SRC}/include ${WHISPER_SRC}/src)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.9.5-aisendiri")
set_target_properties(whisper PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_link_libraries(whisper PUBLIC ggml)

add_library(aisendiri_whisper SHARED aisendiri_whisper.cpp)
target_link_libraries(aisendiri_whisper whisper ggml android log)
