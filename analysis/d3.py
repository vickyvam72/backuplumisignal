from engine import *
import itertools
S = [('S1','rsi<35 & udvr2 & p<200','udvr'),('S2','up3 & rsi<35 & p<200','rsi_low'),('S3','upstreak3 & pos60_lo & inside','lowpos'),
     ('S4','squeeze & upstreak3 & pos60_lo','volratio'),('S5','squeeze & near_ma20 & p<200','volratio')]
b = df['mkt_breadth_ma5']
REG = {'none': np.ones(len(df),bool), 'idx>ma20': df['mkt_up'].to_numpy(), 'br>=.30': (b>=.30).to_numpy(), 'br>=.40': (b>=.40).to_numpy(),
       'br>=.50': (b>=.50).to_numpy(), 'ret5>-3%': (df['mkt_ew_ret5']>-.03).to_numpy(), 'idx>ma20|br>=.4': (df['mkt_up'] | (b>=.4)).to_numpy()}
rows=[]
for nm,c,rk in S:
    m0 = mask_of(c).to_numpy()
    for rg,rm in REG.items():
        for ex in EXITS:
            tr,_ = run(m0 & rm, rk, ex); 
            if len(tr)<40: continue
            trp,_ = run(m0 & rm, rk, ex, path='pess')
            s=summarize(tr); p=summarize(trp)
            rows.append((nm,rg,ex,s['n'],s['win'],s['total'],p['total'],s['maxdd'],s['worst_month'],s['months_neg100'],min(p['q']),sum(x>0 for x in p['q'])))
o=pd.DataFrame(rows,columns=['s','regime','exit','n','win','total','pess','dd','worstM','m<-100','qmin','qpos'])
o['score']=o['pess']/(-o['dd']).clip(lower=30)
pd.set_option('display.width',250); pd.set_option('display.max_rows',400)
for nm in ['S1','S2','S3','S4','S5']:
    print(o[o.s==nm].sort_values('score',ascending=False).head(6).to_string()); print()
o.to_csv('d3.csv',index=False)
