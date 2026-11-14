/*
 * Copyright 2026 Hitesh Soliwal and contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** JDK 21 independent behavioral comparison of AbstractCollection.contains and slot traversal. */
public final class ContainsValueArrayProof {
    private static long assertions;
    private static final Object MATCH = new Object();
    private static final Object OTHER = new Object();

    private static final class Store {
        Object[] payload = new Object[128];
        int[] next = new int[128];
        int head = -1, tail = -1, used, modCount;
        int append(Object value, boolean newKey) {
            if (used == payload.length) {
                payload = Arrays.copyOf(payload, used * 2);
                next = Arrays.copyOf(next, used * 2);
            }
            int slot = used++;
            payload[slot] = value;
            next[slot] = -1;
            if (head < 0) head = slot;
            else next[tail] = slot;
            tail = slot;
            if (newKey) modCount++;
            return slot;
        }
        void removeNext(int slot, boolean distinctKey) {
            int victim = next[slot];
            if (victim < 0) return;
            next[slot] = next[victim];
            payload[victim] = null;
            if (tail == victim) tail = slot;
            if (distinctKey) modCount++;
        }
        void clear() {head = tail = -1; modCount++;}
        Collection<Object> oldView() {
            return new AbstractCollection<>() {
                public int size() {return used;}
                public Iterator<Object> iterator() {
                    return new Iterator<>() {
                        final int expected = modCount;
                        int cursor = head;
                        private void check() {if(expected!=modCount)throw new ConcurrentModificationException();}
                        public boolean hasNext() {check();return cursor>=0;}
                        public Object next() {check();if(cursor<0)throw new NoSuchElementException();
                            int slot=cursor;cursor=Store.this.next[slot];return payload[slot];}
                    };
                }
            };
        }
        boolean newContains(Object query) {
            // Only built-in final equals implementations can take the direct
            // array scan. Opaque user equals may mutate the list during search.
            if (query != null && !(query instanceof String) && !(query instanceof Integer)
                    && !(query instanceof Long) && !(query instanceof Short)
                    && !(query instanceof Byte) && !(query instanceof Character)
                    && !(query instanceof Boolean) && !(query instanceof Float)
                    && !(query instanceof Double) && !(query instanceof Enum<?>)) {
                return oldView().contains(query);
            }
            int expected = modCount, slot = head;
            for (;;) {
                if (expected != modCount) throw new ConcurrentModificationException();
                if (slot == -1) return false;
                int successor = next[slot];
                Object element = payload[slot];
                if (query == null ? element == null : query.equals(element)) return true;
                slot = successor;
            }
        }
    }
    static String outcome(Store s, boolean old, Object query) {
        try {return "value:" + (old ? s.oldView().contains(query) : s.newContains(query));}
        catch (RuntimeException x) {return "exception:" + x.getClass().getName() + ":" + x.getMessage();}
    }
    static void check(boolean v) {assertions++;if(!v)throw new AssertionError("check " + assertions);}
    private static volatile int consumed;
    private static long benchmark(Store store, Object query, boolean direct, int trials) {
        int result=0;
        long started=System.nanoTime();
        for(int i=0;i<trials;i++) {
            if(direct ? store.newContains(query) : store.oldView().contains(query)) result++;
        }
        consumed=result;
        return System.nanoTime()-started;
    }
    public static void main(String[] args) {
        int rounds = args.length == 0 ? 20000 : Integer.parseInt(args[0]);
        Random rng = new Random(0x534c4f54434f4e54L);
        Object[] choices = {null, MATCH, OTHER, "a", "b", 0, 1, 2, 3};
        for (int round=0;round<rounds;round++) {
            Store a = new Store(), b = new Store();
            int n = rng.nextInt(90);
            for(int i=0;i<n;i++) {Object x=choices[rng.nextInt(choices.length)];
                a.append(x,i%5==0);b.append(x,i%5==0);}
            Object q=choices[rng.nextInt(choices.length)];
            String x=outcome(a,true,q),y=outcome(b,false,q);
            check(x.equals(y));
        }
        for (int round=0;round<rounds;round++) {
            Store a=new Store(),b=new Store();
            for(int i=0;i<32;i++){a.append(OTHER,i==0);b.append(OTHER,i==0);}
            int operation=round%5;int when=round%17;
            AtomicInteger ac=new AtomicInteger(),bc=new AtomicInteger();
            final class Query {
                final Store owner;final AtomicInteger calls;
                Query(Store owner,AtomicInteger calls){this.owner=owner;this.calls=calls;}
                @Override public int hashCode() {return 1;}
                public boolean equals(Object x) {
                    int count=calls.getAndIncrement();
                    if(count==when){
                        switch(operation){
                            case 0 -> owner.append(OTHER,true);
                            case 1 -> owner.removeNext(owner.head,true);
                            case 2 -> owner.removeNext(owner.head,false);
                            case 3 -> owner.clear();
                            case 4 -> owner.append(MATCH,false);
                        }
                    }
                    return false;
                }
            }
            String x=outcome(a,true,new Query(a,ac)),y=outcome(b,false,new Query(b,bc));
            check(x.equals(y));check(ac.get()==bc.get());
        }
        System.out.println("PASS assertions="+assertions);
        if (args.length > 0) return;
        for (int n : new int[] {16, 256, 4096}) {
            Store store=new Store();
            for(int i=0;i<n;i++)store.append(i,i==0);
            Integer missing=Integer.valueOf(-123);
            int reps=Math.max(5000,4000000/n);
            for(int j=0;j<3;j++){benchmark(store,missing,false,reps);benchmark(store,missing,true,reps);}
            long[] old=new long[5],fast=new long[5];
            for(int j=0;j<5;j++){
                if((j&1)==0){old[j]=benchmark(store,missing,false,reps);fast[j]=benchmark(store,missing,true,reps);}
                else{fast[j]=benchmark(store,missing,true,reps);old[j]=benchmark(store,missing,false,reps);}
            }
            Arrays.sort(old);Arrays.sort(fast);
            System.out.printf("SIZE=%d OLD_NS=%.2f FAST_NS=%.2f RATIO=%.3f%n",n,old[2]/(double)reps,fast[2]/(double)reps,fast[2]/(double)old[2]);
        }
    }
}
