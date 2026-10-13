from engine import *
import json
b = df['mkt_breadth_ma5'].to_numpy(); r5 = df['mkt_ew_ret5'].to_numpy()
REG = {'ret': r5 > -.03, 'br30': b >= .30, 'br50': b >= .50, 'weak': b < .30}
S = [(1,'rsi<35 & udvr2 & p<200','ret','udvr','atr2.5/2'),(2,'up3 & rsi<35 & p<200','ret','rsi_low','atr2/1.5'),
 (3,'upstreak3 & pos60_lo & inside','br30','lowpos','low10/res'),(4,'squeeze & upstreak3 & pos60_lo','ret','volratio','low5/3atr'),
 (5,'squeeze & near_ma20 & p<200','br50','volratio','low5/res'),(6,'lwick & gapdown_today','weak','volratio','atr2.5/2'),
 (7,'up3 & pos60_lo & reversal','ret','volratio','low5/3atr'),(8,'downtrend & rsi<35 & engulf','ret','value','low5/3atr'),
 (9,'squeeze & pos60_lo & hl_rise','ret','lowpos','low10/res'),(10,'lwick & pos60_lo & nr7','ret','value','low5/3atr')]
W = {'semua':1,'250jt':1,'1M':1,'5M':1,'20M':.5}
masks = {}
out={}
for sid,c,rg,rk0,ex0 in S:
    best=None
    for ex in EXITS:
        if ex=='FIX': continue
        for rk in ['volratio','lowpos','value','rsi_low','udvr']:
            sc=0; detail={}
            for t,w in W.items():
                m = mask_of(c,t).to_numpy() & REG[rg]
                tr,_=run(m,rk,ex); trp,_=run(m,rk,ex,path='pess')
                if len(tr)<10: continue
                s=summarize(tr); p=summarize(trp)
                sc += w*p['total']/max(40,-s['maxdd']); detail[t]=(s['n'],s['total'],s['maxdd'],s['worst_month'])
            if best is None or sc>best[0]: best=(sc,ex,rk,detail)
    sc,ex,rk,detail=best
    print(f"S{sid} {c:34} {rg:5} lama:{rk0}/{ex0} -> baru:{rk}/{ex} score{sc:.1f} | " + ' '.join(f"{t}:n{v[0]} {v[1]:+.0f} dd{v[2]:.0f}" for t,v in detail.items()))
    out[sid]=dict(cond=c,reg=rg,rank=rk,exit=ex)
json.dump(out,open('t4_cal.json','w'),indent=1)
