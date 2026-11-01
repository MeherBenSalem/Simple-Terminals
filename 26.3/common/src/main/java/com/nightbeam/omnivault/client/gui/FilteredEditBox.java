package com.nightbeam.omnivault.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

/** A text field that refuses typed or pasted text which would leave it invalid (vanilla fields no longer filter). */
public class FilteredEditBox extends EditBox {
    private Predicate<String> filter = text -> true;

    public FilteredEditBox(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, height, narration);
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = filter;
    }

    @Override
    public void insertText(String input) {
        String before = getValue();
        int cursor = getCursorPosition();
        super.insertText(input);
        if (!filter.test(getValue())) {
            setValue(before);
            setCursorPosition(cursor);
        }
    }
}
