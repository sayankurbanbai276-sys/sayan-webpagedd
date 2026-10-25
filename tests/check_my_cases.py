"""Semantic comparison of tests/my_cases.jsonl results with the hand-justified expectations.

Usage (repository root):
  python tools/run.py --input tests/my_cases.jsonl > tests/my_cases_actual.jsonl
  python tests/check_my_cases.py
"""
import json
import pathlib

HERE = pathlib.Path(__file__).resolve().parent


def load(name):
    return [json.loads(line) for line in (HERE / name).read_text(encoding='utf-8').splitlines() if line.strip()]


def main():
    cases, expected, actual = load('my_cases.jsonl'), load('my_cases_expected.jsonl'), load('my_cases_actual.jsonl')
    assert len(cases) == len(expected) == len(actual), 'line counts differ'
    for i, (req, exp, got) in enumerate(zip(cases, expected, actual), 1):
        t = req['task']
        if t == 'scc':
            comp = got['component']
            assert comp[0] == comp[1] == comp[2] and comp[3] == comp[4]
            assert len({comp[0], comp[3], comp[5]}) == 3
            assert got['condensation'] == [[comp[0], comp[3]]], got
        elif t in ('kruskal', 'prim'):
            assert got['cost'] == exp['cost'] and got['components'] == exp['components'], got
            assert sorted(got['edge_ids']) == sorted(exp['edge_ids']), got
        else:
            assert got == exp, (i, got, exp)
        print(f'PASS case {i}: {t}')


if __name__ == '__main__':
    main()
