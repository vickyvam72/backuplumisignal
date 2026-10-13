from engine import *
import time
S = [('rsi<35 & udvr2 & p<200','udvr'),('up3 & rsi<35 & p<200','rsi_low'),('upstreak3 & pos60_lo & inside','lowpos'),
     ('squeeze & upstreak3 & pos60_lo','volratio'),('squeeze & near_ma20 & p<200','volratio')]
t0=time.time()
for c,rk in S:
    m = mask_of(c)
    for ex in ['FIX'] + list(EXITS):
        if ex == 'FIX':
            EXITS['FIX'] = dict(tp=.12, sl=.07, hold=7)
        tr, rec = run(m, rk, ex); trp,_ = run(m, rk, ex, path='pess')
        s = summarize(tr); p = summarize(trp)
        print(f"{c:32} {rk:8} {ex:14} n{s['n']:4} win{s['win']:5} tot{s['total']:+7.1f} avg{s['avg']:+5.2f} DD{s['maxdd']:7.1f} q{s['q']} pess{p['total']:+7.1f} worstM{s['worst_month']}")
print(time.time()-t0)
