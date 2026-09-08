#include <windows.h>
#include <stdint.h>
#include <chrono>
#include <string>

typedef int64_t (*NowFn)();
typedef bool (*WaitFn)(int);

static HMODULE hWpiUtil = NULL;
static NowFn realNow = NULL;
static WaitFn realWait = NULL;

static void init() {
    if (!hWpiUtil) {
        hWpiUtil = LoadLibraryA("wpiutil.dll");
        if (hWpiUtil) {
            realNow = (NowFn)GetProcAddress(hWpiUtil, "?Now@util@wpi@@YA_JXZ");
            realWait = (WaitFn)GetProcAddress(hWpiUtil, "?WaitForObject@util@wpi@@YA_NH@Z");
        }
    }
}

extern "C" uint64_t wpi_util_now() {
    init();
    if (realNow) {
        return (uint64_t)realNow();
    }
    return (uint64_t)std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

extern "C" bool wpi_wait_for_object(uint32_t handle) {
    init();
    if (realWait) {
        return realWait((int)handle);
    }
    return WaitForSingleObject((HANDLE)(uintptr_t)handle, INFINITE) == WAIT_OBJECT_0;
}

extern "C" void* fmt_vformat(void* ret_str, void* arg1, void* arg2) {
    // In MSVC x64, return-by-value std::string passes pointer to destination string in RCX.
    // Construct an empty std::string at ret_str
    if (ret_str) {
        std::string* s = (std::string*)ret_str;
        new (s) std::string("");
    }
    return ret_str;
}

BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {
    return TRUE;
}
