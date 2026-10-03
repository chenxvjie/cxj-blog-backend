#!/usr/bin/env python3
"""Save dedicated COS credentials privately; does not restart containers."""
import datetime
import getpass
import os
import pathlib
import re
import secrets
import subprocess
import tempfile

def main():
    if os.geteuid() != 0:
        raise SystemExit('Run with sudo python3 configure-cos.py')
    os.umask(0o077)
    folder = pathlib.Path('/opt/cxj-blog/deploy')
    target = folder / '.env'
    original = target.read_text(encoding='utf-8')
    print('Configure dedicated cxj-blog-cos credentials; never enter SES credentials here.')
    secret_id = getpass.getpass('COS SecretId (hidden): ').strip()
    secret_key = getpass.getpass('COS SecretKey (hidden): ').strip()
    if not re.fullmatch(r'[A-Za-z0-9]+', secret_id) or not re.fullmatch(r'[A-Za-z0-9]+', secret_key):
        raise SystemExit('Invalid credentials; no files changed.')
    values = dict(COS_ENABLED='true', COS_SECRET_ID=secret_id, COS_SECRET_KEY=secret_key,
                  COS_REGION='ap-nanjing', COS_BUCKET='cxj-blog-images-1317285711',
                  COS_PUBLIC_BASE_URL='https://img.chenxujie-bolg.cn')
    kept = []
    for line in original.splitlines():
        match = re.match(r'^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$', line)
        if match and match[1] in values:
            old = match[2].strip()
            if old.startswith(("'", '"')) and not old.endswith(old[0]):
                raise SystemExit('Existing multiline setting needs manual editing; no files changed.')
            continue
        kept.append(line)
    candidate = '\n'.join(kept) + '\n' + '\n'.join(f"{key}='{value}'" for key,value in values.items()) + '\n'
    descriptor,name = tempfile.mkstemp(prefix='.cos-config-',dir=folder)
    try:
        with os.fdopen(descriptor,'w',encoding='utf-8') as stream: stream.write(candidate)
        command = ['docker','compose','--project-directory',str(folder),'--env-file',name]
        for filename in ('docker-compose.prod.yml','docker-compose.flow.yml','docker-compose.frontend.flow.yml'):
            if (folder/filename).exists(): command += ['-f',str(folder/filename)]
        result = subprocess.run(command + ['config','--quiet'],capture_output=True)
        if result.returncode:
            raise SystemExit('Compose validation failed; original .env retained. Output suppressed to protect values.')
        backup = folder / ('.env.before-cos-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + secrets.token_hex(3))
        with backup.open('x',encoding='utf-8') as stream: stream.write(original)
        os.replace(name,target)
        print('COS_CONFIG_SAVED; protected .env backup created. No containers restarted.')
        print('Run the updated preflight-release.sh. Never share .env or backup contents.')
    finally:
        if os.path.exists(name): os.unlink(name)

if __name__ == '__main__': main()
