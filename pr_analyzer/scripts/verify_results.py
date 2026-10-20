#!/usr/bin/env python3
"""New C19 audit utility, not an original analysis or plotting script.

Reads existing files; emits JSON only to stdout. Never writes research data,
plots, or the report. Final governance checks use the separately curated authoritative dataset;
no raw governance file is used for final analysis. All paths can be overridden.
"""
import argparse
import collections
import csv
import hashlib
import io
import json
import math
from pathlib import Path
import platform
import statistics
import xml.etree.ElementTree as ET
import zipfile

BASE = 'full_repo_analysis'
W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_csv(path, first='Repo'):
    records = list(csv.reader(io.StringIO(path.read_text(encoding='utf-8-sig'))))
    i = next(i for i, row in enumerate(records) if row and row[0] == first)
    header = records[i]
    rows = [dict(zip(header, row)) for row in records[i+1:]
            if row and row[0].strip() and len(row) == len(header)]
    if first == 'Sample ID':
        rows = [row for row in rows if '#' in row['Sample ID']]
    return header, rows


def key(row):
    return row.get('Sample ID', row.get('Repo', '') + '#' + row.get('PR #', '')).lower()


def index(rows):
    result = {key(row): row for row in rows}
    assert len(result) == len(rows), 'Duplicate canonical PR identifier'
    return result


def percent(numerator, denominator):
    return f'{100*numerator/denominator:.2f}%'


def distribution(rows):
    rates = [row['_rate'] for row in rows]
    q1, _, q3 = (statistics.quantiles(rates, n=4, method='inclusive')
                 if len(rates) > 1 else [rates[0]] * 3)
    lower, upper = q1-1.5*(q3-q1), q3+1.5*(q3-q1)
    return {'n': len(rows), 'mean_percent': statistics.mean(rates),
            'median_percent': statistics.median(rates), 'q1_percent': q1,
            'q3_percent': q3, 'lower_fence_percent': lower,
            'upper_fence_percent': upper,
            'outliers': [row['_repo'] for row in rows
                         if row['_rate'] < lower or row['_rate'] > upper],
            'present': sum(row['_yes'] for row in rows),
            'eligible': sum(row['_total'] for row in rows),
            'pooled_percent': 100*sum(row['_yes'] for row in rows)/sum(row['_total'] for row in rows),
            'repository_ids': [row['_repo'] for row in rows]}


def group(rows, column):
    codes = sorted({row[column].strip() for row in rows})
    return {code: distribution([row for row in rows if row[column].strip() == code])
            for code in codes}


def ranks(values):
    order = sorted(range(len(values)), key=values.__getitem__)
    result = [0.0] * len(values)
    i = 0
    while i < len(values):
        j = i+1
        while j < len(values) and values[order[j]] == values[order[i]]:
            j += 1
        for position in order[i:j]:
            result[position] = (i+1+j)/2
        i = j
    return result


def pearson(x, y):
    mx, my = statistics.mean(x), statistics.mean(y)
    return sum((a-mx)*(b-my) for a,b in zip(x,y))/math.sqrt(
        sum((a-mx)**2 for a in x)*sum((b-my)**2 for b in y))


def report_tables(path):
    with zipfile.ZipFile(path) as package:
        root = ET.fromstring(package.read('word/document.xml'))
    found = {}
    pending = None
    for element in root.find(W+'body'):
        if element.tag == W+'p':
            text = ''.join(node.text or '' for node in element.iter(W+'t'))
            for number in range(1, 6):
                if text.startswith(f'Table 4.{number}.'):
                    pending = f'Table 4.{number}'
        elif element.tag == W+'tbl' and pending:
            found[pending] = [[ ''.join(node.text or '' for node in cell.iter(W+'t'))
                                for cell in row.findall(W+'tc')]
                               for row in element.findall(W+'tr')]
            pending = None
    return found


def verify(args):
    base = args.repo / BASE
    inputs = {}
    def load(name, first='Repo'):
        path = (args.repo/'detector_validation'/name if name.startswith('all_') else base/name)
        inputs[str(path.relative_to(args.repo))] = sha(path)
        return read_csv(path, first)[1]
    main = load('pr_dataset_output.csv')
    summary = load('repo_detected_disclosure_aggregates.csv')
    visibility = load('repo_visibility_summary.csv', 'Repository')
    sample = load('all_repo_validation_sample_seeded.csv', 'Sample ID')
    labels = load('all_manual_seeded_labels.csv', 'Sample ID')
    detailed = load('all_seeded_detector_validation.csv', 'Sample ID')
    saved_corr = load('spearman_correlation.csv', 'Metric Pair')[0]
    main_index = index(main)
    counts = collections.defaultdict(collections.Counter)
    for row in main:
        counts[row['Repo'].lower()][row['AI Disclosure Present']] += 1
    rate_rows = []
    for row in visibility:
        repo = row['Repository'].lower()
        c = counts[repo]
        assert [int(row[name]) for name in ['Successfully Analysed Eligible PRs',
                'PRs With Disclosure', 'PRs Without Disclosure']] == [sum(c.values()), c['Yes'], c['No']]
        assert abs(float(row['Disclosure Rate'])-c['Yes']/sum(c.values())) < 1e-14
        rate_rows.append({'_repo': repo, '_total': sum(c.values()), '_yes': c['Yes'],
                          '_rate': 100*c['Yes']/sum(c.values()),
                          '_visibility': str(int(float(row['Visibility Score Numeric'])))})
    by_repo = {row['_repo']: row for row in rate_rows}
    for row in summary:
        c = counts[row['Repo'].lower()]
        assert [int(row[name]) for name in ['Eligible PRs Reviewed',
                'AI Disclosure Present Count', 'No Disclosure Count']] == [sum(c.values()), c['Yes'], c['No']]
    totals = {name: sum(int(row[name]) for row in summary) for name in [
        'Eligible PRs Reviewed', 'AI Disclosure Present Count', 'No Disclosure Count',
        'Positive Disclosure Count', 'Negative Disclosure Count', 'Ambiguous Disclosure Count']}
    present = totals['AI Disclosure Present Count']
    largest = max(rate_rows, key=lambda row: row['_total'])
    table41 = [[str(len(rate_rows))], [f'{len(main):,}'], [f'{present:,}'],
               [f"{totals['No Disclosure Count']:,}"],
               [str(sum(row['_total'] < 20 for row in rate_rows))],
               [f"{largest['_total']:,} (`{largest['_repo']}`)"], [str(len(labels))]]
    si, li, di = index(sample), index(labels), index(detailed)
    assert si.keys() == li.keys() == di.keys()
    names = {('Yes','Yes'): 'True Positive', ('No','No'): 'True Negative',
             ('Yes','No'): 'False Positive', ('No','Yes'): 'False Negative'}
    confusion = collections.Counter()
    for ident, row in si.items():
        script, human = row['Script AI Disclosure Present'], li[ident]['Disclosure Present']
        outcome = names[(script,human)]
        assert (di[ident]['Script Disclosure Present'],di[ident]['Consensus Disclosure Present'],
                di[ident]['Detector Outcome']) == (script,human,outcome)
        confusion[outcome] += 1
    tp, tn, fp, fn = [confusion[name] for name in ['True Positive','True Negative','False Positive','False Negative']]
    metrics = {'Accuracy': (tp+tn)/(tp+tn+fp+fn), 'Precision': tp/(tp+fp),
               'Recall': tp/(tp+fn), 'Specificity': tn/(tn+fp),
               'F1-score': 2*tp/(2*tp+fp+fn)}
    table43 = [[str(value)] for value in [tp,tn,fp,fn]] + [[f'{100*value:.2f}%'] for value in metrics.values()]
    table44 = [[f'{totals[name]:,}', percent(totals[name],present), percent(totals[name],len(main))]
               for name in ['Positive Disclosure Count','Negative Disclosure Count','Ambiguous Disclosure Count']]
    table44 += [[f'{present:,}', '100.00%',percent(present,len(main))],
                [f"{totals['No Disclosure Count']:,}", '—', percent(totals['No Disclosure Count'],len(main))]]
    levels = group(rate_rows, '_visibility')
    table45 = [[str(levels[level]['n']),f"{levels[level]['mean_percent']:.2f}%",
                f"{levels[level]['median_percent']:.2f}%",f"{levels[level]['pooled_percent']:.2f}%"]
               for level in ['1','2','3']]
    x = [int(row['_visibility']) for row in rate_rows]
    y = [row['_rate'] for row in rate_rows]
    rho = pearson(ranks(x), ranks(y))
    assert abs(rho-float(saved_corr['Spearman Rho'])) <= 5e-11
    high = [row['_rate'] for row in rate_rows if row['_visibility']=='3']
    low = [row['_rate'] for row in rate_rows if row['_visibility']=='1']
    greater = sum(a>b for a in high for b in low)
    less = sum(a<b for a in high for b in low)
    ties = sum(a==b for a in high for b in low)
    result = {'audit_utility': 'New C19 read-only verification; not the original plotting workflow',
              'python_version': platform.python_version(), 'input_sha256': inputs,
              'table_numeric_cells': {'Table 4.1': table41,'Table 4.3':table43,
                                      'Table 4.4':table44,'Table 4.5':table45},
              'all_repositories': distribution(rate_rows), 'visibility_groups': levels,
              'repository_rates': sorted(rate_rows,key=lambda row:row['_rate'],reverse=True),
              'classification_summary_totals': totals,
              'main_row_classification_counts':dict(collections.Counter(row['Disclosure Classification'] for row in main)),
              'confusion_matrix':dict(confusion), 'metrics':metrics,
              'spearman_rho':rho, 'spearman_p_saved':float(saved_corr['P-Value']),
              'cliffs_delta':{'greater':greater,'less':less,'ties':ties,
                              'pairs':len(high)*len(low),'delta':(greater-less)/(len(high)*len(low))}}
    try:
        import scipy
        from scipy.stats import spearmanr
    except ImportError:
        result['spearman_p_crosscheck'] = 'Skipped: optional SciPy unavailable'
    else:
        corr = spearmanr(x,y)
        assert abs(corr.pvalue-float(saved_corr['P-Value'])) <= 5e-11
        result['spearman_p_crosscheck'] = float(corr.pvalue)
        result['scipy_version'] = scipy.__version__
    if args.report:
        result['report_sha256'] = sha(args.report)
        tables = report_tables(args.report)
        result['reported_tables'] = tables
        result['table_numeric_comparisons'] = {}
        for name, calculated in result['table_numeric_cells'].items():
            reported = [row[1:] for row in tables[name][1:]]
            result['table_numeric_comparisons'][name] = {'all_numeric_cells_match':calculated==reported,
                                                        'reported':reported,'calculated':calculated}
            assert calculated==reported, name+' numeric cell mismatch'
    if args.governance:
        result['governance_sha256'] = sha(args.governance)
        headers, governance = read_csv(args.governance)
        columns = headers[:24]
        rows = []
        for original in governance:
            row = dict(original)
            repo = row['Repo'].strip().lower()
            assert repo in by_repo, repo
            assert row['Repository URL'].strip().lower() == 'https://github.com/'+repo
            assert float(row['Visibility score (0-3; from tracker)']) == float(by_repo[repo]['_visibility'])
            row.update(by_repo[repo])
            rows.append(row)
        assert len(rows)==len({row['_repo'] for row in rows})==38
        assert {row['_repo'] for row in rows}==set(by_repo)
        detail = columns[5]
        curl = next(row for row in rows if row['_repo']=='curl/curl')
        assert curl[detail]=='1', 'Authoritative final dataset must carry the confirmed Curl Level 1 correction'
        counts = {column:dict(collections.Counter(row[column].strip() for row in rows))
                  for column in columns[3:20]}
        groups = {column:group(rows,column) for column in columns[5:20]
                  if column != ' AI in discusion'}
        result['governance_final'] = {'path':str(args.governance), 'n':38,
                                     'category_counts':counts,'groups':groups,
                                     'coding_fully_validated':False,
                                     'validation_status_counts':dict(collections.Counter(
                                         row.get('Coding Validation Status','not supplied') for row in rows))}
        result['governance_warning'] = (
            'Authoritative final-38 analysis view uses documented researcher-confirmed Curl correction '
            'and policy-verified CloudNativePG identity. Carried-forward coding and historical policy '
            'applicability are not fully validated. No automatic recoding, exclusions or aliases are '
            'performed by this read-only verifier; historical raw data is not a final-analysis input.')
        result['group_mean_differences'] = {}
        for column in ['PR template', 'Disclosure in PR required (1 yes; 0 no)']:
            yes, no = groups[column]['1']['mean_percent'], groups[column]['0']['mean_percent']
            result['group_mean_differences'][column] = {
                'yes_mean_percent':yes,'no_mean_percent':no,
                'unrounded_difference_pp':yes-no,
                'full_precision_difference_rounded_2dp':f'{yes-no:.2f}',
                'difference_of_rounded_means_2dp':
                    f'{float(f"{yes:.2f}")-float(f"{no:.2f}"):.2f}'}
        spec = [(columns[12],['1','0']), (columns[13],['1','0']),
                ('PR template',['1','0']), (detail,['1','2','3','4','N/A']),
                (columns[8],['0','1','2','3','N/A']), (columns[9],['0','1','2','3']),
                (columns[10],['0','1','2','3']), (columns[11],['0','2','3','N/A'])]
        calculated = [[' / '.join(str(counts[column].get(code,0)) for code in codes)]
                      for column,codes in spec]
        result['table_numeric_cells']['Table 4.2'] = calculated
        if args.report:
            reported = [row[2:] for row in tables['Table 4.2'][1:]]
            result['table_numeric_comparisons']['Table 4.2'] = {
                'all_numeric_cells_match':calculated==reported,
                'reported':reported,'calculated':calculated}
            assert calculated==reported, 'Table 4.2 numeric cell mismatch'
        assert groups[detail]['1']['n']==7
        assert f"{groups[detail]['1']['mean_percent']:.2f}"=='38.60'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2]/'Compendium_Submission')
    parser.add_argument('--report',type=Path)
    parser.add_argument('--governance',type=Path,
                        default=None, help='Defaults to governance_coding/governance_coding_final.csv within --repo')
    args = parser.parse_args()
    if args.governance is None:
        args.governance = args.repo/'governance_coding/governance_coding_final.csv'
    print(json.dumps(verify(args),indent=2,ensure_ascii=False))


if __name__ == '__main__':
    main()
