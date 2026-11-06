package com.m3.collections.guava.collect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Differential check of the array HashBiMap's hash-flooding fallback: one random operation stream runs on
 * (A) a plain HashBiMap, (B) a HashBiMap forced into FloodedTables up front, and (C) a HashBiMap whose keys and
 * values all share one hash code (so it converts itself). After every step the three must agree on outcome
 * (return value or exception class), entry order and inverse order.
 */
public final class HbmFloodDiff {
  /** Equal by value, every instance hashes to 7: forces long buckets. */
  static final class C {
    final int v;
    C(int v) { this.v = v; }
    @Override public boolean equals(Object o) { return o instanceof C && ((C) o).v == v; }
    @Override public int hashCode() { return 7; }
    @Override public String toString() { return "C" + v; }
  }

  static Object unwrap(Object o) { return o instanceof C ? ((C) o).v : o; }

  static List<Object> order(Map<?, ?> m) {
    List<Object> out = new ArrayList<>();
    for (Map.Entry<?, ?> e : m.entrySet()) { out.add(unwrap(e.getKey())); out.add(unwrap(e.getValue())); }
    return out;
  }

  interface Op { Object run(HashBiMap<Object, Object> m, boolean wrap); }

  static Object w(int x, boolean wrap) { return wrap ? new C(x) : x; }

  static String outcome(Op op, HashBiMap<Object, Object> m, boolean wrap) {
    try { return "R:" + unwrap(op.run(m, wrap)); } catch (RuntimeException e) { return "E:" + e.getClass().getSimpleName(); }
  }

  public static void main(String[] args) {
    long seeds = args.length > 0 ? Long.parseLong(args[0]) : 200;
    int steps = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
    long checks = 0;
    boolean cConverted = false;
    for (long seed = 1; seed <= seeds; seed++) {
      Random r = new Random(seed);
      HashBiMap<Object, Object> a = HashBiMap.create();
      HashBiMap<Object, Object> b = HashBiMap.create();
      b.convertToHashFloodingResistantImplementation();
      HashBiMap<Object, Object> c = HashBiMap.create(r.nextInt(4));
      for (int s = 0; s < steps; s++) {
        int k = r.nextInt(24), v = 100 + r.nextInt(24), kind = r.nextInt(12);
        int pos = r.nextInt(30);
        boolean doClear = r.nextInt(40) == 0;
        Op op = (m, wrap) -> {
          switch (kind) {
            case 0: case 1: return m.put(w(k, wrap), w(v, wrap));
            case 2: return m.forcePut(w(k, wrap), w(v, wrap));
            case 3: return m.remove(w(k, wrap));
            case 4: return m.inverse().put(w(v, wrap), w(k, wrap));
            case 5: return m.inverse().forcePut(w(v, wrap), w(k, wrap));
            case 6: return m.inverse().remove(w(v, wrap));
            case 7: { // entry.setValue at position pos
              Iterator<Map.Entry<Object, Object>> it = m.entrySet().iterator();
              for (int i = 0; i < pos && it.hasNext(); i++) it.next();
              return it.hasNext() ? it.next().setValue(w(v, wrap)) : "none";
            }
            case 8: { // inverse entry.setValue at position pos
              Iterator<Map.Entry<Object, Object>> it = m.inverse().entrySet().iterator();
              for (int i = 0; i < pos && it.hasNext(); i++) it.next();
              return it.hasNext() ? it.next().setValue(w(k, wrap)) : "none";
            }
            case 9: { // iterator remove at position pos
              Iterator<Object> it = m.keySet().iterator();
              for (int i = 0; i < pos && it.hasNext(); i++) it.next();
              if (!it.hasNext()) return "none";
              Object gone = it.next();
              it.remove();
              return gone;
            }
            case 10: return unwrap(m.get(w(k, wrap))) + "/" + unwrap(m.inverse().get(w(v, wrap))) + "/" + m.containsKey(w(k, wrap)) + m.containsValue(w(v, wrap));
            default: if (doClear) { m.clear(); return "cleared"; } return m.size();
          }
        };
        String oa = outcome(op, a, false), ob = outcome(op, b, false), oc = outcome(op, c, true);
        cConverted |= c.isUsingHashFloodingResistance();
        if (!oa.equals(ob) || !oa.equals(oc) || !order(a).equals(order(b)) || !order(a).equals(order(c))
            || !order(a.inverse()).equals(order(b.inverse())) || !order(a.inverse()).equals(order(c.inverse()))
            || a.size() != b.size() || a.size() != c.size() || !b.isUsingHashFloodingResistance()) {
          throw new AssertionError("seed=" + seed + " step=" + s + " kind=" + kind + " k=" + k + " v=" + v + " pos=" + pos
              + "\n A " + oa + " " + order(a) + "\n B " + ob + " " + order(b) + "\n C " + oc + " " + order(c)
              + " flooded=" + c.isUsingHashFloodingResistance());
        }
        checks++;
      }
    }
    if (!cConverted) throw new AssertionError("colliding map never converted to FloodedTables");
    HashBiMap<Object, Object> t = HashBiMap.create();
    for (int i = 0; i < 20; i++) t.put(i, "orig-" + i);
    if (t.isUsingHashFloodingResistance()) throw new AssertionError("converted without collisions");
    for (int i = 0; i < 20; i++) t.put(i, new C(i));
    if (!t.isUsingHashFloodingResistance()) throw new AssertionError("setValue-style replacement did not convert");
    System.out.println("HBM_FLOOD_DIFF PASS checks=" + checks + " seeds=" + seeds + " steps=" + steps);
  }
}
