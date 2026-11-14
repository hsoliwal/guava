/*
 * Copyright 2026 Hitesh Soliwal and contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Independent model of the lazily paged 64-slot mutable-entry boundary cache. */
public final class PagedEntryArrayProof {
    private static final int SHIFT = 6;
    private static final int SIZE = 1 << SHIFT;
    private static final int MASK = SIZE - 1;
    private static long checks;

    private static final class Owner {
        Object[] values = new Object[8];
        Object[][] cache;
        int used;
        private final class Handle implements Map.Entry<Integer, Object> {
            final Integer key;
            Object detached;
            final int slot;
            boolean live = true;
            Handle(int slot) {this.slot=slot;key=slot;detached=values[slot];}
            public Integer getKey() {return key;}
            public Object getValue() {return live ? values[slot] : detached;}
            public Object setValue(Object v) {
                Object old=getValue();
                if(live)values[slot]=v;
                else detached=v;
                return old;
            }
            void detach() {detached=getValue();live=false;}
        }
        private static int pageCount(int capacity) {
            return (capacity >>> SHIFT) + ((capacity & MASK)==0 ? 0 : 1);
        }
        void append(Object v) {
            if(used==values.length)grow();
            values[used++]=v;
        }
        void grow() {
            int capacity=values.length+(values.length>>>1)+1;
            values=Arrays.copyOf(values,capacity);
            if(cache!=null)cache=Arrays.copyOf(cache,pageCount(capacity));
        }
        Handle at(int slot) {
            if(cache==null)cache=new Object[pageCount(values.length)][];
            int page=slot>>>SHIFT;
            if(cache[page]==null)cache[page]=new Object[SIZE];
            Handle h=(Handle)cache[page][slot & MASK];
            if(h==null){h=new Handle(slot);cache[page][slot&MASK]=h;}
            return h;
        }
        Handle cached(int slot) {
            if(cache==null)return null;
            Object[] page=cache[slot>>>SHIFT];
            return page==null?null:(Handle)page[slot & MASK];
        }
        void replace(int slot,Object v) {
            values[slot]=v;
            Handle h=cached(slot);
            if(h!=null)h.detached=v;
        }
        void remove(int slot) {
            Handle h=cached(slot);
            if(h!=null){h.detach();cache[slot>>>SHIFT][slot&MASK]=null;}
            values[slot]=null;
        }
        void clear() {
            if(cache!=null){
                for(int slot=0;slot<used;slot++){
                    Handle h=cached(slot);
                    if(h!=null)h.detach();
                }
            }
            cache=null;
            Arrays.fill(values,null);
            used=0;
        }
        int materializedPages() {
            int count=0;
            if(cache!=null)for(Object[] page:cache)if(page!=null)count++;
            return count;
        }
        int retainedReferenceSlots() {
            if(cache==null)return 0;
            return cache.length + SIZE*materializedPages();
        }
    }
    private static void check(boolean condition) {
        checks++;if(!condition)throw new AssertionError("check="+checks);
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Owner owner=new Owner();
        for(int i=0;i<12000;i++)owner.append(i);
        check(owner.cache==null);
        Owner.Handle first=owner.at(0);
        check(owner.materializedPages()==1);
        check(owner.retainedReferenceSlots() < owner.values.length / 8);
        check(owner.at(0)==first);
        for(int i=12000;i<24000;i++)owner.append(i);
        check(owner.at(0)==first);
        Owner.Handle last=owner.at(23999);
        check(owner.materializedPages()==2);
        check(last.getValue().equals(23999));
        owner.replace(0,-1);
        check(first.getValue().equals(-1));
        owner.remove(0);
        check(first.getValue().equals(-1));
        owner.replace(0,99);
        check(first.setValue(-2).equals(-1));
        check(owner.values[0].equals(99));
        owner.clear();
        check(owner.cache==null);
        check(last.getValue().equals(23999));
        Random random=new Random(0x5041474544454e54L);
        for(int round=0;round<100;round++){
            Owner current=new Owner();
            Map<Integer,Owner.Handle> escaped=new HashMap<>();
            for(int i=0;i<2000;i++){
                int act=random.nextInt(6);
                if((act<2||current.used==0)&&current.used<800){
                    current.append(random.nextInt());
                } else if(act==2&&current.used>0){
                    int slot=random.nextInt(current.used);
                    Owner.Handle h=current.at(slot);
                    escaped.put(slot,h);
                    check(h==current.at(slot));
                    check(h.getValue()==current.values[slot]);
                } else if(act==3&&current.used>0){
                    int slot=random.nextInt(current.used);
                    Owner.Handle h=escaped.get(slot);
                    Object value=random.nextInt();
                    current.replace(slot,value);
                    if(h!=null&&h.live)check(h.getValue().equals(value));
                } else if(act==4&&current.used>0){
                    int slot=random.nextInt(current.used);
                    Owner.Handle h=escaped.remove(slot);
                    Object value=current.values[slot];
                    current.remove(slot);
                    if(h!=null){
                        check(h.getValue()==value);
                        h.setValue("detached");
                        check(current.values[slot]==null);
                    }
                } else if(act==5){
                    current.clear();escaped.clear();
                    check(current.cache==null);
                }
            }
        }
        System.out.println("PASS assertions="+checks);
        System.out.println("one page cache: 12000 mappings, 1 boundary page of 64 refs plus page index; no full-size cache");
    }
}
