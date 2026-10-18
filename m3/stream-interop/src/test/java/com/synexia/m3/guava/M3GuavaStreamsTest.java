// SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
// SPDX-License-Identifier: Apache-2.0
package com.synexia.m3.guava;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class M3GuavaStreamsTest {
    @Test void orderedDuplicateValuesAndLambdaReuse() {
        var data = ArrayListMultimap.<String,Integer>create();
        data.put("x",1); data.put("x",1);
        var frozen = M3GuavaStreams.snapshotValues(data,"x");
        var live = M3GuavaStreams.liveValues(data,"x");
        data.put("x",3);
        assertEquals(List.of(1,1), frozen.collect(Collectors.toList()));
        assertEquals(List.of(1,1,3), live.collect(Collectors.toList()));
        assertEquals(List.of(2,2,6), live.map(x -> x*2).collect(Collectors.toList()));
        assertEquals(List.of(3), live.filter(x -> x>1).collect(Collectors.toList()));
        var bag = HashMultiset.<Integer>create(); bag.add(9,3);
        assertEquals(3L,M3GuavaStreams.snapshotMultiset(bag).count());
    }

    @Test void allIterableCollectionFamiliesUseTheSameOperators() {
        var list = new java.util.ArrayList<>(List.of(4,4));
        var snap = M3GuavaStreams.snapshotAny(list);
        var live = M3GuavaStreams.liveAny(list);
        list.add(5);
        assertEquals(List.of(4,4),snap.collect(Collectors.toList()));
        assertEquals(List.of(4,4,5),live.collect(Collectors.toList()));
        assertEquals(List.of(8,8,10),live.map(v -> v*2).collect(Collectors.toList()));
    }
}
