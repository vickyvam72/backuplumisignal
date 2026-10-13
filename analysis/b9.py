import pandas as pd, numpy as np, json
from sim2 import prep, simulate, stats
exec(open('b8.py').read().split("ranks = {")[0])
exits = {'5/4/2':(.05,.04,2),'6/5/3':(.06,.05,3),'8/5/3':(.08,.05,3),'8/5/5':(.08,.05,5),'10/6/5':(.10,.06,5),'12/7/7':(.12,.07,7)}
for sid,(nm,m,mode) in S.items():
    print(f"S{sid} {nm} ({mode})")
    for mo in ([mode] + ([] if mode=='pre0' else ['pre0'])):
        for en,(tp,sl,h) in exits.items():
            r=simulate(df,F,m,mo,tp,sl,h); rp=simulate(df,F,m,mo,tp,sl,h,path='pess')
            a,b,c,d=stats(r,D,TRAIN),stats(r,D,TEST),stats(rp,D,TRAIN),stats(rp,D,TEST)
            print(f"   {mo} {en:7} TRAIN n{a['n']:5} win{a['win']:5} avg{a['avg']:+.2f} pess{c['avg']:+.2f} | TEST n{b['n']:4} win{b['win']:5} avg{b['avg']:+.2f} pess{d['avg']:+.2f}")
