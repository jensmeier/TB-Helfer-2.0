"""CI-only export of the disposable built test image; never a runtime/NAS task."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tbserver.system_backup import export_system, DockerReadClient

if os.getenv('CI') != 'true':
    raise RuntimeError('CI_ONLY_SYSTEM_EXPORT_GATE')
with tempfile.TemporaryDirectory(prefix='tb-system-gate-') as temp:
    root = Path(temp)
    source = Path(__file__).resolve().parents[1]
    client = DockerReadClient()
    before = client.inspect('tb-scoped-gate')
    archive = export_system(str(uuid4()), root, source, ['tb-scoped-gate'], client)
    with tarfile.open(archive) as bundle:
        manifest = json.load(bundle.extractfile('manifest.json'))
        assert manifest['images']['tb-scoped-gate'] == before
        with (root / 'images.tar').open('wb') as out:
            import shutil
            shutil.copyfileobj(bundle.extractfile('images.tar'), out)
    # Roundtrip the same disposable image, with tags preserved.
    subprocess.run(['docker','image','load','--input',str(root/'images.tar')],check=True,stdout=subprocess.DEVNULL)
    assert client.inspect('tb-scoped-gate') == before
    print('PASS: real Docker GET image save, verified source archive and image load roundtrip; no business data/full backup')
