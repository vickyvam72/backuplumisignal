from engine import *
b = df['mkt_breadth_ma5'].to_numpy(); r5 = df['mkt_ew_ret5'].to_numpy()
REG = {'none': np.ones(len(df),bool), 'ret': r5 > -.03, 'br40': b >= .40}
C = ['up3 & pos60_lo & reversal','pos60_lo & reversal','rsi<35 & engulf','downtrend & rsi<35 & engulf','squeeze & gain60_lo','squeeze & pos60_lo & gain60_lo',
     'dry5 & near_ma20 & gain60_lo','dry5 & pos60_lo & big10','up3 & lwick & pos60_lo','up3 & squeeze & pos60_lo','down3 & red2 & rsi<30','down3 & rsi<35 & big10',
     'lwick & pos60_lo & nr7','vollo & lwick & hl_rise','squeeze & pos60_lo & hl_rise','dry5 & pos60_lo & hl_rise','pullback_up & vollo','pullback_up & hl_rise',
     'cross5_20 & volhi','reclaim_ma50 & volhi','brk20 & vol_up_close_hi & tight20','new_hh60 & volhi & cpos_hi','uptrend & nr7 & inside']
rows=[]
for c in C:
    for rg,rm in REG.items():
        for ex in ['atr2/1.5','atr3/1.5','low5/res','low10/res','low5/3atr']:
            for rk in ['volratio','lowpos','value']:
                res_t={}
                for t in ['250jt','1M','5M','20M']:
                    m = mask_of(c,t).to_numpy() & rm
                    tr,_ = run(m,rk,ex); trp,_=run(m,rk,ex,path='pess')
                    s=summarize(tr); p=summarize(trp)
                    res_t[t]=(s.get('n',0),s.get('total',0),p.get('total',0),s.get('maxdd',0),min(p.get('q',[0])) if s.get('n',0) else 0,s.get('worst_month',0),sum(x>0 for x in p.get('q',[])))
                rows.append((c,rg,ex,rk)+tuple(v for t in ['250jt','1M','5M','20M'] for v in res_t[t]))
cols=['cond','reg','exit','rank']+[f'{k}_{t}' for t in ['250jt','1M','5M','20M'] for k in ['n','tot','ptot','dd','qmin','worst','qpos']]
o=pd.DataFrame(rows,columns=cols); o.to_pickle('t3.pkl')
o['score']=(o['ptot_1M']/(-o['dd_1M']).clip(lower=40))+(o['ptot_5M']/(-o['dd_5M']).clip(lower=40))+0.5*(o['ptot_20M']/(-o['dd_20M']).clip(lower=40))
pd.set_option('display.width',300); pd.set_option('display.max_columns',40)
print(o.sort_values('score',ascending=False).groupby('cond').head(1)[['cond','reg','exit','rank','n_1M','tot_1M','dd_1M','qpos_1M','n_5M','tot_5M','dd_5M','qpos_5M','worst_5M','n_20M','tot_20M','dd_20M','qpos_20M','tot_250jt','score']].to_string())
