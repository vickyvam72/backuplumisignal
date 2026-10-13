import pandas as pd, numpy as np
df = pd.read_pickle('feat.pkl')
dates = sorted(df['date'].unique()); last60 = dates[-60:]
print("60 sesi:", last60[0], "->", last60[-1])
w = df[df['date'].isin(last60)]
# 1) Top total gainers over the window (close of last day vs close before window)
start = df[df['date']==dates[-61]].set_index('ticker')['close']
end = df[df['date']==dates[-1]].set_index('ticker')['close']
tot = (end/start-1).dropna().sort_values(ascending=False)
liq = w.groupby('ticker')['value'].mean()
print("\nTOP 25 kenaikan total 60 hari")
for t,v in tot.head(25).items():
    print(f"{t:6} {v*100:+7.1f}%  {start[t]:>7.0f} -> {end[t]:>7.0f}  rata nilai/hari {liq.get(t,0)/1e9:5.2f}M")
print("\nDistribusi:", (tot>0).sum(),"naik |", (tot<0).sum(),"turun |", (tot>=0.5).sum(), "naik >=50% |", (tot>=1).sum(), "naik >=100%")
# 2) Big up days
big = w[w['ret']>=0.10]
print("\nHari naik >=10%:", len(big), "kejadian di", big['ticker'].nunique(), "saham | ARA:", int(w['is_ara'].sum()))
cnt = big.groupby('ticker').size().sort_values(ascending=False)
print("Paling sering naik >=10%:", ", ".join(f"{t}({n})" for t,n in cnt.head(15).items()))
# price band of big movers
bands = pd.cut(big['prev_close'], [0,100,200,500,1000,5000,1e9], labels=['<100','100-200','200-500','500-1rb','1rb-5rb','>5rb'])
base = pd.cut(w['prev_close'], [0,100,200,500,1000,5000,1e9], labels=['<100','100-200','200-500','500-1rb','1rb-5rb','>5rb'])
print("\nPorsi kejadian naik>=10% per rentang harga vs porsi semua hari:")
print(pd.DataFrame({'big%':bands.value_counts(normalize=True).sort_index()*100,'all%':base.value_counts(normalize=True).sort_index()*100}).round(1))
mkt = w.groupby('date')['ret'].median()
print("\nMedian return harian pasar 60 hari:", round(mkt.mean()*100,3), "% | hari pasar merah:", (mkt<0).sum(), "dari 60")
