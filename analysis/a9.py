from bt import *
import json
liq = (df['close'] >= 50) & (df['val_ma20'] >= 300e6) & (df['trade_days20'] >= 12) & df['ret'].notna()
base = liq & df['is_ara'] & (df['high'] > df['low'])
S = {
 "1. ARA Segar":        base & (df['ara_streak'] <= 2) & (df['gain20'] < 0.30),
 "2. ARA Senyap":       base & (df['ara_streak'] <= 2) & (df['vol_ratio'] < 3),
 "3. ARA Bangun Tidur": base & (df['ara_streak'] <= 2) & (df['dry5'] < 0.7),
 "4. ARA Kedua":        base & (df['ara_streak'] == 2),
 "5. ARA Gocap":        base & (df['ara_streak'] <= 2) & (df['prev_close'] <= 200),
 "Semua ARA (streak 1-2)": base & (df['ara_streak'] <= 2),
}
out = {}
for name, m in S.items():
    r, rt = run_close(m, .03, .04, 3, period=TRAIN), run_close(m, .03, .04, 3, period=TEST)
    perday = df[m].groupby('date').size()
    out[name] = dict(train=r, test=rt, days_with_signal=int(perday.size), median_per_day=float(perday.median()))
    print(f"{name:24} TRAIN {fmt(r)}\n{'':24} TEST  {fmt(rt)}   hari ada sinyal {perday.size}/240")
# monthly consistency of the combined ARA strategy
m = S["Semua ARA (streak 1-2)"]
s = df[m]
res = [(dt[:7], simulate_close(t, idx[(t, dt)], .03, .04, 3)) for t, dt in zip(s['ticker'], s['date'])]
mm = pd.DataFrame([x for x in res if x[1] is not None], columns=['month','r'])
summary = mm.groupby('month')['r'].agg(n='size', win=lambda x: (x>0).mean()*100, avg=lambda x: x.mean()*100)
print(summary.round(1))
out['monthly'] = summary.round(2).reset_index().to_dict('records')
# examples in last 60 days for the strongest strategy
ex = df[S["1. ARA Segar"] & df['date'].isin(TEST)][['date','ticker','close','ret','gain20','vol_ratio']]
ex['hasil'] = [simulate_close(t, idx[(t,dt)], .03,.04,3) for t,dt in zip(ex['ticker'], ex['date'])]
print(ex.sort_values('date').tail(15).to_string(index=False))
json.dump(out, open('final_stats.json','w'), default=float, indent=1)
