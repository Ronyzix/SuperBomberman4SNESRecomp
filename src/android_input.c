#include <jni.h>
#include <SDL3/SDL.h>

static SDL_Joystick *g_touch_joystick;
static SDL_JoystickID g_touch_joystick_id;

void sb4_android_pad_start(void) {
    g_touch_joystick = NULL;
    g_touch_joystick_id = 0;

    if (!SDL_InitSubSystem(SDL_INIT_JOYSTICK | SDL_INIT_GAMEPAD)) {
        SDL_LogError(SDL_LOG_CATEGORY_INPUT,
                     "Não foi possível inicializar o joystick virtual: %s",
                     SDL_GetError());
        return;
    }

    SDL_VirtualJoystickDesc desc;
    SDL_INIT_INTERFACE(&desc);
    desc.type = SDL_JOYSTICK_TYPE_GAMEPAD;
    desc.naxes = SDL_GAMEPAD_AXIS_COUNT;
    desc.nbuttons = (Uint16)(SDL_GAMEPAD_BUTTON_DPAD_RIGHT + 1);
    desc.button_mask = (1u << desc.nbuttons) - 1u;
    desc.axis_mask = (1u << SDL_GAMEPAD_AXIS_COUNT) - 1u;
    desc.name = "Super Bomberman 4 Touch Pad";

    g_touch_joystick_id = SDL_AttachVirtualJoystick(&desc);
    if (g_touch_joystick_id <= 0) {
        SDL_LogError(SDL_LOG_CATEGORY_INPUT,
                     "Não foi possível conectar o gamepad virtual: %s",
                     SDL_GetError());
        g_touch_joystick_id = 0;
        SDL_QuitSubSystem(SDL_INIT_JOYSTICK | SDL_INIT_GAMEPAD);
        return;
    }

    g_touch_joystick = SDL_OpenJoystick(g_touch_joystick_id);
    if (!g_touch_joystick) {
        SDL_LogError(SDL_LOG_CATEGORY_INPUT,
                     "Não foi possível abrir o gamepad virtual: %s",
                     SDL_GetError());
        SDL_DetachVirtualJoystick(g_touch_joystick_id);
        g_touch_joystick_id = 0;
        SDL_QuitSubSystem(SDL_INIT_JOYSTICK | SDL_INIT_GAMEPAD);
        return;
    }
    SDL_Log("Gamepad virtual do Super Bomberman 4 pronto (ID %" SDL_PRIs32 ")",
            g_touch_joystick_id);
}

void sb4_android_pad_stop(void) {
    // SDL_Quit() do host encerra a superfície; não tocar em handles que ele
    // possa já ter liberado. A limpeza do processo é responsabilidade do SDL.
    g_touch_joystick = NULL;
    g_touch_joystick_id = 0;
}

static int sdl_button_for_touch(int button) {
    switch (button) {
        // O mapeamento padrão do host é Xbox: SDL B -> Super Nintendo A,
        // SDL A -> Super Nintendo B, SDL Y -> SNES X e SDL X -> SNES Y.
        case 0: return SDL_GAMEPAD_BUTTON_EAST;          // A
        case 1: return SDL_GAMEPAD_BUTTON_SOUTH;         // B
        case 2: return SDL_GAMEPAD_BUTTON_NORTH;         // X
        case 3: return SDL_GAMEPAD_BUTTON_WEST;          // Y
        case 4: return SDL_GAMEPAD_BUTTON_LEFT_SHOULDER; // L
        case 5: return SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER;// R
        case 6: return SDL_GAMEPAD_BUTTON_START;
        case 7: return SDL_GAMEPAD_BUTTON_BACK;          // Select
        case 8: return SDL_GAMEPAD_BUTTON_DPAD_UP;
        case 9: return SDL_GAMEPAD_BUTTON_DPAD_DOWN;
        case 10: return SDL_GAMEPAD_BUTTON_DPAD_LEFT;
        case 11: return SDL_GAMEPAD_BUTTON_DPAD_RIGHT;
        default: return SDL_GAMEPAD_BUTTON_INVALID;
    }
}

JNIEXPORT void JNICALL
Java_com_manus_sbomber4_MainActivity_nativePadButton(
        JNIEnv *env, jclass clazz, jint button, jboolean pressed) {
    (void)env;
    (void)clazz;
    if (!g_touch_joystick) {
        return;
    }
    int sdl_button = sdl_button_for_touch((int)button);
    if (sdl_button != SDL_GAMEPAD_BUTTON_INVALID) {
        SDL_SetJoystickVirtualButton(g_touch_joystick, sdl_button,
                                     pressed == JNI_TRUE);
    }
}
