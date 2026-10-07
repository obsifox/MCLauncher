cmake_minimum_required(VERSION 3.22.1)
project(mlcbridge C)

add_library(mlcbridge SHARED mlcbridge.c)

target_link_libraries(mlcbridge
    log
)
