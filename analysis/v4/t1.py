from engine import *
b = df['mkt_breadth_ma5'].to_numpy(); r5 = df['mkt_ew_ret5'].to_numpy()
REG = {'ret': r5 > -.03, 'br30': b >= .30, 'br50': b >= .50, 'weak': b < .30}
OLD = [(1,'rsi<35 & udvr2 & p<200','udvr','atr2.5/2','ret'),(2,'up3 & rsi<35 & p<200','rsi_low','atr2/1.5','ret'),
 (3,'upstreak3 & pos60_lo & inside','lowpos','low10/res','br30'),(4,'squeeze & upstreak3 & pos60_lo','volratio','low5/3atr','ret'),
 (5,'squeeze & near_ma20 & p<200','volratio','low5/res','br50'),(6,'lwick & gapdown_today','volratio','atr2.5/2','weak')]
rows=[]
for sid,c,rk,ex,rg in OLD:
    for t in TIERS:
        m = mask_of(c,t).to_numpy() & REG[rg]
        tr,_ = run(m,rk,ex); s=summarize(tr)
        rows.append((sid,t,s.get('n',0),s.get('win'),s.get('total'),s.get('avg'),s.get('maxdd'),s.get('worst_month')))
o=pd.DataFrame(rows,columns=['S','tier','n','win','total','avg','dd','worstM'])
print(o.pivot(index='S',columns='tier',values='total')[list(TIERS)].to_string())
print(o.pivot(index='S',columns='tier',values='n')[list(TIERS)].to_string())
print(o.pivot(index='S',columns='tier',values='avg')[list(TIERS)].to_string())
