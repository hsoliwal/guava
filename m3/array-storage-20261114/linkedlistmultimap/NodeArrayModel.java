/*
 * Copyright 2026 Hitesh Soliwal and contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import java.util.*;
import java.io.*;

/** Model of the same primitive slot links used in the Guava receiver candidate. */
public final class NodeArrayModel<K,V> implements Serializable {
    private static final int NONE = -1;
    private transient Object[] keys, values, entryHandles;
    private transient int[] next, previous, nextSibling, previousSibling, freeNext;
    private transient int used, free, head, tail, size, modCount;
    private transient Map<K, KeyList> byKey;
    private static final class KeyList { int head,tail,count; KeyList(int s) { head=tail=s;count=1; } }
    public NodeArrayModel(){ init(); }
    private void init(){keys=new Object[0];values=new Object[0];entryHandles=null;next=previous=nextSibling=previousSibling=freeNext=new int[0];used=0;free=head=tail=NONE;byKey=new HashMap<>();size=modCount=0;}
    private void grow(){int n=keys.length==0?8:keys.length+(keys.length>>>1)+1;
       Object[] a=Arrays.copyOf(keys,n),b=Arrays.copyOf(values,n),e=entryHandles==null?null:Arrays.copyOf(entryHandles,n);
       int[] c=Arrays.copyOf(next,n),d=Arrays.copyOf(previous,n),f=Arrays.copyOf(nextSibling,n),g=Arrays.copyOf(previousSibling,n),h=Arrays.copyOf(freeNext,n);
       keys=a;values=b;entryHandles=e;next=c;previous=d;nextSibling=f;previousSibling=g;freeNext=h;
    }
    private int fresh(K k,V v){int i;if(free!=NONE){i=free;free=freeNext[i];}else {if(used==keys.length)grow();i=used++;}keys[i]=k;values[i]=v;next[i]=previous[i]=nextSibling[i]=previousSibling[i]=NONE;return i;}
    @SuppressWarnings("unchecked") private K key(int i){return (K)keys[i];}
    @SuppressWarnings("unchecked") private V value(int i){return (V)values[i];}
    @SuppressWarnings("unchecked") private EntryHandle entry(int i){if(entryHandles==null)entryHandles=new Object[keys.length];EntryHandle h=(EntryHandle)entryHandles[i];if(h==null){h=new EntryHandle(i);entryHandles[i]=h;}return h;}
    public final class EntryHandle implements Map.Entry<K,V> {
       private final K storedKey;private V detached;private int slot;
       EntryHandle(int i){storedKey=key(i);detached=value(i);slot=i;}
       @Override public K getKey(){return storedKey;}
       @Override public V getValue(){return slot==NONE?detached:value(slot);}
       @Override public V setValue(V v){V old=getValue();if(slot==NONE)detached=v;else values[slot]=v;return old;}
       private void detach(){detached=getValue();slot=NONE;}
    }
    public int add(K k,V v,int nextSib){int slot=fresh(k,v);
        if(head==NONE){head=tail=slot;byKey.put(k,new KeyList(slot));modCount++;}
        else if(nextSib==NONE){next[tail]=slot;previous[slot]=tail;tail=slot;KeyList kl=byKey.get(k);
           if(kl==null){byKey.put(k,new KeyList(slot));modCount++;}
           else{kl.count++;nextSibling[kl.tail]=slot;previousSibling[slot]=kl.tail;kl.tail=slot;}}
        else{KeyList kl=Objects.requireNonNull(byKey.get(k));kl.count++;previous[slot]=previous[nextSib];
           previousSibling[slot]=previousSibling[nextSib];next[slot]=nextSib;nextSibling[slot]=nextSib;
           if(previousSibling[nextSib]==NONE)kl.head=slot;else nextSibling[previousSibling[nextSib]]=slot;
           if(previous[nextSib]==NONE)head=slot;else next[previous[nextSib]]=slot;
           previous[nextSib]=slot;previousSibling[nextSib]=slot;}
        size++;return slot;
    }
    public void remove(int slot){int p=previous[slot],n=next[slot],ps=previousSibling[slot],ns=nextSibling[slot];K k=key(slot);
       if(p==NONE)head=n;else next[p]=n;
       if(n==NONE)tail=p;else previous[n]=p;
       if(ps==NONE&&ns==NONE){KeyList kl=Objects.requireNonNull(byKey.remove(k));kl.count=0;modCount++;}
       else {KeyList kl=Objects.requireNonNull(byKey.get(k));kl.count--;
          if(ps==NONE)kl.head=ns;else nextSibling[ps]=ns;
          if(ns==NONE)kl.tail=ps;else previousSibling[ns]=ps;}
       if(entryHandles!=null && entryHandles[slot]!=null)entry(slot).detach();
       if(entryHandles!=null)entryHandles[slot]=null;keys[slot]=values[slot]=null;freeNext[slot]=free;free=slot;size--;
    }
    public V put(K k,V v){add(k,v,NONE);return v;}
    public List<V> values(K k){KeyList kl=byKey.get(k);List<V> out=new ArrayList<>();int i=kl==null?NONE:kl.head;while(i!=NONE){out.add(value(i));i=nextSibling[i];}return out;}
    public List<Map.Entry<K,V>> entries(){List<Map.Entry<K,V>> out=new ArrayList<>();for(int i=head;i!=NONE;i=next[i])out.add(entry(i));return out;}
    public V removeOne(K k,int at){KeyList kl=byKey.get(k);int i=kl.head;for(int j=0;j<at;j++)i=nextSibling[i];V old=value(i);remove(i);return old;}
    public void insert(K k,V v,int at){KeyList kl=byKey.get(k);int i=kl==null?NONE:kl.head;for(int j=0;j<at;j++)i=nextSibling[i];add(k,v,i);}
    public void clear(){for(int i=head;i!=NONE;i=next[i])if(entryHandles!=null&&entryHandles[i]!=null)entry(i).detach();init();}
    public boolean hasEntryBoundaryStorage(){return entryHandles!=null;}
    public int size(){return size;}
    public int groups(){return byKey.size();}
    public List<K> keySequence(){List<K> out=new ArrayList<>();for(int i=head;i!=NONE;i=next[i])out.add(key(i));return out;}
    public static void main(String[] a){
      NodeArrayModel<Integer,String> escapedModel=new NodeArrayModel<>();
      escapedModel.add(7,"before",NONE);
      Map.Entry<Integer,String> escapedEntry=escapedModel.entries().get(0);
      if (!escapedEntry.getValue().equals("before"))throw new Error("entry initial");
      escapedEntry.setValue("updated");
      if (!escapedModel.values(7).equals(List.of("updated")))throw new Error("entry live");
      escapedModel.removeOne(7,0);
      if (!escapedEntry.getValue().equals("updated"))throw new Error("entry detached");
      escapedModel.add(7,"recycled",NONE);
      escapedEntry.setValue("orphan");
      if (!escapedEntry.getValue().equals("orphan") || !escapedModel.values(7).equals(List.of("recycled")))throw new Error("entry reuse");
      Map.Entry<Integer,String> secondEntry=escapedModel.entries().get(0);
      escapedModel.clear();
      if (!secondEntry.getValue().equals("recycled"))throw new Error("entry cleared");
      NodeArrayModel<Integer,String> laziness=new NodeArrayModel<>();for(int i=0;i<20000;i++)laziness.put(i%23,"value");if(laziness.hasEntryBoundaryStorage())throw new AssertionError("eager entries");laziness.entries();if(!laziness.hasEntryBoundaryStorage())throw new AssertionError("missing boundary on demand");laziness.clear();if(laziness.hasEntryBoundaryStorage())throw new AssertionError("boundary retained after clear");
      Random r=new Random(987654321);long checks=0;int seeds=a.length>0?Integer.parseInt(a[0]):20;int steps=a.length>1?Integer.parseInt(a[1]):20000;for(int seed=0;seed<seeds;seed++){
      NodeArrayModel<Integer,Integer> obj=new NodeArrayModel<>();LinkedHashMap<Integer,List<Integer>> ref=new LinkedHashMap<>();List<Map.Entry<Integer,Integer>> escaped=new ArrayList<>();List<Map.Entry<Integer,Integer>> global=new ArrayList<>();
      for(int step=0;step<steps;step++){
         int k=r.nextInt(30),v=r.nextInt(100),op=r.nextInt(9);List<Integer> list=ref.get(k);
         if(op<=2){obj.add(k,v,NONE);ref.computeIfAbsent(k,z->new ArrayList<>()).add(v);global.add(Map.entry(k,v));}
         else if(op==3){int at=list==null?0:r.nextInt(list.size()+1);obj.insert(k,v,at);ref.computeIfAbsent(k,z->new ArrayList<>()).add(at,v);int j=0,where=global.size();if(list!=null)for(int m=0;m<global.size();m++){if(Objects.equals(global.get(m).getKey(),k)&&j++==at){where=m;break;}}global.add(where,Map.entry(k,v));}
         else if(op==4&&list!=null){int at=r.nextInt(list.size());Integer old=list.remove(at);if(list.isEmpty())ref.remove(k);if(!Objects.equals(old,obj.removeOne(k,at)))throw new Error("removed");int j=0;for(int m=0;m<global.size();m++)if(Objects.equals(global.get(m).getKey(),k)&&j++==at){global.remove(m);break;}}
         else if(op==5&&obj.size()>0){escaped.add(obj.entries().get(r.nextInt(obj.size())));}
         else if(op==6){obj.clear();ref.clear();global.clear();}
         else if(op==7&&obj.size()>0){Map.Entry<Integer,Integer> e=obj.entries().get(r.nextInt(obj.size()));Vunused(e);}
         if(ref.values().stream().mapToInt(List::size).sum()!=obj.size())throw new Error("size");checks++;
         if(ref.size()!=obj.groups())throw new Error("groups");checks++;
         for(int q=0;q<30;q++){if(!obj.values(q).equals(ref.getOrDefault(q,List.of())))throw new Error("values "+q+" step="+step);checks++;}
         if(step%11==0){List<Integer> keys=new ArrayList<>();for(var e:global)keys.add(e.getKey());
            if(!obj.keySequence().equals(keys))throw new Error("global "+step);checks++;}
      }
    }System.out.println("PASS "+checks);}
    private static <K,V> void Vunused(Map.Entry<K,V> e){e.getValue();}
}
