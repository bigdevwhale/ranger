"""
Builds the demo's year of patrols around Letná, Prague: real loops along real paths.

Inputs (cached in tools/cache): an Overpass dump of walkable ways, Open-Meteo elevations on a grid,
and the OpenFreeMap z14 tiles that cover the area. Outputs go to app/src/demo/assets/demo:
  routes.txt   one encoded polyline per line (Google's algorithm, 1e5)
  elev.json    {"lat0","lon0","dlat","dlon","n","m","z":[...]} elevation grid in metres
  tiles/       14_x_y.mvt, copied into the app's tile cache by the demo seed

Run:  python tools/demo_routes.py
"""
import heapq
import json
import math
import os
import random
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", "cache")
OUT = os.path.join(ROOT, "app", "src", "demo", "assets", "demo")
BOX = (50.075, 14.38, 50.13, 14.47)  # s, w, n, e
HOME = (50.1003, 14.4262)
AGENT = "Ranger-demo/1.0 (github.com/bigdevwhale/ranger)"


def dist(a, b):
    la1, lo1 = map(math.radians, a)
    la2, lo2 = map(math.radians, b)
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def encode(pts):
    out = []
    plat = plon = 0
    for la, lo in pts:
        a, b = round(la * 1e5), round(lo * 1e5)
        for v in (a - plat, b - plon):
            v = ~(v << 1) if v < 0 else v << 1
            while v >= 0x20:
                out.append(chr((0x20 | (v & 0x1F)) + 63))
                v >>= 5
            out.append(chr(v + 63))
        plat, plon = a, b
    return "".join(out)


def graph():
    data = json.load(open(os.path.join(CACHE, "prague.json"), encoding="utf-8"))
    nodes = {e["id"]: (e["lat"], e["lon"]) for e in data["elements"] if e["type"] == "node"}
    adj = {}
    for e in data["elements"]:
        if e["type"] != "way":
            continue
        ns = [n for n in e["nodes"] if n in nodes]
        for a, b in zip(ns, ns[1:]):
            d = dist(nodes[a], nodes[b])
            adj.setdefault(a, []).append((b, d))
            adj.setdefault(b, []).append((a, d))
    # the biggest connected piece only
    seen, best = set(), set()
    for s in adj:
        if s in seen:
            continue
        comp, stack = {s}, [s]
        seen.add(s)
        while stack:
            u = stack.pop()
            for v, _ in adj[u]:
                if v not in seen:
                    seen.add(v); comp.add(v); stack.append(v)
        if len(comp) > len(best):
            best = comp
    adj = {k: [(v, d) for v, d in vs if v in best] for k, vs in adj.items() if k in best}
    return nodes, adj


def route(adj, a, b, noise, rnd):
    """Dijkstra with each edge's length scaled by a per-route random factor, so loops vary."""
    q = [(0.0, a)]
    best = {a: 0.0}
    prev = {}
    while q:
        d, u = heapq.heappop(q)
        if u == b:
            break
        if d > best.get(u, 1e18):
            continue
        for v, w in adj[u]:
            k = noise.setdefault((min(u, v), max(u, v)), 1 + rnd.random() * 1.5)
            nd = d + w * k
            if nd < best.get(v, 1e18):
                best[v] = nd; prev[v] = u
                heapq.heappush(q, (nd, v))
    if b not in prev and a != b:
        return None
    path = [b]
    while path[-1] != a:
        path.append(prev[path[-1]])
    return path[::-1]


def loops(nodes, adj, n, rnd):
    keys = list(adj)
    home = min(keys, key=lambda k: dist(nodes[k], HOME))
    out = []
    targets = []
    for i in range(n):
        r = rnd.random()
        targets.append(rnd.uniform(4.5, 7.5) if r < .55 else rnd.uniform(7.5, 11) if r < .82 else rnd.uniform(12, 18) if r < .96 else rnd.uniform(21, 31))
    for km in targets:
        for _ in range(40):
            noise = {}
            k = 2 if km < 12 else 3
            # waypoints roughly on a circle of the right size
            ang = rnd.random() * 2 * math.pi
            rad = km * 1000 / (2 * math.pi) * 1.25
            wps = []
            for j in range(k):
                a = ang + j * 2 * math.pi / (k + 1) + rnd.uniform(-.3, .3)
                p = (HOME[0] + rad * math.cos(a) / 111320, HOME[1] + rad * math.sin(a) / (111320 * math.cos(math.radians(HOME[0]))))
                p = (min(max(p[0], BOX[0] + .002), BOX[2] - .002), min(max(p[1], BOX[1] + .002), BOX[3] - .002))
                cand = rnd.sample(keys, 400)
                wps.append(min(cand, key=lambda c: dist(nodes[c], p)))
            stops = [home] + wps + [home]
            path = []
            ok = True
            for a, b in zip(stops, stops[1:]):
                seg = route(adj, a, b, noise, rnd)
                if seg is None:
                    ok = False; break
                path += seg if not path else seg[1:]
            if not ok:
                continue
            pts = [nodes[x] for x in path]
            length = sum(dist(p, q) for p, q in zip(pts, pts[1:])) / 1000
            if abs(length - km) / km < .18:
                out.append(pts)
                break
    return out


def elevation():
    path = os.path.join(CACHE, "elev.json")
    if os.path.exists(path):
        return json.load(open(path))
    n = m = 24
    s, w, nn, e = BOX
    dlat = (nn - s) / (n - 1); dlon = (e - w) / (m - 1)
    pts = [(s + i * dlat, w + j * dlon) for i in range(n) for j in range(m)]
    z = []
    for k in range(0, len(pts), 100):
        chunk = pts[k:k + 100]
        url = "https://api.open-meteo.com/v1/elevation?latitude=" + ",".join(f"{p[0]:.5f}" for p in chunk) + "&longitude=" + ",".join(f"{p[1]:.5f}" for p in chunk)
        req = urllib.request.Request(url, headers={"User-Agent": AGENT})
        z += json.load(urllib.request.urlopen(req, timeout=60))["elevation"]
        time.sleep(1)
    g = {"lat0": s, "lon0": w, "dlat": dlat, "dlon": dlon, "n": n, "m": m, "z": z}
    json.dump(g, open(path, "w"))
    return g


def tiles():
    z = 14
    def tx(lon): return int((lon + 180) / 360 * (1 << z))
    def ty(lat):
        r = math.radians(lat)
        return int((1 - math.log(math.tan(r) + 1 / math.cos(r)) / math.pi) / 2 * (1 << z))
    os.makedirs(os.path.join(OUT, "tiles"), exist_ok=True)
    tj = json.load(urllib.request.urlopen(urllib.request.Request("https://tiles.openfreemap.org/planet", headers={"User-Agent": AGENT}), timeout=60))
    tpl = tj["tiles"][0]
    import gzip
    for x in range(tx(BOX[1]), tx(BOX[3]) + 1):
        for y in range(ty(BOX[2]), ty(BOX[0]) + 1):
            f = os.path.join(OUT, "tiles", f"{z}_{x}_{y}.mvt")
            if os.path.exists(f):
                continue
            raw = urllib.request.urlopen(urllib.request.Request(tpl.replace("{z}", str(z)).replace("{x}", str(x)).replace("{y}", str(y)), headers={"User-Agent": AGENT}), timeout=60).read()
            if raw[:2] == b"\x1f\x8b":
                raw = gzip.decompress(raw)
            open(f, "wb").write(raw)
            time.sleep(.2)


def main():
    os.makedirs(OUT, exist_ok=True)
    rnd = random.Random(42)
    nodes, adj = graph()
    print("graph", len(adj), "nodes")
    rs = loops(nodes, adj, 230, rnd)
    print("routes", len(rs))
    with open(os.path.join(OUT, "routes.txt"), "w") as f:
        for r in rs:
            f.write(encode(r) + "\n")
    json.dump(elevation(), open(os.path.join(OUT, "elev.json"), "w"))
    tiles()
    print("tiles", len(os.listdir(os.path.join(OUT, "tiles"))))


if __name__ == "__main__":
    main()
