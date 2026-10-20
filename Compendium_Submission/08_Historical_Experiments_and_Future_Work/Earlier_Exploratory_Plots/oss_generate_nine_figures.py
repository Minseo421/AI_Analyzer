#!/usr/bin/env python3
"""Reproduce nine OSS governance vs observed AI disclosure figures.
Input: /mnt/data/OSS_40_repositories_governance_disclosure_FINAL_BASELINE.csv
40 repositories; napari visibility=3; curl disclosure detail=1.
Observed disclosure count stays at 11,592 out of 41,468 eligible PRs.
"""
import csv
import math
from pathlib import Path
from collections import defaultdict

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
from matplotlib.lines import Line2D
import numpy as np
from scipy.stats import spearmanr

ROOT=Path('/mnt/data')
SRC=ROOT/'OSS_40_repositories_governance_disclosure_FINAL_BASELINE.csv'
OUT=ROOT/'OSS_Results_9_Figures'
OUT.mkdir(exist_ok=True)

with SRC.open(encoding='utf-8-sig',newline='') as f:
    DATA=list(csv.DictReader(f))
assert len(DATA)==40
for row in DATA:
    row['_n']=int(row['Eligible PRs'])
    row['_yes']=int(row['Disclosure Present'])
    row['_rate']=100*float(row['Disclosure Rate'])
    assert row['_n']>0 and row['_yes']<=row['_n']
    assert abs(row['_rate']-100*row['_yes']/row['_n'])<1e-7
assert sum(r['_n'] for r in DATA)==41468
assert sum(r['_yes'] for r in DATA)==11592
assert next(r for r in DATA if r['Repository']=='napari/napari')['Visibility (1-3)']=='3'
assert next(r for r in DATA if r['Repository']=='curl/curl')['Disclosure Detail']=='1'
rho,p=spearmanr([int(r['Visibility (1-3)']) for r in DATA], [r['_rate'] for r in DATA])
assert abs(rho-0.6510396022192574)<1e-10

NAVY='#17324D'; TEAL='#137B8C'; ORANGE='#D48245'; SLATE='#64748B'; PALE='#DFEEF1'; GRID='#E6EDF0'; BACK='#FFFFFF'; BLACK='#213547'
plt.rcParams.update({'font.family':'DejaVu Sans', 'font.size':10.3, 'axes.titlesize':15.5,
                     'axes.labelsize':11, 'xtick.labelsize':9.0, 'ytick.labelsize':9.4,
                     'axes.edgecolor':'#C7D3D9','axes.linewidth':0.8,
                     'pdf.fonttype':42,'ps.fonttype':42,'svg.fonttype':'none',
                     'savefig.facecolor':'white'})

BASELINE='DATA: 40 OSS repositories | 41,468 eligible PRs | 11,592 disclosure-present (27.95%)'
SHORTNOTE='Outcome = observed AI-use or non-use declaration, not confirmed AI use or policy compliance.'

# Descriptive summary output
SUMMARY=[]
def write_stat(chart,category,label,rows):
    n=len(rows)
    if not n: return
    vals=[r['_rate'] for r in rows]
    SUMMARY.append({'Figure':str(chart),'Factor':category,'Category':label,'Repositories (n)':str(n),
        'Mean repo disclosure rate (%)':f'{np.mean(vals):.6f}',
        'Median repo disclosure rate (%)':f'{np.median(vals):.6f}',
        'Total eligible PRs':str(sum(r['_n'] for r in rows)),
        'Total disclosed PRs':str(sum(r['_yes'] for r in rows)),
        'Pooled PR disclosure rate (%)':f'{100*sum(r["_yes"] for r in rows)/sum(r["_n"] for r in rows):.6f}'})

def decorate(fig, ax, fig_id, title, subtitle, note):
    fig.patch.set_facecolor(BACK)
    fig.text(.07,.944, f'FIGURE {fig_id:02}', color=TEAL, fontsize=9, weight='bold', va='bottom')
    fig.text(.07,.906, title, color=NAVY, fontsize=18.3, weight='bold', va='bottom')
    fig.text(.07,.869, subtitle, color=SLATE, fontsize=10, va='bottom')
    ax.set_axisbelow(True)
    ax.grid(axis='y',color=GRID,linewidth=.9)
    for sp in ('top','right'): ax.spines[sp].set_visible(False)
    fig.text(.07,.115, note, color=BLACK, fontsize=8.25)
    fig.text(.07,.088, SHORTNOTE, color=SLATE, fontsize=8.1)
    fig.text(.07,.057, BASELINE, color=SLATE, fontsize=7.7)

def make_grouped(fig_id,filename,title,subtitle,factor,categories,note,
                 show_missing=False, nudge=None):
    fig=plt.figure(figsize=(11.65,7.15),facecolor='white')
    ax=fig.add_axes([.085,.24,.86,.56])
    means=[]; cats=[]; n_tot=0
    for i,(v,name) in enumerate(categories):
        rr=[r for r in DATA if r[factor]==v]
        write_stat(fig_id,factor,v,rr)
        cats.append(f'{name}\nn={len(rr)}'+(f'  |  mean {np.mean([r["_rate"] for r in rr]):.1f}%' if rr else '  |  no data'))
        if not rr: continue
        rates=[r['_rate'] for r in rr]
        n_tot+=len(rr)
        if len(rates)>=2:
            box=ax.boxplot([rates], positions=[i], widths=.48, patch_artist=True, showfliers=False,
                manage_ticks=False, whis=1.5, zorder=1)
            for b in box['boxes']: b.set(facecolor=PALE,edgecolor='#81ADB7',linewidth=1.15,alpha=.92)
            for m in box['medians']:m.set(color=NAVY,linewidth=2.25)
            for w in box['whiskers']+box['caps']:w.set(color='#81ADB7',linewidth=1.2)
        for r in rr:
            # Deterministic repo-specific jitter, no effect on observed value.
            code=sum((k+11)*ord(ch) for k,ch in enumerate(r['Repository']))
            jitter=((code*1301)%201-100)/100 * .20
            ax.scatter(i+jitter,r['_rate'],s=45,facecolors=TEAL,edgecolors='white',linewidths=.65,alpha=.86,zorder=4)
        av=np.mean(rates)
        ax.scatter(i,av,s=116,marker='D',facecolors=ORANGE,edgecolors='white',linewidths=1.4,zorder=6)
        means.append(av)
    assert n_tot==40, (factor,n_tot)
    ax.set_xticks(range(len(categories)),cats)
    ax.set_ylim(-2,101);ax.set_yticks([0,20,40,60,80,100]);ax.set_ylabel('Observed disclosure rate per repository (%)',labelpad=10,color=BLACK)
    ax.set_xlim(-.55,len(categories)-.45)
    decorate(fig,ax,fig_id,title,subtitle,note)
    for i,lbl in enumerate(ax.get_xticklabels()):
        if categories[i][0]=='N/A' or not len([r for r in DATA if r[factor]==categories[i][0]]): lbl.set_color(SLATE)
    legend=[Line2D([],[],color=TEAL,marker='o',linestyle='',markersize=7,label='One repository'),
            Line2D([],[],color=ORANGE,marker='D',linestyle='',markersize=8,label='Unweighted group mean'),
            Line2D([],[],color='#81ADB7',linewidth=7,alpha=.28,label='Box = middle 50% (n ≥ 2)')]
    ax.legend(handles=legend,loc='upper right',frameon=False,ncol=3,fontsize=8.15,bbox_to_anchor=(1.012,1.095))
    if nudge:
        fig.text(.07,.03,nudge,color=SLATE,fontsize=7.6)
    fig.savefig(OUT/(filename+'.png'),dpi=230,bbox_inches='tight',pad_inches=.25)
    fig.savefig(OUT/(filename+'.svg'),bbox_inches='tight',pad_inches=.25)
    return fig

figures=[]
figures.append(make_grouped(1,'01_Visibility_vs_Disclosure_Rate',
  'Policy visibility vs. disclosure rate',
  f'Spearman rank correlation: ρ = {rho:.3f}, p = {p:.2g} (n = 40 repositories)',
  'Visibility (1-3)', [('1','1 · Low'),('2','2 · Moderate'),('3','3 · High')],
  'Visibility measures opportunity to encounter guidance, not whether it was read or followed.',
  nudge='Updated coding: napari/napari visibility = 3.'))
figures.append(make_grouped(2,'02_Disclosure_Detail_vs_Disclosure_Rate',
  'Policy disclosure detail vs. disclosure rate',
  'Requirements grouped by coded level; N/A denotes no classifiable disclosure-detail requirement',
  'Disclosure Detail',[('1','1 · Basic'),('2','2 · Name tool'),('3','3 · Explain use'),('4','4 · Traceability'),('N/A','N/A')],
  'Disclosure-detail scores describe policy text, not what individual PRs disclosed.',
  nudge='Updated coding: curl/curl disclosure detail = 1. N/A is not part of the ordered 1–4 scale.'))

# Fig 3: rationale six binary groups, each row a difference of unweighted group means.
fig=plt.figure(figsize=(12.2,7.25),facecolor='white')
ax=fig.add_axes([.22,.245,.59,.54]); right=fig.add_axes([.81,.245,.17,.54],sharey=ax)
fac=[('Rationale: Transparency','Transparency'),('Rationale: Code Quality','Code quality'),
     ('Rationale: Reviewer Workload','Reviewer workload'),('Rationale: Human Accountability','Human accountability'),
     ('Rationale: Rights / Provenance','Rights / provenance'),('Rationale: Other','Other')]
max_y=len(fac)-1
for i,(field,lab) in enumerate(fac):
    no=[r for r in DATA if r[field]=='0']; yes=[r for r in DATA if r[field]=='1']
    assert len(no)+len(yes)==40
    write_stat(3,field,'0 · Not stated',no);write_stat(3,field,'1 · Stated',yes)
    mn0=np.mean([r['_rate'] for r in no]);mn1=np.mean([r['_rate'] for r in yes]);yy=max_y-i
    ax.plot([mn0,mn1],[yy,yy],color='#BECBD3',linewidth=3,zorder=1)
    ax.scatter(mn0,yy,s=110,color=SLATE,edgecolors='white',linewidth=1.2,zorder=4)
    ax.scatter(mn1,yy,s=110,color=TEAL,edgecolors='white',linewidth=1.2,zorder=5)
    ax.annotate(f'{mn0:.1f}%', (mn0,yy),xytext=(0,10),textcoords='offset points',
                ha='center',va='bottom',fontsize=8.3,color=SLATE)
    ax.annotate(f'{mn1:.1f}%', (mn1,yy),xytext=(0,-11),textcoords='offset points',
                ha='center',va='top',fontsize=8.3,color=TEAL)
    # Paired right-side columns show n and group mean differences.
    right.text(.12,yy,f'{len(yes):>2} / {len(no):>2}',ha='left',va='center',color=BLACK,fontsize=9.1)
    right.text(.95,yy,f'{mn1-mn0:+.1f}',ha='right',va='center',color=TEAL if mn1>=mn0 else SLATE,fontsize=9.2,weight='bold')
ax.set_yticks(range(len(fac)),[s for _,s in fac[::-1]])
ax.set_xlim(0,57);ax.set_xticks([0,10,20,30,40,50]);ax.set_xlabel('Mean repository disclosure rate (%)',labelpad=10)
ax.set_ylim(-.6,max_y+.6);ax.set_axisbelow(True);ax.grid(axis='x',color=GRID)
for sp in ('top','right','left'):ax.spines[sp].set_visible(False)
ax.tick_params(axis='y',length=0,pad=12)
right.set_xlim(0,1);right.set_ylim(ax.get_ylim());right.axis('off')
fig.text(.82,.807,'n  YES / NO',color=SLATE,fontsize=8.5,weight='bold')
fig.text(.972,.807,'Δ pp',color=SLATE,fontsize=8.5,weight='bold',ha='right')
fig.text(.07,.949,'FIGURE 03',color=TEAL,fontsize=9,weight='bold',va='bottom')
fig.text(.07,.906,'Policy rationales vs. disclosure rate',color=NAVY,fontsize=18.3,weight='bold',va='bottom')
fig.text(.07,.869,'Teal = rationale stated  ·  Grey = not stated  ·  Δ pp = mean difference',color=SLATE,fontsize=10,va='bottom')
legend=[Line2D([],[],color=TEAL,marker='o',linestyle='',markersize=8,label='Rationale stated'),
        Line2D([],[],color=SLATE,marker='o',linestyle='',markersize=8,label='Not stated')]
# Color roles explained in subtitle; bottom legend omitted to prevent footer overlap.
fig.text(.07,.115,'Each dot is the unweighted mean of repository disclosure rates; Δ pp = stated minus not stated.',color=BLACK,fontsize=8.25)
fig.text(.07,.088,SHORTNOTE,color=SLATE,fontsize=8.1)
fig.text(.07,.057,BASELINE,color=SLATE,fontsize=7.7)
fig.savefig(OUT/'03_Policy_Rationales_vs_Disclosure_Rate.png',dpi=230,bbox_inches='tight',pad_inches=.25)
fig.savefig(OUT/'03_Policy_Rationales_vs_Disclosure_Rate.svg',bbox_inches='tight',pad_inches=.25)
figures.append(fig)

figures.append(make_grouped(4,'04_PR_Disclosure_Required_vs_Disclosure_Rate',
  'Disclosure required in PR vs. disclosure rate',
  'Binary policy-coding indicator for an explicit PR disclosure requirement',
  'PR Disclosure Required',[('0','0 · Not required'),('1','1 · Required')],
  'The indicator reflects documented PR disclosure requirements; not a verified compliance rate.'))
figures.append(make_grouped(5,'05_Responsibility_vs_Disclosure_Rate',
  'Contributor responsibility vs. disclosure rate',
  'Coded responsibility obligations in AI-related contribution policies',
  'Responsibility',[('0','0 · None'),('1','1 · General'),('2','2 · Explain'),('3','3 · Verify / test'),('N/A','N/A')],
  'Scores describe categories of documented obligations. The 0 and N/A groups each have one repository.'))
figures.append(make_grouped(6,'06_AI_Use_Restrictions_vs_Disclosure_Rate',
  'AI-use restrictions vs. disclosure rate',
  'Coded restrictions: none, low-effort submissions, specific contexts, or general prohibition',
  'AI-use Constraints',[('0','0 · None'),('1','1 · Bulk / low-effort'),('2','2 · Specific contexts'),('3','3 · General ban')],
  'Restriction score 3 has one repository; these categories do not imply equal numerical distances.'))
figures.append(make_grouped(7,'07_Stated_Consequences_vs_Disclosure_Rate',
  'Stated policy consequences vs. disclosure rate',
  'Documented AI-related consequences, not observed enforcement actions',
  'Stated Consequence',[('0','0 · None'),('1','1 · Correction'),('2','2 · Rejection'),('3','3 · Restriction'),('N/A','N/A')],
  'No repository is coded consequence level 1; the N/A group contains one repository.'))
figures.append(make_grouped(8,'08_Attribution_vs_Disclosure_Rate',
  'Attribution requirements vs. disclosure rate',
  'Coded IP, licensing, and provenance requirements in AI-related contribution policies',
  'Attribution',[('0','0 · None'),('1','1 · IP duty'),('2','2 · Licence / DCO / CLA'),('3','3 · Provenance')],
  'No repository is coded attribution level 3; a missing category is not evidence of zero disclosure.'))
figures.append(make_grouped(9,'09_PR_Template_Present_vs_Disclosure_Rate',
  'PR template presence vs. disclosure rate',
  'Presence of any PR template, regardless of whether it mentions AI',
  'PR Template Present',[('0','0 · No template'),('1','1 · Template present')],
  'Template presence overlaps strongly with visibility; this does not isolate a template effect.',
  nudge='18 of 19 high-visibility repositories have a PR template; the two factors are not independent.'))

with PdfPages(ROOT/'OSS_Governance_9_Results_Graphs.pdf',metadata={
    'Title':'OSS Governance and Disclosure: Nine Descriptive Comparisons',
    'Author':'Generated from 40-repository research dataset',
    'Subject':'Observed explicit AI-use or non-use disclosure in 41,468 PRs'}) as pdf:
    for fig in figures: pdf.savefig(fig)

with (ROOT/'OSS_9_Figure_Group_Summary.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=list(SUMMARY[0]));writer.writeheader();writer.writerows(SUMMARY)

# 3 x 3 contact sheet (for navigation, not for academic publication).
from PIL import Image, ImageOps, ImageDraw, ImageFont
names=['01_Visibility_vs_Disclosure_Rate','02_Disclosure_Detail_vs_Disclosure_Rate','03_Policy_Rationales_vs_Disclosure_Rate',
       '04_PR_Disclosure_Required_vs_Disclosure_Rate','05_Responsibility_vs_Disclosure_Rate','06_AI_Use_Restrictions_vs_Disclosure_Rate',
       '07_Stated_Consequences_vs_Disclosure_Rate','08_Attribution_vs_Disclosure_Rate','09_PR_Template_Present_vs_Disclosure_Rate']
thumb_w,thumb_h=680,423
sheet=Image.new('RGB',(thumb_w*3+56,thumb_h*3+56),(236,241,244))
for i,name in enumerate(names):
    im=Image.open(OUT/(name+'.png')).convert('RGB')
    im.thumbnail((thumb_w-30,thumb_h-30),Image.Resampling.LANCZOS)
    x=14+(i%3)*(thumb_w+14);y=14+(i//3)*(thumb_h+14)
    sheet.paste(im,(x+(thumb_w-im.width)//2,y+(thumb_h-im.height)//2))
sheet.save(ROOT/'OSS_9_Graphs_Overview.png',optimize=True)

for fig in figures:plt.close(fig)
print('Created figures:',len(figures))
print('Rows in summary:',len(SUMMARY))
print('Spearman rho, p:',rho,p)
for key in ['OSS_Governance_9_Results_Graphs.pdf','OSS_9_Figure_Group_Summary.csv','OSS_9_Graphs_Overview.png']:
 pth=ROOT/key
 print(pth, pth.stat().st_size)
