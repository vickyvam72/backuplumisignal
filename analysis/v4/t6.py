exec(open('t5.py').read().split("for tier in TIERS:")[0])
base_REG = dict(REG)
# variant: every non-weak strategy also needs breadth >= 30%
REG['ret'] = base_REG['ret'] & (b >= .30)
for tier in TIERS:
    s = combined(tier, 'expanding')
    print(f"{tier:6} expanding+br30 n{s['n']} win{s['win']} tot{s['total']:+8.1f} avg{s['avg']:+.2f} DD{s['maxdd']:7.1f} worstM{s['worst_month']:7.1f} q{[round(x) for x in s['q']]}")
# individual effect
for sdef in STR:
    if sdef['reg']!='ret': continue
    line=[]
    for tier in ['semua','1M','5M','20M']:
        for nm,rg in [('ret',base_REG['ret']),('ret+br30',REG['ret'])]:
            m = mask_of(sdef['cond'],tier).to_numpy() & rg
            tr,_=run(m,sdef['rank'],sdef['exit']); s=summarize(tr)
            line.append(f"{tier}/{nm}: {s.get('total',0):+.0f} dd{s.get('maxdd',0):.0f}")
    print('S',sdef['id'],' | '.join(line))
