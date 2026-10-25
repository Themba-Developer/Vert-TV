"""Include dependency notices in the distributed APK; do not print environment credentials."""
import json,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
data=json.loads(subprocess.check_output(['cargo','metadata','--locked','--format-version','1'],cwd=root/'native/adblock_bridge'))
sections=['Rust dependency notices for the Vert TV native ad blocker.\n']
for package in sorted(data['packages'],key=lambda p:p['name']):
    folder=Path(package['manifest_path']).parent
    if package['name']=='vert-adblock-bridge':continue
    parts=[package['name']+' '+package['version'],'Licence: '+str(package.get('license') or ''),'Source: '+str(package.get('repository') or 'https://crates.io/crates/'+package['name']+'/'+package['version'])]
    files=set(folder.glob('LICENSE*'))|set(folder.glob('COPYING*'))|set(folder.glob('LICENCE*'))
    if package.get('license_file'):files.add(folder/package['license_file'])
    for file in sorted(files):
        if file.is_file():parts.extend([file.name,file.read_text(errors='replace')])
    sections.append('\n'.join(parts))
(root/'app/src/main/assets/rust-dependency-licenses.txt').write_text('\n\n'+'\n\n----------------\n\n'.join(sections))
