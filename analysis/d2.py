from engine import *
S = [('rsi<35 & udvr2 & p<200','udvr','atr2/1.5'),('up3 & rsi<35 & p<200','rsi_low','atr2/1.5'),('upstreak3 & pos60_lo & inside','lowpos','low5/res'),
     ('squeeze & upstreak3 & pos60_lo','volratio','low5/3atr'),('squeeze & near_ma20 & p<200','volratio','low5/res')]
M = {}
for k,(c,rk,ex) in enumerate(S,1):
    tr,_ = run(mask_of(c), rk, ex)
    M[f'S{k}'] = tr.groupby(tr.date.str[:7])['pnl'].sum().round(0)
    M[f'n{k}'] = tr.groupby(tr.date.str[:7])['pnl'].size()
t = pd.DataFrame(M).fillna(0)
t['GAB'] = t[[f'S{k}' for k in range(1,6)]].sum(axis=1)
m2 = mk.copy(); m2['m'] = m2.index.str[:7]
reg = m2.groupby('m').agg(ew_month=('ew_ret', lambda x: round(((1+x).prod()-1)*100,1)), breadth=('breadth', lambda x: round(x.mean()*100)),
                          days_down=('ew_ret', lambda x: int((x<0).sum())), worst_day=('ew_ret', lambda x: round(x.min()*100,1)))
pd.set_option('display.width',250)
print(t.join(reg).to_string())
# May detail: daily market
print(mk.loc['2026-04-20':'2026-06-10', ['ew_ret','breadth','ew_ret5']].round(3).to_string())
