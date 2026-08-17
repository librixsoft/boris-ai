package com.boris.cli;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.SeparateTextGUIThread;
import com.googlecode.lanterna.gui2.Separator;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.Terminal;

import com.boris.chat.ChatService;

/**
 * BorisUI — TUI fullscreen estilo Claude Code / Qwen CLI, sobre Lanterna.
 *
 * Layout (BorderLayout):
 *   TOP    -> header fijo
 *   CENTER -> panel de chat (TextBox multilínea, solo lectura) con scroll
 *             y scrollbar propios, ocupa todo el espacio sobrante
 *   BOTTOM -> footer fijo: separador, línea de estado/spinner, caja de
 *             input con borde, línea de ayuda
 *
 * Lanterna se encarga de: alt-screen (bloquea el scrollback nativo de la
 * terminal), resize, doble buffer/redibujado y foco (Tab alterna entre el
 * chat y el input; con foco en el chat, flechas/PgUp/PgDn/rueda del mouse
 * scrollean).
 *
 * NOTA sobre el alt-screen: screen.startScreen() ya activa el buffer
 * alternativo (\e[?1049h), que en Terminal.app/iTerm2 debería bloquear el
 * scrollback nativo por sí solo. Si en tu máquina seguís pudiendo scrollear
 * la terminal "de fondo", casi siempre es porque el proceso se lanza sin un
 * TTY real de por medio (ej. `mvn exec:java`). Ejecutá el jar empaquetado
 * directo: `java -jar target/boris-cli-1.0.0.jar`.
 */
public class BorisUI {

    // Paleta oscura moderna (truecolor). Si tu terminal no soporta RGB de 24
    // bits vas a ver un fallback más feo; Terminal.app moderno e iTerm2 sí
    // lo soportan.
    private static final TextColor BG            = new TextColor.RGB(18, 18, 22);   // fondo casi negro
    private static final TextColor BG_ELEVATED   = new TextColor.RGB(26, 26, 32);   // paneles/input
    private static final TextColor FG            = new TextColor.RGB(226, 226, 230); // texto principal
    private static final TextColor MUTED         = new TextColor.RGB(120, 120, 130); // texto secundario
    private static final TextColor ACCENT        = new TextColor.RGB(255, 149, 90);  // naranja suave
    private static final TextColor USERC         = new TextColor.RGB(96, 205, 255);  // cyan suave
    private static final TextColor SELECTED_BG   = new TextColor.RGB(40, 40, 48);

    private static final String[] SPINNER_FRAMES =
            {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private final ChatService chatService;

    private Screen screen;
    private MultiWindowTextGUI gui;
    private Window window;
    private TextBox chatBox;
    private TextBox inputBox;
    private Label statusLabel;

    private final StringBuilder transcript = new StringBuilder();
    private final AtomicBoolean waiting = new AtomicBoolean(false);

    public BorisUI(String settingsPath) throws Exception {
        this.chatService = ChatService.withTools(settingsPath, "boris");
    }

    public void start() throws Exception {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        // Habilita captura de eventos de mouse (incluida la rueda), sin la
        // cual TextBox nunca recibe SCROLL_UP/SCROLL_DOWN aunque tenga foco.
        factory.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE_DRAG_MOVE);

        Terminal terminal = factory.createTerminal();
        screen = new TerminalScreen(terminal);
        screen.startScreen(); // entra en alt-screen: bloquea el scrollback nativo

        gui = new MultiWindowTextGUI(new SeparateTextGUIThread.Factory(), screen);
        gui.setTheme(buildDarkTheme());

        buildWindow();
        gui.addWindow(window);

        ((SeparateTextGUIThread) gui.getGUIThread()).start();

        appendLine("boris listo. Escribí un mensaje y Enter. "
                + "/exit para salir, /clear para limpiar, Tab para mover el foco entre chat e input.");

        try {
            window.waitUntilClosed();
        } finally {
            screen.stopScreen(); // restaura la terminal (vuelve el scrollback normal)
        }
    }

    private SimpleTheme buildDarkTheme() {
        SimpleTheme theme = SimpleTheme.makeTheme(
                false,        // activeIsBold
                FG,           // baseForeground
                BG,           // baseBackground
                FG,           // editableForeground (texto dentro del input)
                BG_ELEVATED,  // editableBackground (fondo del input)
                ACCENT,       // selectedForeground
                SELECTED_BG,  // selectedBackground
                BG            // guiBackground (fondo detrás de todo)
        );
        // Bordes con un gris tenue en vez del blanco/verde por defecto
        theme.addOverride(Separator.class, MUTED, BG);
        return theme;
    }

    private void buildWindow() {
        window = new BasicWindow();
        window.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));

        Panel root = new Panel(new BorderLayout());

        Label header = new Label(" boris  ·  terminal agent");
        header.setForegroundColor(ACCENT);
        root.addComponent(header, BorderLayout.Location.TOP);

        chatBox = new TextBox(new TerminalSize(1, 1), TextBox.Style.MULTI_LINE);
        chatBox.setReadOnly(true);
        root.addComponent(chatBox.withBorder(Borders.singleLine()), BorderLayout.Location.CENTER);

        Panel footer = new Panel(new LinearLayout(Direction.VERTICAL));

        footer.addComponent(new Separator(Direction.HORIZONTAL));

        statusLabel = new Label("");
        statusLabel.setForegroundColor(MUTED);
        footer.addComponent(statusLabel);

        Panel inputRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Label promptLabel = new Label("❯ ");
        promptLabel.setForegroundColor(USERC);
        inputRow.addComponent(promptLabel);

        inputBox = new TextBox(new TerminalSize(20, 1), TextBox.Style.SINGLE_LINE);
        inputBox.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        inputRow.addComponent(inputBox);

        footer.addComponent(inputRow.withBorder(Borders.singleLine()));

        Label hint = new Label(" /exit salir   /clear limpiar   Tab: cambiar foco   ↑↓ PgUp/PgDn / rueda: scroll del chat");
        hint.setForegroundColor(MUTED);
        footer.addComponent(hint);

        root.addComponent(footer, BorderLayout.Location.BOTTOM);

        window.setComponent(root);
        window.setFocusedInteractable(inputBox);

        // Enter envía el mensaje en vez de insertar salto de línea.
        // Usamos el campo inputBox directamente porque el parámetro del
        // callback está tipado como Interactable, no como TextBox.
        inputBox.setInputFilter((textBox, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter) {
                if (!waiting.get()) {
                    String text = inputBox.getText().trim();
                    if (!text.isEmpty()) {
                        inputBox.setText("");
                        handleSubmit(text);
                    }
                }
                return false; // no insertar el salto de línea
            }
            return true;
        });
    }

    private void handleSubmit(String text) {
        if (text.equals("/exit") || text.equals("/quit")) {
            window.close();
            return;
        }
        if (text.equals("/clear")) {
            transcript.setLength(0);
            gui.getGUIThread().invokeLater(() -> chatBox.setText(""));
            return;
        }

        appendLine("❯ " + text);

        waiting.set(true);
        startSpinner();

        StringBuilder assistantBuffer = new StringBuilder();
        AtomicBoolean firstChunk = new AtomicBoolean(true);

        Thread task = new Thread(() -> {
            try {
                chatService.sendMessageStream(
                        text,
                        chunk -> {
                            if (chunk != null && !chunk.isEmpty()) {
                                if (firstChunk.compareAndSet(true, false)) {
                                    waiting.set(false);
                                    appendLine("● ");
                                }
                                assistantBuffer.append(chunk);
                                replaceLastLine("● " + assistantBuffer);
                            }
                        },
                        () -> {
                            waiting.set(false);
                            String finalText = assistantBuffer.toString();
                            if (ChatService.EXIT_COMMAND.equals(finalText)) {
                                gui.getGUIThread().invokeLater(() -> window.close());
                            }
                        }
                );
            } catch (Exception e) {
                waiting.set(false);
                appendLine("✗ error: " + e.getMessage());
            }
        });
        task.setDaemon(true);
        task.start();
    }

    // ---- transcript helpers (siempre corren en el hilo de la GUI) ----

    private void appendLine(String text) {
        gui.getGUIThread().invokeLater(() -> {
            if (transcript.length() > 0) transcript.append("\n");
            transcript.append(text);
            chatBox.setText(transcript.toString());
            scrollToBottom();
        });
    }

    private void replaceLastLine(String newText) {
        gui.getGUIThread().invokeLater(() -> {
            int lastNewline = transcript.lastIndexOf("\n");
            transcript.setLength(lastNewline >= 0 ? lastNewline + 1 : 0);
            transcript.append(newText);
            chatBox.setText(transcript.toString());
            scrollToBottom();
        });
    }

    private void scrollToBottom() {
        int lastLine = Math.max(0, chatBox.getLineCount() - 1);
        chatBox.setCaretPosition(lastLine, 0);
    }

    // ---- spinner ----

    private void startSpinner() {
        Thread t = new Thread(() -> {
            int i = 0;
            while (waiting.get()) {
                String frame = SPINNER_FRAMES[i % SPINNER_FRAMES.length];
                gui.getGUIThread().invokeLater(() -> statusLabel.setText(" " + frame + " pensando..."));
                i++;
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            gui.getGUIThread().invokeLater(() -> statusLabel.setText(""));
        });
        t.setDaemon(true);
        t.start();
    }
}