package dev.client.api.nullcry.render;

import dev.client.api.nullcry.ClientApi;

import static org.lwjgl.opengl.GL11.*;

public class StencilUtil implements ClientApi {

    public static void enable() {
        glStencilMask(0xFF);
        glClear(GL_STENCIL_BUFFER_BIT);
        glEnable(GL_STENCIL_TEST);
        glStencilFunc(GL_ALWAYS, 1, 1);
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE);
        glDisable(GL_DEPTH_TEST);
        glColorMask(false, false, false, false);
    }

    public static void disable() {
        glDisable(GL_STENCIL_TEST);
        glEnable(GL_DEPTH_TEST);
    }

    public static void read(int ref) {
        glColorMask(true, true, true, true);
        glStencilFunc(GL_EQUAL, ref, 1);
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    }
}
