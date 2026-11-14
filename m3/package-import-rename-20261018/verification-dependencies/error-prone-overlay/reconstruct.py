#!/usr/bin/env python3
"""Reconstruct only the hash-admitted Error Prone receiver from a public upstream JAR."""
import argparse,hashlib,json,pathlib,re,zipfile
p=argparse.ArgumentParser()
p.add_argument('--original-jar',required=True);p.add_argument('--original-pom',required=True)
p.add_argument('--overlay',required=True);p.add_argument('--output',required=True)
a=p.parse_args(); root=pathlib.Path(a.overlay); manifest=json.loads((root/'manifest.json').read_text())
if manifest['receiver_version'] != '2.50.0-m3-guava-namespace-20261018' or len(manifest['entries']) != 8:
 raise SystemExit('Unexpected receiver identity or owned entry count')
sha=lambda b:hashlib.sha256(b).hexdigest()
def admit(path,expected):
 data=path.read_bytes()
 if sha(data)!=expected:raise SystemExit('Hash admission refused: '+str(path))
 return data
original=pathlib.Path(a.original_jar);pom=pathlib.Path(a.original_pom)
admit(original,manifest['original_jar_sha256']);admit(pom,manifest['original_pom_sha256'])
admit(root/'classes.zip',manifest['overlay_sha256']);admit(pathlib.Path(__file__),manifest['reconstructor_sha256'])
admit(root/'COPYING',manifest['license_sha256'])
out=pathlib.Path(a.output);out.mkdir(parents=True,exist_ok=True)
jar=out/('error_prone_core-'+manifest['receiver_version']+'.jar')
if jar.exists() or jar.with_suffix('.pom').exists():raise SystemExit('Require fresh receiver output')
with zipfile.ZipFile(root/'classes.zip') as z:
 if len(z.namelist())!=len(set(z.namelist())):raise SystemExit('Duplicate overlay entry')
 replacements={}
 for name in z.namelist():
  if not name.startswith('com/google/errorprone/bugpatterns/threadsafety/') or not name.endswith('.class') or name.rsplit('/',1)[-1][:-6].split('$',1)[0] not in ('WellKnownMutability','HeldLockAnalyzer'):raise SystemExit('Unowned overlay entry '+name)
  data=z.read(name)
  if sha(data)!=manifest['entries'].get(name):raise SystemExit('Overlay entry hash drift')
  replacements[name]=data
 if set(replacements)!=set(manifest['entries']):raise SystemExit('Missing overlay entry')
with zipfile.ZipFile(original) as src,zipfile.ZipFile(jar,'w') as dst:
 for item in src.infolist():dst.writestr(item,replacements.pop(item.filename,src.read(item.filename)))
 for name,data in sorted(replacements.items()):
  info=zipfile.ZipInfo(name,(2026,10,18,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;dst.writestr(info,data)
text=pom.read_text();needle='<artifactId>error_prone_core</artifactId>'
if text.count(needle)!=1:raise SystemExit('Ambiguous upstream POM owner')
text=text.replace(needle,needle+'\n  <version>'+manifest['receiver_version']+'</version>',1).replace('${project.version}','2.50.0')
receiver_pom=jar.with_suffix('.pom');receiver_pom.write_text(text)
admit(jar,manifest['receiver_jar_sha256']);admit(receiver_pom,manifest['receiver_pom_sha256'])
(out/'RECONSTRUCTION_RECEIPT.json').write_text(json.dumps({'original_jar_sha256':manifest['original_jar_sha256'],'overlay_sha256':manifest['overlay_sha256'],'receiver_jar_sha256':sha(jar.read_bytes()),'receiver_pom_sha256':sha(receiver_pom.read_bytes()),'owned_class_entries':len(manifest['entries']),'canonical_owner':manifest['canonical_owner'],'status':'PASS_EXACT_QUALIFIED_ARTIFACT'},indent=2)+'\n')
print(jar);print(receiver_pom)
