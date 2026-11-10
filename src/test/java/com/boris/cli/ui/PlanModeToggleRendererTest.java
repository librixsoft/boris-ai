package com.boris.cli.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.CheckBox;

class PlanModeToggleRendererTest {

    private static final int TRACK_LENGTH = 5;
    private static final int KNOB_WIDTH = 1;
    private static final int LABEL_TRACK_SPACING = 2;
    private static final int WRITE_LABEL_WIDTH = 10;
    private static final int PLAN_LABEL_WIDTH = 9;

    private final PlanModeToggleRenderer renderer = new PlanModeToggleRenderer();

    @Test
    void getPreferredSizeEnModoWriteIncluyeLabelTrackYKnob() {
        CheckBox checkBox = new CheckBox("WRITE MODE");

        TerminalSize size = renderer.getPreferredSize(checkBox);

        int expectedWidth = WRITE_LABEL_WIDTH + LABEL_TRACK_SPACING + KNOB_WIDTH + TRACK_LENGTH;
        assertEquals(expectedWidth, size.getColumns());
        assertEquals(1, size.getRows());
    }

    @Test
    void getPreferredSizeEnModoPlanIncluyeTrackKnobYLabel() {
        CheckBox checkBox = new CheckBox("PLAN MODE");
        checkBox.setChecked(true);

        TerminalSize size = renderer.getPreferredSize(checkBox);

        int expectedWidth = PLAN_LABEL_WIDTH + LABEL_TRACK_SPACING + KNOB_WIDTH + TRACK_LENGTH;
        assertEquals(expectedWidth, size.getColumns());
        assertEquals(1, size.getRows());
    }

    @Test
    void getPreferredSizeConLabelVacioDevuelveSoloElTrack() {
        CheckBox checkBox = new CheckBox("");

        TerminalSize size = renderer.getPreferredSize(checkBox);

        assertEquals(KNOB_WIDTH + LABEL_TRACK_SPACING + TRACK_LENGTH, size.getColumns());
        assertEquals(1, size.getRows());
    }

    @Test
    void getPreferredSizeMideElLabelPorDisplayWidth() {
        CheckBox checkBox = new CheckBox("WRITE MODE\uDB40\uDC00");

        TerminalSize size = renderer.getPreferredSize(checkBox);

        int expectedWidth = WRITE_LABEL_WIDTH + LABEL_TRACK_SPACING + KNOB_WIDTH + TRACK_LENGTH;
        assertEquals(expectedWidth, size.getColumns());
        assertEquals(1, size.getRows());
    }
}
