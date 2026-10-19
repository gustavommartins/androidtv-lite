package com.androidtvlite.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeOrderTest {

    private static List<String> l(String... a) {
        return new ArrayList<String>(Arrays.asList(a));
    }

    private static Set<String> s(String... a) {
        return new HashSet<String>(Arrays.asList(a));
    }

    private static void eq(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + ": esperado=" + expected + " atual=" + actual);
        }
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // merge
        eq(l("a", "b"), HomeOrder.merge(l(), l("a", "b", "c"), s("a", "b"), s()),
                "primeiro uso usa defaults instalados");
        eq(l("b", "a", "c"), HomeOrder.merge(l("b", "a"), l("a", "b", "c"), s("a", "b", "c"), s()),
                "ordem salva preservada e app novo no fim");
        eq(l("b", "a", "x"), HomeOrder.merge(l("b", "a"), l("a", "x"), s("a", "b", "x"), s()),
                "app novo (default) vai ao fim");
        eq(l("b", "c"), HomeOrder.merge(l("b", "a"), l("a", "b", "c"), s("a", "b", "c"), s("a")),
                "oculto nao volta (salvo)");
        eq(l("b"), HomeOrder.merge(l(), l("a", "b"), s("a", "b"), s("a")),
                "oculto nao volta (defaults)");
        eq(l("b"), HomeOrder.merge(l("a", "b"), l("a", "b"), s("b"), s()),
                "desinstalado some");
        eq(l("a", "b"), HomeOrder.merge(l("a", "a", "b"), l("a", "b", "b"), s("a", "b"), s()),
                "sem duplicatas");
        eq(l(), HomeOrder.merge(l(), l("a"), s(), s()), "nada instalado");

        // move
        List<String> m = l("a", "b", "c");
        check(HomeOrder.move(m, 0, 2), "move valido");
        eq(l("b", "c", "a"), m, "insercao para frente desloca vizinhos");
        check(HomeOrder.move(m, 2, 0), "move de volta");
        eq(l("a", "b", "c"), m, "insercao para tras desloca vizinhos");
        m = l("a", "b", "c");
        check(!HomeOrder.move(m, -1, 1), "from negativo");
        check(!HomeOrder.move(m, 0, 3), "to == size");
        check(!HomeOrder.move(m, 3, 0), "from == size");
        check(!HomeOrder.move(m, 0, -1), "to negativo");
        eq(l("a", "b", "c"), m, "lista intacta em falha");
        check(HomeOrder.move(m, 1, 1), "mesmo indice valido");
        eq(l("a", "b", "c"), m, "mesmo indice nao altera");
        check(!HomeOrder.move(l(), 0, 0), "lista vazia");

        // target: grade de 3 colunas, 8 itens (ultima linha incompleta)
        int size = 8, cols = 3;
        eq(1, HomeOrder.target(0, HomeOrder.RIGHT, size, cols), "direita");
        eq(-1, HomeOrder.target(2, HomeOrder.RIGHT, size, cols), "direita na borda da linha");
        eq(-1, HomeOrder.target(3, HomeOrder.LEFT, size, cols), "esquerda no inicio da linha");
        eq(4, HomeOrder.target(5, HomeOrder.LEFT, size, cols), "esquerda");
        eq(-1, HomeOrder.target(7, HomeOrder.RIGHT, size, cols), "direita no ultimo item incompleto");
        eq(-1, HomeOrder.target(0, HomeOrder.LEFT, size, cols), "esquerda no 0");
        eq(3, HomeOrder.target(0, HomeOrder.DOWN, size, cols), "baixo");
        eq(7, HomeOrder.target(4, HomeOrder.DOWN, size, cols), "baixo ate ultima linha");
        eq(7, HomeOrder.target(5, HomeOrder.DOWN, size, cols), "baixo em linha incompleta vai ao ultimo");
        eq(-1, HomeOrder.target(6, HomeOrder.DOWN, size, cols), "baixo na ultima linha");
        eq(-1, HomeOrder.target(7, HomeOrder.DOWN, size, cols), "baixo no ultimo");
        eq(0, HomeOrder.target(3, HomeOrder.UP, size, cols), "cima");
        eq(-1, HomeOrder.target(2, HomeOrder.UP, size, cols), "cima na primeira linha");
        eq(-1, HomeOrder.target(0, 99, size, cols), "direcao invalida");
        eq(-1, HomeOrder.target(0, HomeOrder.RIGHT, 1, 1), "uma coluna: direita");
        eq(-1, HomeOrder.target(0, HomeOrder.LEFT, 1, 1), "uma coluna: esquerda");
        eq(1, HomeOrder.target(0, HomeOrder.DOWN, 2, 1), "uma coluna: baixo");

        // split / join
        eq(l(), HomeOrder.split(""), "split vazio");
        eq(l(), HomeOrder.split(null), "split null");
        eq("", HomeOrder.join(l()), "join vazio");
        eq("a\nb\nc", HomeOrder.join(l("a", "b", "c")), "join");
        eq("a", HomeOrder.join(l("a")), "join um item");
        eq(l("a", "b", "c"), HomeOrder.split("a\nb\nc"), "split");
        List<String> rt = l("com.x/.A", "com.y/.B", "z");
        eq(rt, HomeOrder.split(HomeOrder.join(rt)), "round-trip");

        System.out.println("OK");
    }
}
