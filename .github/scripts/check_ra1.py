from decimal import Decimal
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import tempfile
import time


def wait_until(condition, process, log_path, timeout=35):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise AssertionError(log_path.read_text(encoding='utf-8', errors='replace'))
        result = condition()
        if result:
            return result
        time.sleep(0.25)
    raise AssertionError('Timeout\n' + log_path.read_text(encoding='utf-8', errors='replace'))


def stop(process):
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def check_case(project, jar, folder, explicit):
    folder.mkdir(parents=True)
    data = folder / 'Tienda con espacios 上海 España'
    env = os.environ.copy()
    for key in ('AADTEX_BASE_DIR', 'AADTEX_DEMO_ENABLED', 'SPRING_APPLICATION_JSON'):
        env.pop(key, None)
    java = str(Path(env['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if env.get('JAVA_HOME') else 'java'
    command = [java, '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-jar', str(jar)]
    if explicit:
        env['AADTEX_BASE_DIR'] = str(data)
    else:
        default = Path('C:/workspace/store_data') if os.name == 'nt' else Path('/workspace/store_data')
        candidates = [default, folder / 'store_data']
    log_path = folder / 'run.log'
    with log_path.open('wb') as log:
        process = subprocess.Popen(command, cwd=folder, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            if not explicit:
                data = wait_until(lambda: next((p for p in candidates if (p / '.demo-generated').exists()), None), process, log_path)
            bucket = data / 'output/bucket/aadtex-daily-reports/2026/09'
            reports = lambda: list(bucket.glob('summary_batch_*.json'))
            wait_until(lambda: len(reports()) == 4, process, log_path)
            summaries = [json.loads(p.read_text(encoding='utf-8'), parse_float=Decimal) for p in reports()]
            assert sum(r['transactions'] for r in summaries) == 9
            assert sum(r['totalSales'] for r in summaries) == Decimal('224852.35')
            assert any('Zara 上海南京东路店' in r['storeNames'] for r in summaries)
            assert any('Zara Gran Vía Madrid' in r['storeNames'] for r in summaries)
            assert len(list((data / 'output/processed/signatures').glob('*.jpg'))) == 4
            assert (data / 'output/error/daily_sales_broken_store.csv').stat().st_size == 0
            assert not list((data / 'input').iterdir())
            for r in summaries:
                assert (data / r['signature']).is_file()
            assert not list((data / 'output/processed').rglob('daily_sales_financial.csv'))
            shutil.copyfile(project / 'src/main/resources/demo/input/batch_usa.zip', data / 'input/batch_usa.zip')
            wait_until(lambda: len(reports()) == 5, process, log_path)
        finally:
            stop(process)
    with (folder / 'restart.log').open('wb') as log:
        process = subprocess.Popen(command, cwd=folder, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            time.sleep(7)
            assert process.poll() is None
            assert len(reports()) == 5
            assert not list((data / 'input').iterdir())
        finally:
            stop(process)
    print('OK: ' + ('configured path, spaces and UTF-8' if explicit else 'default path or local fallback') + '; totals; quarantine; live batch; restart')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--skip-default', action='store_true')
    parser.add_argument('--project', type=Path)
    args = parser.parse_args()
    project = (args.project or Path(__file__).resolve().parents[2] / 'actividades/ra1_act1').resolve()
    jar = project / 'target/ra1-act1-1.0.0.jar'
    assert jar.is_file(), 'Compile first with Maven'
    parent = os.environ.get('AADTEX_TEST_TMP')
    with tempfile.TemporaryDirectory(prefix='ra1 portability ', dir=parent) as temp:
        root = Path(temp)
        if not args.skip_default:
            check_case(project, jar, root / 'default', explicit=False)
        check_case(project, jar, root / 'custom', explicit=True)
