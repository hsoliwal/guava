// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.m3.collections.guava.collect;

import java.lang.reflect.Field;
import java.util.*;

/** Public-result oracle plus exact callback/identity transcript and retained-tail accounting. */
public final class TopKArrayContract {
    private static long checks, transcript = 1, comparisonTrace = 1;
    private static final class Token {
        final int rank, id;
        Token(int rank, int id) { this.rank = rank; this.id = id; }
    }
    private static int rank(Token value) { return value == null ? Integer.MIN_VALUE : value.rank; }
    private static int identity(Token value) { return value == null ? -1 : value.id; }
    private static void require(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("check " + checks);
    }
    private static final Comparator<Token> ORDER = (a, b) -> {
        comparisonTrace = comparisonTrace * 31 + identity(a);
        comparisonTrace = comparisonTrace * 31 + identity(b);
        return Integer.compare(rank(a), rank(b));
    };
    public static void main(String[] args) throws Exception {
        Random random = new Random(831793);
        for (int k = 0; k <= 32; k++) for (int trial = 0; trial < 120; trial++) {
            int n = random.nextInt(513);
            ArrayList<Token> input = new ArrayList<>();
            var left = TopKSelector.<Token>least(k, ORDER);
            var right = TopKSelector.<Token>least(k, ORDER);
            for (int i = 0; i < n; i++) {
                Token t = i % 19 == 0 ? null : new Token(random.nextInt(37) - 18, i);
                input.add(t);
                (i % 2 == 0 ? left : right).offer(t);
            }
            left.combine(right);
            List<Token> actual = left.topK();
            input.sort(Comparator.comparingInt(TopKArrayContract::rank));
            require(actual.size() == Math.min(k, n));
            for (int i = 0; i < actual.size(); i++) {
                require(rank(actual.get(i)) == rank(input.get(i)));
                require(input.contains(actual.get(i)));
                transcript = transcript * 31 + identity(actual.get(i));
            }
            Object[] snapshot = actual.toArray();
            left.offer(new Token(-100, 99999));
            require(Arrays.equals(snapshot, actual.toArray()));
            try { actual.add(null); throw new AssertionError("mutable result"); }
            catch (UnsupportedOperationException expected) { checks++; }
            transcript = transcript * 31 + left.topK().size();
        }
        for (int failAt = 1; failAt <= 80; failAt++) {
            final int fail = failAt;
            int[] calls = {0};
            Comparator<Integer> cmp = (a, b) -> {
                if (++calls[0] == fail) throw new IllegalArgumentException("comparison " + fail);
                return a.compareTo(b);
            };
            var top = TopKSelector.least(8, cmp);
            try { for (int i = 64; i >= 0; i--) top.offer(i); top.topK(); }
            catch (IllegalArgumentException expected) { require(expected.getMessage().equals("comparison " + fail)); }
            transcript = transcript * 31 + calls[0];
        }
        Field field = TopKSelector.class.getDeclaredField("buffer");
        field.setAccessible(true);
        int retained = 0;
        for (int k : new int[]{1, 2, 8, 32, 1024}) {
            var top = TopKSelector.<Token>least(k, ORDER);
            for (int i = 2 * k; i > 0; i--) top.offer(new Token(i, i));
            Object[] buffer = (Object[]) field.get(top);
            require(buffer.length == 2 * k);
            for (int i = k; i < buffer.length; i++) if (buffer[i] != null) retained++;
            require(top.topK().size() == k);
        }
        require(retained == (Boolean.getBoolean("m3.candidate") ? 0 : 1067));
        System.out.println("checks=" + checks + " transcript=" + transcript + " comparisons=" + comparisonTrace);
        System.out.println("discardedTailReferences=" + retained);
    }
}
