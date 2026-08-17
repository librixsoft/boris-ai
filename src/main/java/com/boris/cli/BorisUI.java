package com.boris.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Border;
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
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
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
 *   CENTER -> panel de chat (TextBox multilínea, solo lectura), con wrap
 *             manual de texto (nunca hay scroll horizontal) y scroll
 *             vertical automático
 *   BOTTOM -> footer fijo: separador, línea de estado/spinner, caja de
 *             input a todo el ancho, línea de ayuda
 *
 * Scroll: funciona con Tab + flechas/PgUp/PgDn sobre el chat, con la rueda
 * del mouse si el terminal la reporta, y ADEMÁS interceptamos
 * flecha arriba/abajo/PgUp/PgDn mientras el foco está en el input — esto es
 * necesario porque muchos terminales (iTerm2 con "scroll wheel sends arrow
 * keys in alternate screen") traducen el gesto de trackpad en pulsaciones
 * de flecha en vez de eventos de mouse reales, y esas flechas se perderían
 * en el input si no las reenviamos manualmente al chat.
 */
public class BorisUI {

    // Paleta oscura, sin grises neutros: los bordes usan un tono cálido muy
    // oscuro en vez de gris plano.
    private static final TextColor BG           = new TextColor.RGB(2, 2, 3);     // fondo negro casi absoluto
    private static final TextColor BG_ELEVATED  = new TextColor.RGB(9, 8, 10);    // input / paneles elevados
    private static final TextColor FG           = new TextColor.RGB(140, 200, 255); // texto principal, azul claro
    private static final TextColor MUTED        = new TextColor.RGB(60, 55, 70);   // detalles tenues (púrpura oscuro)
    private static final TextColor ACCENT       = new TextColor.RGB(180, 160, 255); // púrpura claro
    private static final TextColor USERC        = new TextColor.RGB(140, 200, 255); // azul claro
    private static final TextColor SELECTED_BG  = new TextColor.RGB(22, 18, 30);

    private static final String[] SPINNER_FRAMES =
            {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    // Líneas que mueve cada pulsación de flecha (antes 1, se sentía muy corto)
    private static final int ARROW_SCROLL_STEP = 3;

    private final ChatService chatService;

    private Screen screen;
    private MultiWindowTextGUI gui;
    private Window window;
    private TextBox chatBox;
    private TextBox inputBox;
    private Label statusLabel;

    // Guardamos el texto "crudo" (sin wrap) por separado del texto ya
    // envuelto que se muestra, para poder re-envolver en cada resize sin
    // perder información.
    private final StringBuilder rawTranscript = new StringBuilder();
    private final AtomicBoolean waiting = new AtomicBoolean(false);

    public BorisUI(String settingsPath) throws Exception {
        this.chatService = ChatService.withTools(settingsPath, "boris");
    }

    public void start() throws Exception {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        factory.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE_DRAG_MOVE);

        Terminal terminal = factory.createTerminal();

        // Refuerzo: además de configurarlo en el factory, lo seteamos
        // directo sobre el terminal ya creado. En algunas builds el valor
        // del factory no queda aplicado si se llega acá por createTerminal()
        // en vez de createScreen().
        if (terminal instanceof com.googlecode.lanterna.terminal.ExtendedTerminal) {
            ((com.googlecode.lanterna.terminal.ExtendedTerminal) terminal)
                    .setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE_DRAG_MOVE);
        }

        screen = new TerminalScreen(terminal);
        screen.startScreen();

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
            screen.stopScreen();
        }
    }

    private SimpleTheme buildDarkTheme() {
        SimpleTheme theme = SimpleTheme.makeTheme(
                false,
                FG,
                BG,
                FG,
                BG_ELEVATED,
                ACCENT,
                SELECTED_BG,
                BG
        );
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
        statusLabel.setForegroundColor(ACCENT);
        footer.addComponent(statusLabel);

        // BorderLayout en vez de LinearLayout: LEFT para el prompt fijo,
        // CENTER para el input, que así ocupa TODO el ancho restante real
        // (con LinearLayout + Fill el input no crecía en el eje horizontal).
        Panel inputRow = new Panel(new BorderLayout());
        Label promptLabel = new Label("❯ ");
        promptLabel.setForegroundColor(USERC);
        inputRow.addComponent(promptLabel, BorderLayout.Location.LEFT);

        inputBox = new TextBox(new TerminalSize(1, 1), TextBox.Style.SINGLE_LINE);
        inputRow.addComponent(inputBox, BorderLayout.Location.CENTER);

        // BUG FIX: footer usa LinearLayout(VERTICAL), que por defecto NO
        // estira sus hijos al ancho completo, solo les da su tamaño
        // preferido — por eso el input quedaba como una cajita chiquita.
        // Con Alignment.Fill forzamos que ocupe todo el ancho disponible.
        // OJO: withBorder(...) devuelve un Border (no un Panel), pero
        // Border también implementa Component y tiene setLayoutData.
        Border inputRowBordered = inputRow.withBorder(Borders.singleLine());
        inputRowBordered.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        footer.addComponent(inputRowBordered);

        Label hint = new Label(" /exit salir   /clear limpiar   Tab: cambiar foco   ↑↓ PgUp/PgDn: scroll del chat");
        hint.setForegroundColor(ACCENT);
        footer.addComponent(hint);

        root.addComponent(footer, BorderLayout.Location.BOTTOM);

        window.setComponent(root);
        window.setFocusedInteractable(inputBox);

        // Si la terminal se redimensiona, re-envolvemos el texto ya
        // acumulado al nuevo ancho para que nunca aparezca scroll horizontal.
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onResized(Window w, TerminalSize oldSize, TerminalSize newSize) {
                gui.getGUIThread().invokeLater(() -> {
                    renderTranscript();
                    scrollToBottom();
                });
            }
        });

        inputBox.setInputFilter((textBox, keyStroke) -> {
            KeyType type = keyStroke.getKeyType();

            if (type == KeyType.Enter) {
                if (!waiting.get()) {
                    String text = inputBox.getText().trim();
                    if (!text.isEmpty()) {
                        inputBox.setText("");
                        handleSubmit(text);
                    }
                }
                return false;
            }

            // Reenvía scroll al chat aunque el foco esté en el input —
            // necesario para trackpads que se traducen en flechas.
            if (type == KeyType.ArrowUp) {
                scrollChatBy(-ARROW_SCROLL_STEP);
                return false;
            }
            if (type == KeyType.ArrowDown) {
                scrollChatBy(ARROW_SCROLL_STEP);
                return false;
            }
            if (type == KeyType.PageUp) {
                scrollChatBy(-visibleChatRows());
                return false;
            }
            if (type == KeyType.PageDown) {
                scrollChatBy(visibleChatRows());
                return false;
            }

            return true;
        });
    }

    private int visibleChatRows() {
        TerminalSize size = chatBox.getSize();
        return size == null ? 10 : Math.max(1, size.getRows() - 1);
    }

    private void scrollChatBy(int deltaLines) {
        gui.getGUIThread().invokeLater(() -> {
            int lineCount = chatBox.getLineCount();
            if (lineCount == 0) {
                return;
            }
            int currentRow = chatBox.getCaretPosition().getRow();
            int newRow = Math.max(0, Math.min(lineCount - 1, currentRow + deltaLines));
            chatBox.setCaretPosition(newRow, 0);
        });
    }

    private void handleSubmit(String text) {
        if (text.equals("/exit") || text.equals("/quit")) {
            window.close();
            return;
        }
        if (text.equals("/clear")) {
            rawTranscript.setLength(0);
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
            if (rawTranscript.length() > 0) rawTranscript.append("\n");
            rawTranscript.append(text);
            renderTranscript();
            scrollToBottom();
        });
    }

    private void replaceLastLine(String newText) {
        gui.getGUIThread().invokeLater(() -> {
            int lastNewline = rawTranscript.lastIndexOf("\n");
            rawTranscript.setLength(lastNewline >= 0 ? lastNewline + 1 : 0);
            rawTranscript.append(newText);
            renderTranscript();
            scrollToBottom();
        });
    }

    /**
     * Re-envuelve rawTranscript según el ancho actual del chatBox y lo
     * vuelca al componente. Esto es lo que elimina el scroll horizontal:
     * ninguna línea visible es más larga que el ancho disponible.
     */
    private void renderTranscript() {
        int width = usableChatWidth();
        chatBox.setText(wrap(rawTranscript.toString(), width));
    }

    private int usableChatWidth() {
        TerminalSize size = chatBox.getSize();
        // -1 para dejar lugar a la barra de scroll vertical cuando aparece.
        int cols = (size == null ? 80 : size.getColumns()) - 1;
        return Math.max(10, cols);
    }

    private String wrap(String text, int width) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (String rawLine : text.split("\n", -1)) {
            if (rawLine.isEmpty()) {
                out.append('\n');
                continue;
            }
            List<String> pieces = wrapLine(rawLine, width);
            for (String piece : pieces) {
                out.append(piece).append('\n');
            }
        }
        if (out.length() > 0) {
            out.setLength(out.length() - 1); // saca el último \n de más
        }
        return out.toString();
    }

    private List<String> wrapLine(String line, int width) {
        List<String> result = new ArrayList<>();
        if (line.length() <= width) {
            result.add(line);
            return result;
        }
        String[] words = line.split(" ", -1);
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            // Palabra más larga que el ancho: se corta a la fuerza.
            while (word.length() > width) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                result.add(word.substring(0, width));
                word = word.substring(width);
            }
            int extra = current.length() == 0 ? 0 : 1;
            if (current.length() + extra + word.length() > width) {
                result.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                if (current.length() > 0) current.append(' ');
                current.append(word);
            }
        }
        result.add(current.toString());
        return result;
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