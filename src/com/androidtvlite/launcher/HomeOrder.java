package com.androidtvlite.launcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Logica pura da ordem dos apps na Home (sem dependencias do Android, testavel com JDK puro). */
final class HomeOrder {

    static final int LEFT = 0;
    static final int RIGHT = 1;
    static final int UP = 2;
    static final int DOWN = 3;

    private HomeOrder() {
    }

    /**
     * Ordem salva (so os instalados e nao ocultos) seguida dos padroes que ainda nao estao nela.
     * Sem ordem salva, vale a lista padrao. Apps novos caem no fim; ocultos nao voltam.
     */
    static List<String> merge(List<String> saved, List<String> defaults,
                              Set<String> installed, Set<String> hidden) {
        Set<String> out = new LinkedHashSet<String>();
        if (saved.isEmpty()) {
            for (String k : defaults) {
                if (installed.contains(k) && !hidden.contains(k)) out.add(k);
            }
            return new ArrayList<String>(out);
        }
        for (String k : saved) {
            if (installed.contains(k) && !hidden.contains(k)) out.add(k);
        }
        for (String k : defaults) {
            if (installed.contains(k) && !hidden.contains(k) && !saved.contains(k)) out.add(k);
        }
        return new ArrayList<String>(out);
    }

    /**
     * Tira o item de {@code from} e o insere em {@code to}; os vizinhos no meio se deslocam,
     * como no iOS. Falso se algum indice estiver fora dos limites.
     */
    static boolean move(List<String> list, int from, int to) {
        if (from < 0 || from >= list.size() || to < 0 || to >= list.size()) return false;
        if (from != to) list.add(to, list.remove(from));
        return true;
    }

    /** Indice vizinho na grade, ou -1. Esquerda/direita nao atravessam a linha. */
    static int target(int index, int dir, int size, int columns) {
        int t;
        switch (dir) {
            case LEFT:
                if (index % columns == 0) return -1;
                t = index - 1;
                break;
            case RIGHT:
                if (index % columns == columns - 1) return -1;
                t = index + 1;
                break;
            case UP:
                t = index - columns;
                break;
            case DOWN:
                t = index + columns;
                // Linha de baixo incompleta: vai para o ultimo item em vez de ficar travado.
                if (t >= size && index / columns < (size - 1) / columns) t = size - 1;
                break;
            default:
                return -1;
        }
        return t >= 0 && t < size ? t : -1;
    }

    static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    static List<String> split(String s) {
        List<String> out = new ArrayList<String>();
        if (s == null || s.isEmpty()) return out;
        for (String p : s.split("\n")) {
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }
}
