package com.adaptivefit.engine;

import java.text.Normalizer;
import java.util.Locale;

/** Chuan hoa chu: bo dau tieng Viet, thuong hoa, ky tu la thanh khoang trang. */
final class TextUtil {

    private TextUtil() {}

    static String normalize(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", " ")
                .trim();
        return t;
    }

    /** Doan van da chuan hoa, boc hai dau bang khoang trang de tim tu theo ranh gioi. */
    static String padded(String s) {
        return " " + normalize(s) + " ";
    }
}
