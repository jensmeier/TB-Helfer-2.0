"""Production monitor alert logic with captured notifications; sends no mail."""
import ast, unittest
from pathlib import Path
from typing import Any
class Alerts(unittest.TestCase):
    def setUp(self):
        self.sent=[];self.events=[];self.old={}
        names={'_record_public_check','_transition_code','_event_body'}
        tree=ast.parse((Path(__file__).parents[1]/'tbserver/monitor.py').read_text())
        tree.body=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in names]
        self.env=dict(Any=Any,_now_iso=lambda:'now',_append_event=lambda *a,**k:self.events.append((a,k)),
                      _send_alert=lambda *a,**k:self.sent.append(k))
        exec(compile(tree,'monitor','exec'),self.env)
    def step(self,status):
        current={'status':status}
        self.env['_record_public_check'](current,self.old,{'api':{'status':'OK','latency_ms':12},'database':{'status':'OK','latency_ms':8}},None)
        self.old=current
    def test_short_errors_no_mail_and_visible(self):
        for s in ['ERROR','ERROR','OK']:self.step(s)
        self.assertEqual(self.sent,[]);self.assertEqual(len(self.events),2)
    def test_third_error_and_single_recovery(self):
        for s in ['ERROR']*5:self.step(s)
        self.assertEqual(len(self.sent),1)
        self.assertIn('Interne API: OK',self.sent[0]['body']);self.assertIn('Datenbank: OK',self.sent[0]['body'])
        for s in ['WARN','OK','OK']:self.step(s)
        self.assertEqual(len(self.sent),2);self.assertTrue(self.sent[-1]['recovery'])
    def test_nonerror_resets_consecutive_counter(self):
        for s in ['ERROR','ERROR','WARN','ERROR','ERROR']:self.step(s)
        self.assertEqual(self.sent,[])
        self.step('ERROR');self.assertEqual(len(self.sent),1)
    def test_persisted_counter_survives_reload(self):
        self.old={'status':'ERROR','consecutive_errors':2,'incident_confirmed':False}
        self.step('ERROR');self.assertEqual(len(self.sent),1)
    def test_legacy_error_starts_fresh(self):
        self.old={'status':'ERROR'};self.step('ERROR');self.assertEqual(self.sent,[])
    def test_new_incident_after_recovery(self):
        for s in ['ERROR']*3+['OK']+['ERROR']*3:self.step(s)
        self.assertEqual(len(self.sent),3)
if __name__=='__main__':unittest.main()
