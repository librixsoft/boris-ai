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
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.Terminal;

import com.boris.chat.ChatService;
import com.boris.task.TaskAborter;

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

    // Dracula-inspired palette: fondo oscuro, morados, naranja y gris cálido.
    private static final TextColor BG           = new TextColor.RGB(28, 30, 38);    // fondo principal: más oscuro, pero sin llegar a negro puro
    private static final TextColor BG_ELEVATED  = new TextColor.RGB(39, 42, 54);    // paneles / input
    private static final TextColor FG           = new TextColor.RGB(248, 248, 242); // texto principal: blanco crema
    private static final TextColor MUTED        = new TextColor.RGB(98, 114, 164); // gris azulado suave
    private static final TextColor ACCENT       = new TextColor.RGB(189, 147, 249); // púrpura principal
    private static final TextColor USERC        = new TextColor.RGB(255, 184, 108); // naranja cálido
    private static final TextColor SELECTED_BG  = new TextColor.RGB(58, 61, 79);    // selección / resalte gris-violeta

    private static final String[] SPINNER_FRAMES =
            {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    // Líneas que mueve cada pulsación de flecha (antes 1, se sentía muy corto)
    private static final int ARROW_SCROLL_STEP = 3;

    private final ChatService chatService;
    private final TaskAborter taskAborter = new TaskAborter();
    private volatile boolean wasAborted = false;

    private Screen screen;
    private MultiWindowTextGUI gui;
    private Window window;
    private ChatPanel chatBox;
    private TextBox inputBox;
    private Label statusLabel;

    // Guardamos el texto "crudo" (sin wrap) por separado del texto ya
    // envuelto que se muestra, para poder re-envolver en cada resize sin
    // perder información.
    private final StringBuilder rawTranscript = new StringBuilder();
    private final AtomicBoolean waiting = new AtomicBoolean(false);

    // Historial de comandos estilo terminal
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private String currentInputBeforeHistory = "";

    public BorisUI(String settingsPath) throws Exception {
        this.chatService = ChatService.withTools(settingsPath, "boris");
    }

    public void start() throws Exception {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        // Deja habilitado el mouse en la TUI, pero con el scroll global del terminal
        // consumido por la ventana para que el usuario no salga del fullscreen.
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
                + "/exit para salir, /clear para limpiar, ESC para abortar tarea, Tab para mover el foco entre chat e input.");

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
        window = new BorisWindow();
        window.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));

        Panel root = new Panel(new BorderLayout());

        Label header = new Label(" boris  ·  terminal agent");
        header.setForegroundColor(ACCENT);
        root.addComponent(header, BorderLayout.Location.TOP);

        chatBox = new ChatPanel();
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

        Label hint = new Label(" /exit salir   /clear limpiar   ESC: abortar tarea   Tab: cambiar foco   ↑↓: historial (input) / scroll (chat)   PgUp/PgDn: scroll del chat");
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

            // ESC: abortar tarea actual
            if (type == KeyType.Escape) {
                if (waiting.get()) {
                    taskAborter.abort();
                    wasAborted = true;
                    waiting.set(false);
                    gui.getGUIThread().invokeLater(() -> {
                        statusLabel.setText(" aborted");
                        // Resetear el TaskAborter después de un pequeño delay
                        // para asegurar que el callback detecte el aborto primero
                        new Thread(() -> {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            taskAborter.reset();
                        }).start();
                    });
                }
                return false;
            }

            // Historial de comandos: flecha arriba
            if (type == KeyType.ArrowUp) {
                if (!commandHistory.isEmpty()) {
                    // Guardar el input actual si estamos empezando a navegar
                    if (historyIndex == -1) {
                        currentInputBeforeHistory = inputBox.getText();
                    }
                    // Navegar hacia atrás en el historial
                    if (historyIndex < commandHistory.size() - 1) {
                        historyIndex++;
                        inputBox.setText(commandHistory.get(commandHistory.size() - 1 - historyIndex));
                    }
                } else {
                    // Si no hay historial, reenviar scroll al chat
                    scrollChatBy(-ARROW_SCROLL_STEP);
                }
                return false;
            }

            // Historial de comandos: flecha abajo
            if (type == KeyType.ArrowDown) {
                if (historyIndex > 0) {
                    // Navegar hacia adelante en el historial
                    historyIndex--;
                    inputBox.setText(commandHistory.get(commandHistory.size() - 1 - historyIndex));
                } else if (historyIndex == 0) {
                    // Volver al input original
                    historyIndex = -1;
                    inputBox.setText(currentInputBeforeHistory);
                } else {
                    // Si no estamos navegando el historial, reenviar scroll al chat
                    scrollChatBy(ARROW_SCROLL_STEP);
                }
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

            // Resetear el índice del historial cuando el usuario edita el input
            if (historyIndex != -1 && type != KeyType.ArrowUp && type != KeyType.ArrowDown) {
                historyIndex = -1;
                currentInputBeforeHistory = "";
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
            chatBox.scroll(deltaLines);
        });
    }

    private void handleSubmit(String text) {
        if (text.equals("/exit") || text.equals("/quit")) {
            window.close();
            return;
        }
        if (text.equals("/clear")) {
            chatService.clearHistory();
            rawTranscript.setLength(0);
            gui.getGUIThread().invokeLater(() -> chatBox.setText(""));
            return;
        }

        // Agregar comando al historial (evitando duplicados consecutivos)
        if (!text.isEmpty() && (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(text))) {
            commandHistory.add(text);
        }
        historyIndex = -1;
        currentInputBeforeHistory = "";

        appendLine("❯ " + text);

        wasAborted = false;
        taskAborter.reset();
        waiting.set(true);
        startSpinner();

        StringBuilder assistantBuffer = new StringBuilder();
        AtomicBoolean firstChunk = new AtomicBoolean(true);

        Thread task = new Thread(() -> {
            try {
                taskAborter.startTask(Thread.currentThread());
                
                chatService.sendMessageStream(
                        text,
                        chunk -> {
                            // Chequeo de aborto en el callback
                            if (taskAborter.isAborted()) {
                                return;
                            }
                            if (chunk != null && !chunk.isEmpty()) {
                                if (firstChunk.compareAndSet(true, false)) {
                                    // Agregar salto de línea y el prefijo del asistente
                                    gui.getGUIThread().invokeLater(() -> {
                                        if (rawTranscript.length() > 0) rawTranscript.append("\n");
                                        rawTranscript.append("● ");
                                        renderTranscript();
                                        scrollToBottom();
                                    });
                                }
                                // Simplemente agregar el chunk al final, sin reemplazar
                                gui.getGUIThread().invokeLater(() -> {
                                    rawTranscript.append(chunk);
                                    renderTranscript();
                                    scrollToBottom();
                                });
                            }
                        },
                        () -> {
                            waiting.set(false);
                            // Agregar salto de línea al final de la respuesta
                            gui.getGUIThread().invokeLater(() -> {
                                rawTranscript.append("\n");
                                renderTranscript();
                                scrollToBottom();
                            });
                            String finalText = assistantBuffer.toString();
                            if (ChatService.EXIT_COMMAND.equals(finalText)) {
                                gui.getGUIThread().invokeLater(() -> window.close());
                            }
                            taskAborter.reset();
                        }
                );
            } catch (Exception e) {
                waiting.set(false);
                appendLine("✗ error: " + e.getMessage());
                taskAborter.reset();
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

    private void scrollToBottom() {
        if (chatBox != null) {
            chatBox.scrollToBottom();
        }
    }

    private void renderTranscript() {
        int width = usableChatWidth();
        String wrapped = wrap(rawTranscript.toString(), width);
        boolean wasAtBottom = chatBox != null && chatBox.isAtBottom();
        chatBox.setText(wrapped);
        if (wasAtBottom) {
            chatBox.scrollToBottom();
        }
    }

    private int usableChatWidth() {
        TerminalSize size = chatBox.getSize();
        return Math.max(10, size.getColumns() - 3);
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

    // ---- spinner ----

    private void startSpinner() {
        Thread t = new Thread(() -> {
            int i = 0;
            while (waiting.get() && !taskAborter.isAborted()) {
                String frame = SPINNER_FRAMES[i % SPINNER_FRAMES.length];
                gui.getGUIThread().invokeLater(() -> statusLabel.setText(" " + frame + " pensando..."));
                i++;
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            // Solo limpiar el label si no fue abortado manualmente
            if (!wasAborted) {
                gui.getGUIThread().invokeLater(() -> statusLabel.setText(""));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * ChatPanel: Componente flexible para mostrar texto con scrolling vertical.
     * Interactable: puede recibir el foco y manejar input (mouse y teclado).
     */
    private class BorisWindow extends BasicWindow {
        @Override
        public boolean handleInput(KeyStroke key) {
            if (key instanceof MouseAction) {
                MouseAction mouse = (MouseAction) key;
                MouseActionType type = mouse.getActionType();
                if (type == MouseActionType.SCROLL_UP) {
                    scrollChatBy(-ARROW_SCROLL_STEP);
                    return true;
                }
                if (type == MouseActionType.SCROLL_DOWN) {
                    scrollChatBy(ARROW_SCROLL_STEP);
                    return true;
                }
            }
            return super.handleInput(key);
        }
    }

    private class ChatPanel extends AbstractComponent<ChatPanel> {
        private List<String> lines = new ArrayList<>();
        private int scrollOffset = 0; // Primera línea visible

        public ChatPanel() {
            super();
            setPreferredSize(new TerminalSize(80, 20));
        }

        public boolean isAtBottom() {
            int maxOffset = Math.max(0, lines.size() - visibleLines());
            return scrollOffset >= maxOffset;
        }

        public void setText(String text) {
            this.lines = Arrays.asList(text.split("\n", -1));
            int maxOffset = Math.max(0, lines.size() - visibleLines());
            this.scrollOffset = Math.min(this.scrollOffset, maxOffset);
            invalidate();
        }

        public void scroll(int deltaLines) {
            int maxOffset = Math.max(0, lines.size() - visibleLines());
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + deltaLines));
            invalidate();
        }

        public void scrollToBottom() {
            int visible = visibleLines();
            scrollOffset = Math.max(0, lines.size() - visible);
            invalidate();
        }

        private int visibleLines() {
            TerminalSize size = getSize();
            return size == null ? 10 : Math.max(1, size.getRows());
        }

        private int scrollbarWidth() {
            return 2;
        }

        private int scrollbarX() {
            TerminalSize size = getSize();
            return size == null ? 0 : Math.max(0, size.getColumns() - scrollbarWidth());
        }

        private int scrollbarTrackHeight() {
            TerminalSize size = getSize();
            return size == null ? 1 : Math.max(1, size.getRows());
        }

        @Override
        protected ComponentRenderer<ChatPanel> createDefaultRenderer() {
            return new ComponentRenderer<ChatPanel>() {
                @Override
                public TerminalSize getPreferredSize(ChatPanel component) {
                    return component.getPreferredSize();
                }

                @Override
                public void drawComponent(TextGUIGraphics graphics, ChatPanel component) {
                    TerminalSize size = graphics.getSize();
                    if (size == null) {
                        return;
                    }

                    int visibleRows = size.getRows();
                    int contentHeight = Math.max(1, lines.size());
                    int maxOffset = Math.max(0, contentHeight - visibleRows);

                    graphics.setBackgroundColor(BG);

                    // Track if we're inside a table and if we're rendering the header
                    boolean inTable = false;
                    boolean isHeader = false;
                    List<Integer> columnWidths = new ArrayList<>();

                    for (int i = 0; i < visibleRows && scrollOffset + i < lines.size(); i++) {
                        String line = lines.get(scrollOffset + i);
                        
                        // Detect table markdown
                        if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
                            // Check if it's a separator line (contains only -, +, and |)
                            String trimmed = line.trim().replaceAll("\\|", "").trim();
                            if (trimmed.matches("^[-+\\s]+$")) {
                                // Separator line - skip but stay in table
                                inTable = true;
                                isHeader = false;
                                graphics.setBackgroundColor(BG);
                                graphics.setForegroundColor(MUTED);
                                graphics.putString(0, i, padOrTruncate(line, size.getColumns() - scrollbarWidth()));
                                continue;
                            }
                            
                            if (!inTable) {
                                // Start of new table - parse column widths from header
                                columnWidths = parseColumnWidths(line);
                                inTable = true;
                                isHeader = true;
                            } else {
                                isHeader = false;
                            }
                            
                            // Render table row with formatting
                            renderTableRow(graphics, line, i, size, isHeader, columnWidths);
                        } else {
                            // Normal line - apply markdown formatting
                            inTable = false;
                            isHeader = false;
                            columnWidths.clear();
                            renderMarkdownLine(graphics, line, i, size);
                        }
                    }

                    for (int i = (lines.size() - scrollOffset); i < visibleRows; i++) {
                        graphics.putString(0, i, padOrTruncate("", size.getColumns() - scrollbarWidth()));
                    }

                    drawScrollbar(graphics, size.getColumns(), visibleRows, maxOffset, scrollOffset);
                }

                private void renderMarkdownLine(TextGUIGraphics graphics, String line, int row, TerminalSize size) {
                    graphics.setBackgroundColor(BG);
                    
                    // Check for headers (# ## ###)
                    if (line.startsWith("###")) {
                        graphics.setForegroundColor(ACCENT);
                        String content = line.substring(3).trim();
                        graphics.putString(0, row, padOrTruncate("   " + content, size.getColumns() - scrollbarWidth()));
                        return;
                    }
                    if (line.startsWith("##")) {
                        graphics.setForegroundColor(ACCENT);
                        String content = line.substring(2).trim();
                        graphics.putString(0, row, padOrTruncate("  " + content, size.getColumns() - scrollbarWidth()));
                        return;
                    }
                    if (line.startsWith("#")) {
                        graphics.setForegroundColor(ACCENT);
                        String content = line.substring(1).trim();
                        graphics.putString(0, row, padOrTruncate(" " + content, size.getColumns() - scrollbarWidth()));
                        return;
                    }
                    
                    // Check for blockquotes (> )
                    if (line.trim().startsWith(">")) {
                        graphics.setForegroundColor(MUTED);
                        graphics.putString(0, row, padOrTruncate(line, size.getColumns() - scrollbarWidth()));
                        return;
                    }
                    
                    // Check for code blocks (``` or ```)
                    if (line.trim().startsWith("```") || line.trim().startsWith("``")) {
                        graphics.setForegroundColor(USERC);
                        graphics.setBackgroundColor(BG_ELEVATED);
                        graphics.putString(0, row, padOrTruncate(line, size.getColumns() - scrollbarWidth()));
                        return;
                    }
                    
                    // Check for inline code (`code`)
                    if (line.contains("`")) {
                        renderInlineCode(graphics, line, row, size);
                        return;
                    }
                    
                    // Check for bold (**text**)
                    if (line.contains("**")) {
                        renderBoldText(graphics, line, row, size);
                        return;
                    }
                    
                    // Check for italic (*text*)
                    if (line.contains("*") && !line.contains("**")) {
                        renderItalicText(graphics, line, row, size);
                        return;
                    }
                    
                    // Check for lists (- or *)
                    if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                        graphics.setForegroundColor(USERC);
                        graphics.putString(0, row, padOrTruncate(line, size.getColumns() - scrollbarWidth()));
                        return;
                    }
                    
                    // Check for numbered lists (1. 2. etc)
                    if (line.trim().matches("^\\d+\\..*")) {
                        graphics.setForegroundColor(USERC);
                        graphics.putString(0, row, padOrTruncate(line, size.getColumns() - scrollbarWidth()));
                        return;
                    }
                    
                    // Default text
                    graphics.setForegroundColor(FG);
                    graphics.putString(0, row, padOrTruncate(line, size.getColumns() - scrollbarWidth()));
                }

                private void renderInlineCode(TextGUIGraphics graphics, String line, int row, TerminalSize size) {
                    graphics.setBackgroundColor(BG);
                    int x = 0;
                    StringBuilder current = new StringBuilder();
                    boolean inCode = false;
                    
                    for (int i = 0; i < line.length() && x < size.getColumns() - scrollbarWidth(); i++) {
                        char c = line.charAt(i);
                        
                        if (c == '`' && (i == 0 || line.charAt(i-1) != '\\')) {
                            if (inCode) {
                                // End of code block
                                graphics.setBackgroundColor(BG_ELEVATED);
                                graphics.setForegroundColor(USERC);
                                graphics.putString(x, row, current.toString());
                                x += current.length();
                                current.setLength(0);
                                inCode = false;
                                graphics.setBackgroundColor(BG);
                                graphics.setForegroundColor(FG);
                            } else {
                                // Start of code block
                                inCode = true;
                            }
                        } else {
                            current.append(c);
                        }
                    }
                    
                    // Flush remaining text
                    if (current.length() > 0) {
                        if (inCode) {
                            graphics.setBackgroundColor(BG_ELEVATED);
                            graphics.setForegroundColor(USERC);
                        } else {
                            graphics.setBackgroundColor(BG);
                            graphics.setForegroundColor(FG);
                        }
                        graphics.putString(x, row, padOrTruncate(current.toString(), size.getColumns() - scrollbarWidth() - x));
                    }
                }

                private void renderBoldText(TextGUIGraphics graphics, String line, int row, TerminalSize size) {
                    graphics.setBackgroundColor(BG);
                    int x = 0;
                    StringBuilder current = new StringBuilder();
                    boolean inBold = false;
                    
                    for (int i = 0; i < line.length() && x < size.getColumns() - scrollbarWidth(); i++) {
                        char c = line.charAt(i);
                        
                        if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                            if (inBold) {
                                // End of bold
                                graphics.setForegroundColor(ACCENT);
                                graphics.putString(x, row, current.toString());
                                x += current.length();
                                current.setLength(0);
                                inBold = false;
                                i++; // skip second *
                                graphics.setForegroundColor(FG);
                            } else {
                                // Start of bold
                                inBold = true;
                                i++; // skip second *
                            }
                        } else {
                            current.append(c);
                        }
                    }
                    
                    // Flush remaining text
                    if (current.length() > 0) {
                        if (inBold) {
                            graphics.setForegroundColor(ACCENT);
                        } else {
                            graphics.setForegroundColor(FG);
                        }
                        graphics.putString(x, row, padOrTruncate(current.toString(), size.getColumns() - scrollbarWidth() - x));
                    }
                }

                private void renderItalicText(TextGUIGraphics graphics, String line, int row, TerminalSize size) {
                    graphics.setBackgroundColor(BG);
                    int x = 0;
                    StringBuilder current = new StringBuilder();
                    boolean inItalic = false;
                    
                    for (int i = 0; i < line.length() && x < size.getColumns() - scrollbarWidth(); i++) {
                        char c = line.charAt(i);
                        
                        if (c == '*' && (i == 0 || line.charAt(i-1) != '*')) {
                            if (inItalic) {
                                // End of italic
                                graphics.setForegroundColor(SELECTED_BG);
                                graphics.putString(x, row, current.toString());
                                x += current.length();
                                current.setLength(0);
                                inItalic = false;
                                graphics.setForegroundColor(FG);
                            } else {
                                // Start of italic
                                inItalic = true;
                            }
                        } else {
                            current.append(c);
                        }
                    }
                    
                    // Flush remaining text
                    if (current.length() > 0) {
                        if (inItalic) {
                            graphics.setForegroundColor(SELECTED_BG);
                        } else {
                            graphics.setForegroundColor(FG);
                        }
                        graphics.putString(x, row, padOrTruncate(current.toString(), size.getColumns() - scrollbarWidth() - x));
                    }
                }

                private List<Integer> parseColumnWidths(String line) {
                    List<Integer> widths = new ArrayList<>();
                    String[] parts = line.split("\\|");
                    for (String part : parts) {
                        if (!part.trim().isEmpty()) {
                            widths.add(part.trim().length() + 2); // +2 for padding
                        }
                    }
                    return widths;
                }

                private void renderTableRow(TextGUIGraphics graphics, String line, int row, TerminalSize size, boolean isHeader, List<Integer> columnWidths) {
                    String[] parts = line.split("\\|");
                    int x = 0;
                    
                    // Set colors based on whether it's header or data
                    if (isHeader) {
                        graphics.setBackgroundColor(BG_ELEVATED);
                        graphics.setForegroundColor(ACCENT);
                    } else {
                        graphics.setBackgroundColor(BG);
                        graphics.setForegroundColor(FG);
                    }
                    
                    // Draw left border
                    graphics.putString(x, row, "│");
                    x += 1;
                    
                    // Draw cells
                    for (int i = 1; i < parts.length - 1; i++) { // Skip first and last empty parts
                        String cell = parts[i].trim();
                        int cellWidth = columnWidths.size() > i - 1 ? columnWidths.get(i - 1) : 15;
                        
                        // Pad cell content
                        String paddedCell = padOrTruncate(cell, cellWidth - 1);
                        graphics.putString(x, row, paddedCell);
                        x += cellWidth;
                        
                        // Draw separator
                        graphics.putString(x, row, "│");
                        x += 1;
                    }
                    
                    // Fill remaining space
                    if (x < size.getColumns() - scrollbarWidth()) {
                        graphics.putString(x, row, padOrTruncate("", size.getColumns() - scrollbarWidth() - x));
                    }
                }

                private void drawScrollbar(TextGUIGraphics graphics, int totalWidth, int visibleRows, int maxOffset, int currentOffset) {
                    if (maxOffset <= 0) {
                        return;
                    }

                    int barX = Math.max(0, totalWidth - scrollbarWidth());
                    int barHeight = Math.max(1, visibleRows - 2);
                    int thumbHeight = Math.max(5, (visibleRows * visibleRows) / Math.max(1, visibleRows + maxOffset));
                    int thumbTop = (currentOffset * (barHeight - thumbHeight)) / Math.max(1, maxOffset);

                    for (int row = 0; row < visibleRows; row++) {
                        graphics.putString(barX, row, "│");
                    }

                    for (int row = thumbTop; row < thumbTop + thumbHeight && row < visibleRows; row++) {
                        graphics.putString(barX, row, "█");
                    }
                }

                private String padOrTruncate(String line, int width) {
                    if (width <= 0) {
                        return "";
                    }
                    if (line.length() >= width) {
                        return line.substring(0, width);
                    }
                    StringBuilder sb = new StringBuilder(line);
                    while (sb.length() < width) {
                        sb.append(' ');
                    }
                    return sb.toString();
                }
            };
        }
    }
}