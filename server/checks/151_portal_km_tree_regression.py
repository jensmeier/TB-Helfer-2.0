"""Focused local checks of production KM parsing and tree rendering; no services required."""
import ast
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / "tbserver/media_gallery.py").read_text()
module = ast.parse(source)
parser = next(n for n in module.body if isinstance(n, ast.FunctionDef) and n.name == "_search_spec")
namespace = {"re": re}
exec(compile(ast.Module(body=[parser], type_ignores=[]), str(root / "tbserver/media_gallery.py"), "exec"), namespace)
parse = namespace["_search_spec"]
for value in ("13.000", "13,000", "KM 13.000", "13"):
    terms, low, high = parse(value)
    assert (terms, low, high) == ([], 12000, 14000), (value, low, high)
    candidates = [11999, 12000, 13000, 14000, 14001]
    assert [m for m in candidates if low <= m <= high] == [12000, 13000, 14000]
for value in ("13.200", "13,200", "km 13.200"):
    terms, low, high = parse(value)
    assert (terms, low, high) == ([], 13100, 13299)
    assert [m for m in [13099, 13100, 13200, 13299, 13300] if low <= m <= high] == [13100, 13200, 13299]
assert parse("0.000") == ([], -1000, 1000)
assert parse("-13.000") == ([], -14000, -12000)
assert parse("strail defekt") == (["strail", "defekt"], None, None)

javascript = r"""
const assert=require('node:assert/strict'),fs=require('node:fs');
const source=fs.readFileSync(process.argv[1],'utf8');
const labels=fs.readFileSync(require('node:path').join(require('node:path').dirname(process.argv[1]),'v1_node_labels.html'),'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
eval(labels);
const begin=source.indexOf('function nodeTreeRows('),end=source.indexOf('async function editNode(',begin);
assert(begin>=0&&end>begin);
const production=source.slice(begin,end);
eval(production+`
(async()=>{
  const nodes=[
    {id:'child',parent_id:'v2',name:'Anbindung an Bestandsschacht',node_kind:'ALLGEMEIN_OBJEKT'},
    {id:'root',parent_id:null,name:'Gleisquerung 45.021',node_kind:'ALLGEMEIN_OBJEKT'},
    {id:'v1',parent_id:'root',name:'SchachtV1',node_kind:'ALLGEMEIN_OBJEKT'},
    {id:'v2',parent_id:'root',name:'SchachtV2',node_kind:'ALLGEMEIN_OBJEKT'}
  ];
  const original=JSON.stringify(nodes);
  assert.deepEqual(nodeTreeRows(nodes).map(r=>[r.node.id,r.depth]),[['root',0],['v1',1],['v2',1],['child',2]]);
  assert.equal(JSON.stringify(nodes),original,'Rendering must not rewrite parent relationships');
  let nodeData=[],optionsCalls=0,requests=[];
  const worksiteId='site',target={innerHTML:''};
  const document={getElementById(id){assert.equal(id,'nodes');return target;}};
  const tbApi=async url=>{requests.push(url);return {data:{nodes}};};
  const can=()=>false,esc=value=>String(value),nodeTypeLabel=value=>value;
  const nodeOptions=()=>optionsCalls++;
  eval(production);
  await loadNodes();
  assert.deepEqual(nodeData.map(n=>n.id),['root','v1','v2','child']);
  assert(target.innerHTML.indexOf('SchachtV2')<target.innerHTML.indexOf('Anbindung an Bestandsschacht'));
  assert(target.innerHTML.includes('margin-left:36px'));
  assert.deepEqual(requests,['/api/v1/worksites/site/nodes']);
  assert.equal(optionsCalls,1);
  const malformed=[{id:'a',parent_id:'b'},{id:'b',parent_id:'a'},{id:'orphan',parent_id:'missing'},{id:'self',parent_id:'self'}];
  assert.equal(new Set(nodeTreeRows(malformed).map(r=>r.node.id)).size,4);
  const deep=Array.from({length:5000},(_,i)=>({id:String(i),parent_id:i?String(i-1):null})).reverse();
  assert.equal(nodeTreeRows(deep).length,5000);
  assert.deepEqual(nodeTreeRows([]),[]);
})().catch(error=>{console.error(error);process.exitCode=1;});
`);
"""
subprocess.run(["node", "-e", javascript, str(root / "templates/v1_worksite.html")], check=True)
print("PASS: production KM parser inclusive boundaries, unchanged fractional search, screenshot tree order/rendering, no mutations, orphan/cycle/deep-tree safety")
