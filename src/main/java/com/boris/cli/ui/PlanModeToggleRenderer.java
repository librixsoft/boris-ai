package com.boris.cli.ui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.CheckBox;
import com.googlecode.lanterna.gui2.InteractableRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.EnumSet;

public class PlanModeToggleRenderer implements InteractableRenderer<CheckBox> {

    private static final int TRACK_LENGTH = 5;
    private static final int KNOB_WIDTH = 1;
    private static final int LABEL_TRACK_SPACING = 2;
    private static final char KNOB_ON = '●';
    private static final char KNOB_OFF = '○';
    private static final char TRACK = '─';
    private static final TextColor PLAN_MODE_COLOR = new TextColor.RGB(255, 165, 0);
    private static final TextColor LABEL_COLOR = UiTheme.IDLE;

    @Override
    public TerminalSize getPreferredSize(CheckBox component) {
        int labelWidth = RenderUtils.displayWidth(component.getLabel());
        int width = labelWidth + LABEL_TRACK_SPACING + KNOB_WIDTH + TRACK_LENGTH;
        return new TerminalSize(width, 1);
    }

    @Override
    public TerminalPosition getCursorLocation(CheckBox component) {
        return new TerminalPosition(0, 0);
    }

    @Override
    public void drawComponent(TextGUIGraphics graphics, CheckBox component) {
        boolean plan = component.isChecked();
        String label = component.getLabel();
        TextColor modeColor = plan ? PLAN_MODE_COLOR : LABEL_COLOR;
        TextCharacter trackChar = new TextCharacter(TRACK, modeColor, UiTheme.BG);
        TextCharacter knobChar = new TextCharacter(plan ? KNOB_ON : KNOB_OFF, modeColor, UiTheme.BG);

        int columns = graphics.getSize().getColumns();
        if (columns < KNOB_WIDTH + TRACK_LENGTH) {
            return;
        }

        if (plan) {
            drawTrack(graphics, trackChar, 0, TRACK_LENGTH);
            graphics.setCharacter(TRACK_LENGTH, 0, knobChar);
            int labelStart = TRACK_LENGTH + KNOB_WIDTH + LABEL_TRACK_SPACING;
            drawLabel(graphics, label, labelStart, modeColor);
        } else {
            int labelWidth = RenderUtils.displayWidth(label);
            drawLabel(graphics, label, 0, modeColor);
            int knobColumn = labelWidth + LABEL_TRACK_SPACING;
            graphics.setCharacter(knobColumn, 0, knobChar);
            drawTrack(graphics, trackChar, knobColumn + KNOB_WIDTH, TRACK_LENGTH);
        }
    }

    private static void drawTrack(TextGUIGraphics graphics, TextCharacter trackChar, int startColumn, int length) {
        for (int i = 0; i < length; i++) {
            graphics.setCharacter(startColumn + i, 0, trackChar);
        }
    }

    private static void drawLabel(TextGUIGraphics graphics, String label, int startColumn, TextColor color) {
        CodepointAwareTextRenderer.renderWithStyle(graphics, label, 0, startColumn, graphics.getSize().getColumns(),
                color, UiTheme.BG, EnumSet.noneOf(SGR.class));
    }
}
