import sys
sys.argv=['x','/tmp/t5_unused/']
src=open('final2.py').read().split("ALL_T, ALL_C, SUMM")[0]
exec(src)
def combined(tier, method):
    masks={s['id']: mask_of(s['cond'], tier).to_numpy() & REG[s['reg']] for s in STR}
    cand=[]
    for s in STR:
        rk=np.asarray(RANKS[s['rank']],dtype=float); idx=np.where(masks[s['id']]&np.isfinite(rk))[0]
        tmp=pd.DataFrame({'i':idx,'d':di[idx],'sid':s['id'],'r':rk[idx]})
        tmp['pct']=tmp.groupby('d')['r'].rank(pct=True); tmp['pos']=tmp.groupby('d')['r'].rank(ascending=False,method='first'); cand.append(tmp)
    cand=pd.concat(cand).sort_values('d')
    multi=cand.groupby(['d','i']).size().rename('nstrat'); cand=cand.join(multi,on=['d','i'])
    sexit={s['id']:s['exit'] for s in STR}; hist={s['id']:[] for s in STR}; held={}; picked=[]
    for d,day in cand.groupby('d',sort=True):
        q={}
        for sid,h in hist.items():
            done=[p for (xd,p) in h if xd<d]
            if method=='last30': done=done[-30:]
            q[sid]=np.mean(done) if len(done)>=(5 if method=='last30' else 10) else 0.0
        day=day.assign(q=day.sid.map(q))
        if method in ('last30','expanding'): day=day.sort_values(['q','pct'],ascending=[False,False])
        elif method=='roundrobin': day=day.sort_values(['pos','q'],ascending=[True,False])
        elif method=='multi': day=day.sort_values(['nstrat','q','pct'],ascending=[False,False,False])
        elif method=='shrunk':
            n={sid:len([1 for (xd,p) in hist[sid] if xd<d]) for sid in hist}
            day=day.assign(qs=[q[s]*n[s]/(n[s]+30) for s in day.sid]).sort_values(['qs','pct'],ascending=[False,False])
        taken=0; seen=set()
        for _,c in day.iterrows():
            if taken>=5: break
            i=int(c.i); t=tick_all[i]
            if t in seen or held.get(t,-1)>d: continue
            seen.add(t); taken+=1
            ex=sexit[int(c.sid)]; res,exk,e,tp,sl=OUTC[ex]
            if np.isfinite(res[i]):
                xd=EXD[ex][i]; held[t]=xd; hist[int(c.sid)].append((xd,res[i]*100))
                picked.append(dict(date=dates[d],pnl=res[i]*100))
    P=pd.DataFrame(picked); return summarize(P)
for tier in TIERS:
    for mth in ['last30','expanding','shrunk','roundrobin','multi']:
        s=combined(tier,mth)
        print(f"{tier:6} {mth:10} n{s['n']} win{s['win']} tot{s['total']:+8.1f} avg{s['avg']:+.2f} DD{s['maxdd']:7.1f} worstM{s['worst_month']:7.1f} q{[round(x) for x in s['q']]}")
