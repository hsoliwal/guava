// SPDX-License-Identifier: Apache-2.0
package com.m3.collections.guava.collect;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Deterministic oracle: complete original Guava queue versus package-only relocation. */
public final class M3GuavaEvictingRelocationProof {
    private static long checks;
    private M3GuavaEvictingRelocationProof() { }
    private static void equal(Object expected,Object actual,String why) {
        checks++;
        if (!Objects.equals(expected,actual))
            throw new AssertionError(why+" "+expected+" != "+actual);
    }
    private static void equalArray(Object[] expected,Object[] actual) {
        checks++;
        if (!Arrays.equals(expected,actual))throw new AssertionError("queue order");
    }
    private static Object serial(Object obj) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ObjectOutputStream out=new ObjectOutputStream(bytes)){out.writeObject(obj);}
        try(ObjectInputStream in=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }
    private static void verify(com.google.common.collect.EvictingQueue<Integer> a,
            EvictingQueue<Integer> b,int capacity) {
        equal(a.size(),b.size(),"size");
        equal(a.peek(),b.peek(),"peek");
        equal(a.remainingCapacity(),b.remainingCapacity(),"remaining");
        equal(capacity-a.size(),b.remainingCapacity(),"bound");
        equalArray(a.toArray(),b.toArray());
    }
    private static void removeAt(com.google.common.collect.EvictingQueue<Integer> a,
            EvictingQueue<Integer> b,int index) {
        Iterator<Integer> x=a.iterator(),y=b.iterator();
        for(int i=0;i<=index;i++)equal(x.next(),y.next(),"iterator");
        x.remove();y.remove();
    }
    private static void run() throws Exception {
        Random rng=new Random(0x4d33475541564145L);
        for(int capacity:new int[]{0,1,2,3,7,31,64}) {
            var a=com.google.common.collect.EvictingQueue.<Integer>create(capacity);
            var b=EvictingQueue.<Integer>create(capacity);
            for(int step=0;step<500;step++) {
                switch(rng.nextInt(8)) {
                    case 0,1,2 -> {
                        int v=rng.nextInt(121)-60;
                        equal(a.add(v),b.add(v),"add");
                    }
                    case 3 -> equal(a.poll(),b.poll(),"poll");
                    case 4 -> {
                        List<Integer> values=new ArrayList<>();
                        for(int n=rng.nextInt(10);n>0;n--)values.add(rng.nextInt(9));
                        equal(a.addAll(values),b.addAll(values),"addAll");
                    }
                    case 5 -> {
                        if(!a.isEmpty())removeAt(a,b,rng.nextInt(a.size()));
                    }
                    case 6 -> {
                        if(rng.nextInt(7)==0){a.clear();b.clear();}
                    }
                    default -> {
                        int v=rng.nextInt();
                        equal(a.offer(v),b.offer(v),"offer");
                    }
                }
                verify(a,b,capacity);
                if(step%137==0)equalArray(
                        ((com.google.common.collect.EvictingQueue<?>)serial(a)).toArray(),
                        ((EvictingQueue<?>)serial(b)).toArray());
            }
        }
        boolean original=false,relocated=false;
        try{com.google.common.collect.EvictingQueue.create(-1);}catch(IllegalArgumentException ex){original=true;}
        try{EvictingQueue.create(-1);}catch(IllegalArgumentException ex){relocated=true;}
        equal(true,original,"invalid capacity");equal(original,relocated,"capacity exception");
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=0)throw new IllegalArgumentException("no arguments");
        run();
        System.out.println("PASS M3_GUAVA_EVICTING_RELOCATION checks="+checks);
    }
}
