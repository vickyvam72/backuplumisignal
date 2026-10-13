from engine import *
import itertools
b = df['mkt_breadth_ma5']; r5 = df['mkt_ew_ret5']
WEAK = {'weak(ret5<=-3%)': (r5 <= -.03).to_numpy(), 'weak(br<.30)': (b < .30).to_numpy(), 'crash(br<.15)': (b < .15).to_numpy()}
for k,v in WEAK.items(): print(k, 'hari:', pd.Series(D[v]).nunique())
atoms = ['rsi<35','rsi<40','down3','red','red2','cpos_lo','cpos_hi','lwick','gapdown_today','green','up3','udvr2','volhi','vollo','pos60_lo','p<200','p>=200','uptrend','downtrend','big10','inside','near_ma20']
combos = [(a,) for a in atoms] + list(itertools.combinations(atoms,2))
months_weak = None
rows=[]
for wk, wm in WEAK.items():
    for cb in combos:
        m = base.to_numpy() & wm
        for a in cb: m &= A[a].to_numpy()
        if m.sum() < 60: continue
        for rk in ['rsi_low','volratio','ret']:
            for ex in ['atr2/1.5','atr2.5/2','low5/res']:
                tr,_ = run(m, rk, ex)
                if len(tr) < 30: continue
                trp,_ = run(m, rk, ex, path='pess')
                s=summarize(tr); p=summarize(trp)
                # per episode (month) consistency
                mon = trp.groupby(trp.date.str[:7])['pnl'].sum()
                rows.append((wk,' & '.join(cb),rk,ex,s['n'],s['win'],s['total'],p['total'],s['maxdd'],int((mon>0).sum()),len(mon),round(mon.min(),1)))
o=pd.DataFrame(rows,columns=['weak','cond','rank','exit','n','win','total','pess','dd','mon_pos','mon_n','worst_mon'])
o.to_csv('d4.csv',index=False)
o['score']=o['pess']/(-o['dd']).clip(lower=20)
pd.set_option('display.width',250); pd.set_option('display.max_rows',100)
print(o.sort_values('score',ascending=False).groupby(['weak','cond']).head(1).groupby('weak').head(12).to_string())
