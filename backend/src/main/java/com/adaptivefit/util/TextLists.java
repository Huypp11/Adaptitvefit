package com.adaptivefit.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Luu danh sach ngan (cac buoc tap, duong dan anh) trong mot cot text, moi dong mot phan tu. */
public final class TextLists {

    private TextLists() {}

    public static List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static String join(Collection<String> items) {
        if (items == null) return null;
        List<String> clean = items.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        return clean.isEmpty() ? null : String.join("\n", clean);
    }
}
