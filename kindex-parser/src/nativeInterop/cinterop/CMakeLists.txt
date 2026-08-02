cmake_minimum_required(VERSION 3.16)
project(treesitter C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 14)

# Force release mode / NDEBUG to disable internal tree-sitter assertions (ts_assert)
add_compile_definitions(NDEBUG)

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/src)

# Collect core Tree-sitter & Grammar sources if present
file(GLOB_RECURSE TREESITTER_SOURCES 
    "${CMAKE_CURRENT_SOURCE_DIR}/src/*.c" 
    "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp"
)

if(NOT TREESITTER_SOURCES)
    # Placeholder empty source if building against precompiled objects
    message(STATUS "No C/C++ sources found under src/, using header-only interface.")
endif()

add_library(treesitter STATIC ${TREESITTER_SOURCES})

set_target_properties(treesitter PROPERTIES
    PREFIX ""
    OUTPUT_NAME "treesitter"
)
