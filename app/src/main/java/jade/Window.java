package jade;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import lombok.Getter;
import renderer.DebugDraw;
import renderer.Framebuffer;
import renderer.PickingTexture;
import renderer.Renderer;
import renderer.Shader;
import scenes.LevelEditorScene;
import scenes.LevelScene;
import scenes.Scene;
import util.AssetPool;
import util.Settings;

import org.lwjgl.Version;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Window {
    private int width, height;
    private String title;
    private long lpGlfwWindow;
    private ImGuiLayer imGuiLayer;
    private Framebuffer framebuffer;
    private PickingTexture pickingTexture;

    public float r, g, b, a;
    private boolean fadeToBlack = false;

    private static Window __instance = null;

    private static Scene currentScene;

    private Window() {
        this.width = 1366;
        this.height = 768;
        this.title = "Mario";
        this.r = 1;
        this.g = 1;
        this.b = 1;
        this.a = 1;
    }

    public static void changeScene(int newScene) {
        switch (newScene) {
            case 0:
                currentScene = new LevelEditorScene();
                break;
            case 1:
                currentScene = new LevelScene();
                break;
            default:
                assert false : "Unknown scene `" + newScene + "`";
                break;
        }

        currentScene.load();
        currentScene.init();
        currentScene.start();
    }

    public static Window get() {
        if (Window.__instance == null) {
            Window.__instance = new Window();
        }

        return Window.__instance;
    }

    public void run() {
        System.out.println("Hello LWJGL " + Version.getVersion() + "!");

        init();
        loop();

        // Free the memory
        glfwFreeCallbacks(lpGlfwWindow);
        glfwDestroyWindow(lpGlfwWindow);

        // Terminate GLFW and the error callback
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    public void init() {
        // Setup error callback to stderr
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        // glfwWindowHint(GLFW_MAXIMIZED, GLFW_TRUE);

        // Create the window
        lpGlfwWindow = glfwCreateWindow(this.width, this.height, this.title, NULL, NULL);
        if (lpGlfwWindow == NULL) {
            throw new IllegalStateException("Failed to create the GLFW window");
        }

        glfwSetCursorPosCallback(lpGlfwWindow, MouseListener::mousePosCallback);
        glfwSetMouseButtonCallback(lpGlfwWindow, MouseListener::mouseButtonCallback);
        glfwSetScrollCallback(lpGlfwWindow, MouseListener::mouseScrollCallback);
        glfwSetKeyCallback(lpGlfwWindow, KeyListener::keyCallback);
        glfwSetWindowSizeCallback(lpGlfwWindow, (w, newWidth, newHeight) -> {
            Window.setWidth(newWidth);
            Window.setHeight(newHeight);
        });

        // Make the OpenGL context current
        glfwMakeContextCurrent(lpGlfwWindow);

        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(lpGlfwWindow);

        // LWJGL detects the context that is current in the current thread, creates the
        // GLCapabilities instance and makes the OpenGL bindings available for use.
        GL.createCapabilities();

        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        
        this.imGuiLayer = new ImGuiLayer(lpGlfwWindow);
        this.imGuiLayer.initImGui();
        
        this.framebuffer = new Framebuffer(Settings.getScreenWidth(), Settings.getScreenHeight());
        this.pickingTexture = new PickingTexture(Settings.getScreenWidth(), Settings.getScreenHeight());
        glViewport(0, 0, Settings.getScreenWidth(), Settings.getScreenHeight());

        glfwMaximizeWindow(lpGlfwWindow);

        Window.changeScene(0);
    }

    public void loop() {
        float beginTime = (float)glfwGetTime();
        float endTime;
        float dt = -1.0f;

        Shader defaultShader = AssetPool.getShader("../assets/shaders/default.glsl");
        Shader pickingShader = AssetPool.getShader("../assets/shaders/pickingShader.glsl");

        while (!glfwWindowShouldClose(lpGlfwWindow)) {
            // Poll for window events.
            glfwPollEvents();

            // Render pass 1: Render to picking texture
            glDisable(GL_BLEND);
            pickingTexture.enableWriting();

            glViewport(0, 0, Settings.getScreenWidth(), Settings.getScreenHeight());
            glClearColor(0f, 0f, 0f, 0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            Renderer.bindShader(pickingShader);
            currentScene.render();

            if (MouseListener.mouseButtonDown(GLFW_MOUSE_BUTTON_LEFT)) {
                int x = (int)MouseListener.getScreenX();
                int y = (int)MouseListener.getScreenY();
                System.out.println(pickingTexture.readPixel(x, y));
            }

            pickingTexture.disableWriting();

            glEnable(GL_BLEND);

            // Render pass 2: Render to the game
            DebugDraw.beginFrame();
            
            this.framebuffer.bind();
            glClearColor(r, g, b, a);
            glClear(GL_COLOR_BUFFER_BIT);

            if (dt >= 0){
                DebugDraw.draw();
                Renderer.bindShader(defaultShader);
                currentScene.update(dt);
                currentScene.render();
            }

            this.framebuffer.unbind();
            
            this.imGuiLayer.update(dt, currentScene);
            glfwSwapBuffers(lpGlfwWindow);

            endTime = (float)glfwGetTime();
            dt = endTime - beginTime;
            beginTime = endTime;
        }

        currentScene.saveExit();
    }

    public static Scene getScene() {
        return get().currentScene;
    }

    public static int getWidth() {
        return get().width;
    }

    public static int getHeight() {
        return get().height;
    }

    public static void setWidth(int width) {
        get().width = width;
    }

    public static void setHeight(int height) {
        get().height = height;
    }

    public static Framebuffer getFramebuffer() {
        return get().framebuffer;
    }

    public static float getTargetAspectRatio() {
        // return 1366.0f / 768.0f;
        // return 16.0f / 9.0f;
        return (float)Settings.getScreenAspectRatio();
    }
}
