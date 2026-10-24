cmake_minimum_required(VERSION 3.20)
project(SuperBomberman4SNESRecomp C CXX)

option(SB4_ANDROID "Build the Android ARM64 application after host compiler detection" OFF)
if(SB4_ANDROID)
    set(ANDROID TRUE)
    set(CMAKE_SYSTEM_NAME Android)
    set(CMAKE_SYSTEM_VERSION 24)
    set(ANDROID_ABI "arm64-v8a")
    set(ANDROID_PLATFORM "android-24")
    set(ANDROID_STL "c++_shared")
endif()

set(CMAKE_C_STANDARD 11)

# Release pin for lobby matching. Release builds stamp the VERSION file so two
# peers can prove they are the same build; everything else stamps "dev".
file(READ "${CMAKE_CURRENT_SOURCE_DIR}/VERSION" _GAME_VER_RAW)
string(STRIP "${_GAME_VER_RAW}" GAME_RELEASE_VERSION)
set(SNESRECOMP_BUILD_VERSION "" CACHE STRING
    "Lobby release pin (empty = Release uses VERSION, else dev)")
if(SNESRECOMP_BUILD_VERSION STREQUAL "")
    get_property(_is_multi GLOBAL PROPERTY GENERATOR_IS_MULTI_CONFIG)
    if(_is_multi)
        set(SNESRECOMP_BUILD_VERSION "$<IF:$<CONFIG:Release>,${GAME_RELEASE_VERSION},dev>")
    elseif(CMAKE_BUILD_TYPE STREQUAL "Release")
        set(SNESRECOMP_BUILD_VERSION "${GAME_RELEASE_VERSION}")
    else()
        set(SNESRECOMP_BUILD_VERSION "dev")
    endif()
endif()

set(SNESRECOMP_ROOT "${CMAKE_SOURCE_DIR}/snesrecomp" CACHE PATH
    "Path to the snesrecomp checkout used by this game")
if(NOT EXISTS "${SNESRECOMP_ROOT}/runner/runner.cmake")
    message(FATAL_ERROR
        "snesrecomp submodule missing at ${SNESRECOMP_ROOT}.\n"
        "Run: git submodule update --init --recursive")
endif()
# Mod packages are part of every project, not an option. The launcher's Mods
# page, the .snesmod loader, the trusted-plugin registry and netplay's mod-set
# gate all hang off this one switch; a build without it cannot exchange mods
# with a peer that has them, and the framework says so at every lobby init.
# Must precede the include: runner.cmake reads it to add the loader sources.
set(SNESRECOMP_ENABLE_MODS ON CACHE BOOL
    "Mod package loader + trusted plugins (required for every title)" FORCE)
if(ANDROID)
    set(SNESRECOMP_EXECUTION_DEFAULT "on" CACHE STRING
        "Use available native C fast paths on Android; unsupported code keeps LLE fallback" FORCE)
endif()
include(${SNESRECOMP_ROOT}/runner/runner.cmake)

set(SB4_GAME_SOURCES
    ${SNESRECOMP_RUNNER_SOURCES}
    src/main.c
    src/host_contract.c
    src/game_rtl.c
    src/gen_stubs.c
)
if(ANDROID)
    list(APPEND SB4_GAME_SOURCES src/android_input.c)
    add_library(SuperBomberman4SNESRecomp SHARED ${SB4_GAME_SOURCES})
    set_target_properties(SuperBomberman4SNESRecomp PROPERTIES OUTPUT_NAME main)
else()
    add_executable(SuperBomberman4SNESRecomp ${SB4_GAME_SOURCES})
endif()

# ROM identity. rom_identity.txt is the one place the digests live: the build
# turns it into snesrecomp_rom_identity.h (SNESRECOMP_ROM_EXPECTED_SHA256 and
# friends) for src/main.c, and tools/regen.sh, scripts/package_release.sh and
# the release workflow read the same file. A revision bump is one edit there.
snesrecomp_rom_identity(SuperBomberman4SNESRecomp ${CMAKE_SOURCE_DIR}/rom_identity.txt)

# Generated recompiler output. Not committed: it is derived from a copyrighted
# ROM, so every developer regenerates it locally from their own copy -- and a
# release does not ship it either. With -DSNESRECOMP_SETUP_HOST=ON and an empty
# src/gen/, this builds the SETUP HOST instead: the same executable minus the
# recompiled code, whose only path is the launcher's Generate & rebuild wizard.
# That is what release zips contain; the player's machine produces the
# playable binary from their own ROM. Without the option an empty src/gen/ is
# still a hard error (the framework owns the message).
snesrecomp_target_generated_code(SuperBomberman4SNESRecomp ${CMAKE_SOURCE_DIR}/src/gen)

# The mod catalog. This names WHERE the packages are; the framework owns the
# layout they are staged into beside the executable, and stages them on every
# build OF THE EXECUTABLE -- including the launcher's own Generate & rebuild on
# a player's machine, which builds only this target and would skip a sibling
# ALL target. mods/preloaded/packages/ holds reviewed, default-disabled
# packages; players install .snesmod archives through the launcher.
# snesrecomp/docs/MOD_PACKAGES.md.
#
# It used to be `snesrecomp_target_stage_dir(... ${CMAKE_SOURCE_DIR}/mods mods)`
# -- the destination spelled per title, which is how three ports in the same
# workspace ended up shipping an empty Mods page and nothing said so. Declaring
# it is now also enforced: a project with packages that never calls this fails
# to configure.
snesrecomp_target_mod_catalog(SuperBomberman4SNESRecomp ${CMAKE_SOURCE_DIR}/mods/preloaded)
# Any other directory the game reads exe-relative (a translations/ table, ...)
# is staged with snesrecomp_target_stage_dir, never with a separate ALL target.

target_include_directories(SuperBomberman4SNESRecomp PRIVATE
    ${SNESRECOMP_RUNNER_INCLUDE_DIRS}
    # common_rtl.c includes "config.h": the desktop key-map / Config contract
    # the framework host supplies (snesrecomp_target_desktop_host below).
    ${SNESRECOMP_ROOT}/runner/src/desktop
    ${CMAKE_SOURCE_DIR}/src
    ${CMAKE_SOURCE_DIR}/recomp
)
target_link_libraries(SuperBomberman4SNESRecomp PRIVATE ${SNESRECOMP_RUNNER_LIBRARIES})
target_compile_definitions(SuperBomberman4SNESRecomp PRIVATE
    SNES_GAME_VERSION="${SNESRECOMP_BUILD_VERSION}"
    SNES_GAME_TITLE="Super Bomberman 4"
)
snesrecomp_target_sdl(SuperBomberman4SNESRecomp)
if(ANDROID)
    set(SB4_CXX_SHARED_RUNTIME
        "${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
    set(SB4_CXX_UNWIND_RUNTIME
        "${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/19/lib/linux/aarch64/libunwind.a")
    if(NOT EXISTS "${SB4_CXX_SHARED_RUNTIME}")
        message(FATAL_ERROR "libc++_shared.so do NDK não encontrado: ${SB4_CXX_SHARED_RUNTIME}")
    endif()
    if(NOT EXISTS "${SB4_CXX_UNWIND_RUNTIME}")
        message(FATAL_ERROR "libunwind.a do NDK não encontrado: ${SB4_CXX_UNWIND_RUNTIME}")
    endif()
    target_link_options(SuperBomberman4SNESRecomp PRIVATE -stdlib=libc++)
    target_link_libraries(SuperBomberman4SNESRecomp PRIVATE
        "${SB4_CXX_SHARED_RUNTIME}" "${SB4_CXX_UNWIND_RUNTIME}")
    set_target_properties(SuperBomberman4SNESRecomp PROPERTIES
        SKIP_BUILD_RPATH TRUE BUILD_RPATH "" INSTALL_RPATH "")
    if(TARGET SDL3-shared)
        target_link_options(SDL3-shared PRIVATE -stdlib=libc++)
        target_link_libraries(SDL3-shared PRIVATE
            "${SB4_CXX_SHARED_RUNTIME}" "${SB4_CXX_UNWIND_RUNTIME}")
        set_target_properties(SDL3-shared PROPERTIES
            SKIP_BUILD_RPATH TRUE BUILD_RPATH "" INSTALL_RPATH "")
    endif()
endif()
if(ANDROID AND TARGET SDL3::SDL3-shared)
    add_custom_command(TARGET SuperBomberman4SNESRecomp POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            $<TARGET_FILE:SDL3::SDL3-shared>
            $<TARGET_FILE_DIR:SuperBomberman4SNESRecomp>
        COMMENT "Staging libSDL3.so beside the Android game library"
        VERBATIM)
endif()

# The desktop host: launcher flow, ROM resolution, config.ini + [KeyMap],
# window + SDL/OpenGL presenters, audio, gamepads, the save-state browser and
# rewind filmstrip, the pacing clock, crash handlers and the post-mortem
# report. src/main.c is the shim that names this title and supplies its
# hooks; the host is the framework's and improves on a submodule pull.
# Add TIER2 once this port reads the interpreter-gap manifest after each run.
snesrecomp_target_desktop_host(SuperBomberman4SNESRecomp)
# rom.cfg (the ROM the wizard set this project up from; git-ignored) is
# staged beside the executable when none is there yet, so the first launch
# opens on that dump rather than asking for one.
if(NOT ANDROID)
    snesrecomp_target_rom_cache(SuperBomberman4SNESRecomp)
endif()

# No GUI launcher: this project was scaffolded with --no-recomp-ui.
# Add the submodule and re-run this block to get one:
#   git submodule add https://github.com/mstan/recomp-ui.git recomp-ui
#   set(RECOMP_UI_ROOT "${CMAKE_SOURCE_DIR}/recomp-ui" CACHE PATH "" FORCE)
#   include(${RECOMP_UI_ROOT}/recomp_ui.cmake)
#   recomp_target_launcher_ui(SuperBomberman4SNESRecomp CONSOLE snes)

# Netplay is not built into this target.
# No multitap: two seats, one controller per port.
