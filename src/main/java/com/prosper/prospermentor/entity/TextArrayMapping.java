package com.prosper.prospermentor.entity;

import java.util.ArrayList;
import java.util.List;

final class TextArrayMapping {

    private TextArrayMapping() {
    }

    static List<String> toList(String[] values) {
        if (values == null) {
            return null;
        }
        return new ArrayList<>(List.of(values));
    }

    static List<String> toListOrEmpty(String[] values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(values));
    }

    static String[] fromList(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.toArray(String[]::new);
    }

    static String[] fromListOrEmpty(List<String> values) {
        if (values == null) {
            return new String[0];
        }
        return values.toArray(String[]::new);
    }
}
