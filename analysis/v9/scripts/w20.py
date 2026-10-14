import sys; sys.argv=['x','profit']
src=open('sel.py').read().split("if JENIS == 'profit':")[0]
exec(src)
o=pd.read_pickle('p1t.pkl'); conds=sorted(set(o.cond))
EX=['atr2/1.5','atr3/1.5','atr2.5/2','low5/res','low10/res','low5/3atr']; RK=['volratio','rsi_low','value','lowpos']
rows=[]
for c in conds:
  for reg in ['weak30','weak20']:
    for ex in EX:
      for rk in RK:
        s=dict(cond=c,reg=reg,exit=ex,rank=rk)
        try: tr=trades_of(s,'20M')
        except Exception as e: continue
        if len(tr)<40: continue
        h=health(s,'20M')
        st=summarize(tr)
        rows.append(dict(cond=c,reg=reg,exit=ex,rank=rk,n=st['n'],tot=st['total'],ok=h is not None, u60=tr[tr.date.isin(TE)].pnl.sum()))
d=pd.DataFrame(rows); d.to_pickle('w20.pkl')
print(d[d.ok].sort_values('tot',ascending=False).head(20).to_string())
print(len(d), d.ok.sum())
