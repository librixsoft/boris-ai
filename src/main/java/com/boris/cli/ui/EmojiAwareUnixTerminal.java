package com.boris.cli.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal.CtrlCBehaviour;
import com.googlecode.lanterna.terminal.ansi.UnixTerminal;

/**
 * Unix TTY terminal that writes complete codepoints over the wire.
 * The stock StreamBasedTerminal encodes each UTF-16 unit separately, so
 * surrogate pairs (emoji) are delivered to the terminal as '?' bytes.
 */
public class EmojiAwareUnixTerminal extends UnixTerminal {

    public EmojiAwareUnixTerminal() throws IOException {
        this(System.in, System.out, Charset.defaultCharset(), CtrlCBehaviour.CTRL_C_KILLS_APPLICATION);
    }

    public EmojiAwareUnixTerminal(InputStream terminalInput,
                                  OutputStream terminalOutput,
                                  Charset terminalCharset,
                                  CtrlCBehaviour ctrlCBehaviour) throws IOException {
        super(terminalInput, terminalOutput, terminalCharset, ctrlCBehaviour);
    }

    @Override
    public void putString(String string) throws IOException {
        for (byte[] chunk : TerminalTextEncoder.encode(string, getCharset())) {
            writeToTerminal(chunk);
        }
    }
}
