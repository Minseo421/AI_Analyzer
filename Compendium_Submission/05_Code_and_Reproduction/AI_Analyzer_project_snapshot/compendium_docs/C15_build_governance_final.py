#!/usr/bin/env python3
"""Build the user-authorized final governance view; preserve raw inputs.

New compendium transformation utility, not a historical analysis script.
Existing destination files are verified byte-for-byte, never overwritten.
"""
import csv
import hashlib
import io
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT/'compendium_docs/data'
SOURCE = Path('/Users/minseokim/Desktop/P4P/GPT/repository_dataset(Governance_Coding_Sheet).csv')
RAW = DATA/'historical_raw/governance_coding_40_repository_original.csv'
FINAL = DATA/'governance_coding_final.csv'
LEDGER = DATA/'governance_coding_transformations.json'
EXPECTED_RAW_SHA = 'e33ee272a42c56dd49cd94dde0c1b1daaaad4d797b46540d45880bf3cdaf48e2'
EVIDENCE = 'compendium_docs/data/policy_evidence/2026-10-20_cloudnativepg_curl_policy_verification.md'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def create_or_verify(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        assert path.read_bytes() == data, f'Refusing to overwrite differing existing file: {path}'
    else:
        with path.open('xb') as stream:
            stream.write(data)


def main():
    raw_bytes = RAW.read_bytes() if RAW.exists() else SOURCE.read_bytes()
    assert digest(raw_bytes) == EXPECTED_RAW_SHA, 'Raw governance input differs from reviewed source'
    if SOURCE.exists():
        assert SOURCE.read_bytes() == raw_bytes, 'External original changed; review before rebuilding'
    create_or_verify(RAW, raw_bytes)
    records = list(csv.reader(io.StringIO(raw_bytes.decode('utf-8-sig'))))
    headers = records[0][:24]
    assert len(headers) == len(set(headers)) == 24
    raw_rows = [(record_number, dict(zip(headers, row[:24])))
                for record_number,row in enumerate(records[1:],2) if row and row[0].strip()]
    assert len(raw_rows) == 40
    repo_path = ROOT/'pr-analyzer-maven-skip-bad-repos/repos.txt'
    pr_path = ROOT/'pr-analyzer-maven-skip-bad-repos/pr_dataset_output.csv'
    repos = [line.strip() for line in repo_path.read_text().splitlines()
             if line.strip() and not line.lstrip().startswith('#')]
    expected = {repo.lower(): repo for repo in repos}
    pr_records = list(csv.reader(io.StringIO(pr_path.read_text(encoding='utf-8-sig'))))
    header_i = next(i for i,row in enumerate(pr_records) if row and row[0]=='Repo')
    pr_repos = {row[0].lower() for row in pr_records[header_i+1:] if row and row[0]}
    assert len(repos) == len(expected) == 38 and set(expected) == pr_repos
    rows = {}
    excluded = []
    changes = []
    row_map = []
    detail = headers[5]
    metadata = ['Source CSV Record','Source Dataset SHA256','Identity Verification Status',
                'Coding Validation Status','Unresolved Coding Fields','Validation Notes',
                'Researcher Confirmed Corrections','Policy Evidence Record']
    for record,original in raw_rows:
        row = dict(original)
        old_repo = row['Repository URL'].strip().removeprefix('https://github.com/').strip('/').lower()
        if old_repo in ['mesa/mesa','stacdev/stac']:
            excluded.append({'source_csv_record':record,'source_repo':row['Repo'],
                             'canonical_repo':old_repo,'reason':'Absent from final repos.txt and PR dataset; researcher requires exactly final 38 repositories',
                             'recorded_git_exclusion_commit':'44c2808' if old_repo=='mesa/mesa' else '9f8d541'})
            continue
        canonical = old_repo
        if old_repo == 'cncf/landscape':
            assert row['Repo'] == 'CloudNativePg Landscape'
            assert row['Policy URL'].strip() == 'https://github.com/cloudnative-pg/governance/blob/main/AI_POLICY.md'
            canonical = 'cloudnative-pg/cloudnative-pg'
        assert canonical in expected and canonical not in rows, canonical
        row['Repo'] = expected[canonical]
        row['Repository URL'] = 'https://github.com/'+expected[canonical]
        row['Policy URL'] = row['Policy URL'].strip()
        if canonical == 'curl/curl':
            assert row[detail] == '0'
            row[detail] = '1'
        for field in headers:
            if original[field] != row[field]:
                if canonical=='curl/curl' and field==detail:
                    reason = 'Researcher-confirmed correction of earlier coding/data-entry error; final report uses Level 1; no general 0-to-1 rule'
                    authority = 'Researcher confirmation, 2026-10-20'
                elif canonical=='cloudnative-pg/cloudnative-pg' and field in ['Repo','Repository URL']:
                    reason = 'Repository identity corrected after verifying target contributing-guide -> governance guidelines -> AI policy chain'
                    authority = EVIDENCE
                elif field=='Repo':
                    reason = 'Standardize display name to exact final repos.txt identifier; coding values unchanged'
                    authority = 'repos.txt and PR dataset membership'
                elif field=='Repository URL':
                    reason = 'Canonical URL from verified final repository identity; trim surrounding whitespace/trailing slash'
                    authority = 'repos.txt and PR dataset membership'
                elif field=='Policy URL':
                    reason = 'Trim surrounding whitespace only; preserve policy source URL'
                    authority = 'Original cell value'
                else:
                    raise AssertionError('Unapproved coding transformation: '+field)
                changes.append({'source_csv_record':record,'repo':row['Repo'],'column':field,
                                'original':original[field],'final':row[field],'reason':reason,'authority':authority})
        row.update({'Source CSV Record':str(record),'Source Dataset SHA256':EXPECTED_RAW_SHA,
                    'Identity Verification Status':'matched_final_repository_identifiers',
                    'Coding Validation Status':'carried_forward_not_independently_revalidated',
                    'Unresolved Coding Fields':'All carried-forward governance codes; historical policy applicability',
                    'Validation Notes':'Final sample membership checked; original codes preserved except the explicit Curl correction. Existing evidence fields may be incomplete.',
                    'Researcher Confirmed Corrections':'','Policy Evidence Record':''})
        if canonical=='cloudnative-pg/cloudnative-pg':
            row['Identity Verification Status']='verified_current_official_policy_chain'
            row['Coding Validation Status']='current_policy_conflicts_flagged_not_fully_validated'
            row['Unresolved Coding Fields']='Attribution; AI-use constraints; Disclosure in commit required; Stated consequence; conditional PR requirement interpretation; remaining codes and historical applicability'
            row['Validation Notes']='Current official policy chain supports CloudNativePG identity. Several stored codes may conflict with current policy; preserve values pending review, not an unverified recoding.'
            row['Policy Evidence Record']=EVIDENCE
        if canonical=='curl/curl':
            row['Coding Validation Status']='detail_researcher_confirmed_other_codes_not_revalidated'
            row['Researcher Confirmed Corrections']='Disclosure detail 0 -> 1; earlier coding/data-entry error; researcher confirmation 2026-10-20'
            row['Policy Evidence Record']=EVIDENCE
            row['Validation Notes']='Retain official curl policy source; confirmed Level 1 is repository governance coding, not proof of mandatory disclosure for every PR. Other codes and historical applicability remain unvalidated.'
        rows[canonical]=row
        row_map.append({'source_csv_record':record,'original_repo':original['Repo'],
                        'original_repository_url':original['Repository URL'],'final_repo':row['Repo']})
    assert len(excluded)==2 and len(rows)==38 and set(rows)==set(expected)
    stream = io.StringIO(newline='')
    writer = csv.DictWriter(stream,fieldnames=headers+metadata,lineterminator='\n')
    writer.writeheader()
    writer.writerows(rows[repo.lower()] for repo in repos)
    final_bytes = stream.getvalue().encode('utf-8')
    create_or_verify(FINAL,final_bytes)
    ledger = {'schema_version':1,'prepared_date':'2026-10-20','timezone':'Pacific/Auckland',
              'authority':'Researcher instructions in this session, 2026-10-20',
              'dataset_role':'Authoritative final-38 compendium analysis view; NOT a claim of full coding validation or original historical execution bytes',
              'original_external_source':str(SOURCE),'historical_raw_copy':str(RAW.relative_to(ROOT)),
              'historical_raw_sha256':EXPECTED_RAW_SHA,'historical_raw_populated_repositories':40,
              'final_file':str(FINAL.relative_to(ROOT)),'final_sha256':digest(final_bytes),'final_repositories':38,
              'matching_inputs':{str(repo_path.relative_to(ROOT)):digest(repo_path.read_bytes()),str(pr_path.relative_to(ROOT)):digest(pr_path.read_bytes())},
              'structural_transformations':['Retain first 24 named governance columns; exclude right-hand summaries, empty header columns and footer from final analysis view',
                                            'Keep original full CSV bytes in historical/raw-data location',
                                            'Order final rows exactly as final repos.txt',
                                            'Append provenance/validation metadata without inventing missing governance values',
                                            'Serialize derived CSV as UTF-8 with LF line endings; source raw bytes unchanged'],
              'exclusions':excluded,'row_identity_map':row_map,'field_transformations':changes,
              'policy_evidence_record':EVIDENCE,'historical_policy_applicability_verified':False,
              'fully_validated':False,'curl_correction':{'repo':'curl/curl','column':detail,'original':'0','final':'1',
                  'reason':'Correction of earlier coding/data-entry error; researcher confirms Level 1 used in final report',
                  'authority':'Researcher confirmation 2026-10-20','general_recode_rule':False,
                  'policy_source':'https://curl.se/dev/contribute.html#on-ai-use-in-curl'},
              'cloudnativepg_identity':{'original_name':'CloudNativePg Landscape','original_repository_url':'https://github.com/cncf/landscape',
                   'final_repo':'cloudnative-pg/cloudnative-pg','status':'Current official policy chain verified; coding conflicts flagged; no automatic governance recoding'}}
    create_or_verify(LEDGER,(json.dumps(ledger,indent=2,ensure_ascii=False)+'\n').encode('utf-8'))
    print(json.dumps({'raw_sha256':EXPECTED_RAW_SHA,'final_sha256':digest(final_bytes),
                      'final_rows':38,'excluded_rows':len(excluded),'field_changes':len(changes),
                      'fully_validated':False},indent=2))


if __name__=='__main__':
    main()
