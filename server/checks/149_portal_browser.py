"""Rendered production templates in Chromium, mocked API only; no live service."""
import json
from pathlib import Path
from urllib.parse import urlsplit, parse_qs
from playwright.sync_api import sync_playwright, expect

root=Path('/tmp/portal-rendered')
pages={'/site':'v1_worksite.html','/pictures':'v1_portal_pictures.html','/settings':'v1_portal_home.html','/':'v1_public_home.html','/account':'v1_portal_account.html'}
permissions={key:True for key in ['worksite.structure.edit','members.manage','files.view','defect.create','acceptance.create','btb.create']}
reports=[{'id':'b1','work_date':'2026-10-06','status':'FINALIZED'},{'id':'b2','work_date':'2026-10-07','status':'ACTIVE'}]
photo={'attachment_id':'p1','filename':'Foto.jpg','entity_type':'BTB','source':'ANDROID','captured_at':'2026-10-06T12:00:00Z','renditions':{'thumb':'/thumb','preview':'/preview'}}
requests=[]
def handle(route):
    path=urlsplit(route.request.url).path
    if path in pages:
        route.fulfill(content_type='text/html',body=(root/pages[path]).read_text());return
    if path in ['/thumb','/preview']:
        route.fulfill(content_type='image/svg+xml',body='<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="blue"/></svg>');return
    requests.append(path)
    if '/sync/events' in path:
        route.fulfill(content_type='text/event-stream',body=': test\n\n');return
    if path=='/api/v1/worksites': data={'worksites':[{'id':'site1','name':'Testbaustelle'}]}
    elif path.endswith('/nodes'):data={'nodes':[]}
    elif path.endswith('/teams'):data={'teams':[{'id':'team1','name':'Team','member_count':1}]}
    elif path.endswith('/btbs'):data={'btbs':reports}
    elif path.endswith('/media/page'):
        query=parse_qs(urlsplit(route.request.url).query)
        size=int(query.get('limit',['60'])[0]);assert size in (12,60)
        offset=int(query.get('cursor',['0'])[0])
        term=query.get('search',[''])[0]
        indices=[] if term=='no-match' else ([9999] if term=='last-photo' else range(offset,min(offset+size,10000)))
        items=[{**photo,'attachment_id':'p'+str(i),'filename':'Foto_'+str(i)+'.jpg'} for i in indices]
        data={'media':items,'next_cursor':str(offset+60) if not term and offset+60<10000 else None}
    elif path.endswith('/media'):data={'media':[photo,photo]}
    elif path.endswith('/members'):data={'members':[{'user_id':'helper','display_name':'Helfer','worksite_role':'HELPER','permission_profile':'HELPER_STANDARD'}]}
    elif path.endswith('/invite-candidates'):data={'candidates':[]}
    elif path.endswith('/invitations'):data={'invitations':[]}
    elif path.endswith('/defects'):data={'defects':[{'id':'defect1','title':'Testmangel','status':'OPEN','version':1,'payload':{}}]}
    elif path.endswith('/acceptances'):data={'acceptances':[]}
    elif path.endswith('/email/status'):data={'verified':False}
    elif path.endswith('/directory'):data={'users':[]}
    else:data={'worksite':{'name':'Testbaustelle'},'permissions':permissions}
    route.fulfill(content_type='application/json',body=json.dumps(data))

with sync_playwright() as p:
    browser=p.chromium.launch()
    for width in [1440,390]:
        context=browser.new_context(viewport={'width':width,'height':1000})
        context.route('**/*',handle)
        page=context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
        requests.clear()
        page.goto('https://portal.test/site');expect(page.locator('#title')).to_have_text('Testbaustelle')
        expect(page.locator('[data-panel="overview"]')).to_have_attribute('aria-current','page')
        page.locator('[data-panel="reports"]').click();expect(page.locator('.report-row:visible')).to_have_count(2)
        page.locator('#reportStatus').select_option('FINALIZED');expect(page.locator('.report-row:visible')).to_have_count(1)
        expect(page.locator('.report-row:visible .pill')).to_have_text('Abgeschlossen')
        page.locator('[data-panel="structure"]').click();expect(page.locator('#nodeForm')).not_to_be_visible()
        page.get_by_text('Unterbaustelle anlegen',exact=True).click();expect(page.locator('#nodeForm')).to_be_visible()
        page.locator('[data-panel="team"]').click();expect(page.get_by_text('Aus Baustellenteam entfernen',exact=True)).to_be_visible()
        page.get_by_text('Person einladen',exact=True).click();page.locator('#inviteAreaMode').select_option('limited');expect(page.locator('#inviteKmFrom')).to_be_visible()
        expect(page.locator('textarea.json:visible')).to_have_count(0)
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'),'Horizontal overflow in worksite'
        assert not any(r.endswith('/media') or r.endswith('/media/page') for r in requests),'Worksite loaded media eagerly'
        page.locator('[data-panel="issues"]').click()
        page.get_by_text('Fotos anzeigen',exact=True).click()
        expect(page.locator('.fach-images img')).to_have_count(12)
        expect(page.get_by_text('Alle zugehörigen Fotos',exact=True)).to_have_attribute('href','/portal/v1/pictures?worksite=site1&kind=DEFECT&parent_id=defect1')
        page.screenshot(path=str(root/f'worksite-{width}.png'),full_page=True)
        page.goto('https://portal.test/pictures?worksite=site1');expect(page.locator('#gallery figure')).to_have_count(60)
        expect(page.locator('#gallery a').first).to_have_attribute('href','/preview')
        expect(page.locator('#gallery img').first).to_have_attribute('loading','lazy')
        expect(page.locator('#previousPictures')).to_be_disabled()
        page.locator('#nextPictures').click();expect(page.locator('#pageNumber')).to_have_text('Seite 2')
        expect(page.locator('#gallery figure')).to_have_count(60)
        expect(page.locator('#gallery figcaption').first).to_contain_text('Foto_60.jpg')
        page.locator('#previousPictures').click();expect(page.locator('#pageNumber')).to_have_text('Seite 1')
        count_before=len([r for r in requests if r.endswith('/media/page')])
        page.evaluate("mediaChanged({worksite_id:'site1',entity_type:'FILE'})")
        expect(page.locator('#updates')).to_be_visible()
        assert len([r for r in requests if r.endswith('/media/page')])==count_before,'Live change reloaded gallery'
        expect(page.locator('#gallery figure')).to_have_count(60)
        page.locator('#search').fill('no-match');expect(page.locator('#gallery figure')).to_have_count(0)
        expect(page.locator('#nextPictures')).to_be_disabled()
        page.locator('#search').fill('last-photo');expect(page.locator('#gallery figure')).to_have_count(1)
        expect(page.locator('#gallery figcaption')).to_contain_text('Foto_9999.jpg')
        assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'),'Horizontal overflow in gallery'
        page.screenshot(path=str(root/f'gallery-{width}.png'),full_page=True)
        page.goto('https://portal.test/settings');expect(page.locator('#emailState')).to_contain_text('bestätigt')
        page.goto('https://portal.test/');expect(page.get_by_text('Baustellen insgesamt',exact=False)).to_be_visible()
        page.screenshot(path=str(root/f'public-{width}.png'),full_page=True)
        assert not errors,errors
        context.close()
    browser.close()
assert not any('/content' in path for path in requests),'Original image was fetched automatically'
print('PASS: Chromium desktop/mobile tabs, report filters, collapsed forms, invitation scope, gallery dedup/filter/preview, settings, public page, no original fetch')
