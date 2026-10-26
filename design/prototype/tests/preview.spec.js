import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import palette from '../tokens.js';

const nav = (page, name) => page.getByRole('navigation', {name:'Main navigation'}).getByRole('button', {name, exact:true});
test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', {name:'A little clarity. A little more room.'})).toBeVisible();
});

test('first visit respects refusal and explains the source without granting access', async ({page}) => {
  await page.getByLabel('Experience', {exact:true}).selectOption('fresh');
  const choose = page.getByRole('button', {name:'Choose what to observe'});
  const skip = page.getByRole('button', {name:'Continue without tracking'});
  await expect(choose).toHaveClass('secondary');
  await expect(skip).toHaveClass('secondary');
  await choose.click();
  await page.getByLabel('App usage · demo selection').check();
  await page.getByRole('button', {name:'Review selected source'}).click();
  await expect(page.getByRole('dialog')).toContainText('This preview does none of those things');
  await page.getByRole('button', {name:'Finish explanation · keep tracking off'}).click();
  await expect(page.getByRole('status')).toContainText('No source or permission was enabled');
});

test('skipping onboarding reaches settings and keeps tracking off', async ({page}) => {
  await page.getByLabel('Experience', {exact:true}).selectOption('fresh');
  await page.getByRole('button', {name:'Continue without tracking'}).click();
  await expect(page.locator('h1')).toHaveText('Quietly, your way.');
  await nav(page,'History').click();
  await expect(page.getByRole('heading', {name:'Nothing is being tracked.'})).toBeVisible();
});

test('coverage loss never appears as zero or an unsupported positive insight', async ({page}) => {
  await page.getByLabel('Experience', {exact:true}).selectOption('gap');
  await expect(page.locator('#screen')).toContainText('App usage access interrupted.');
  await expect(page.locator('#screen')).toContainText('reopening insight is withheld');
  await expect(page.locator('#screen')).not.toContainText('Social sample reopened 3 times');
  await nav(page,'Patterns').click();
  await expect(page.locator('#screen')).toContainText('This insight is withheld');
  await nav(page,'Protect').click();
  await expect(page.locator('#screen')).toContainText('Unavailable · access interrupted');
});

test('enabled empty source is distinct from a fully observed zero', async ({page}) => {
  await page.getByLabel('Experience', {exact:true}).selectOption('empty');
  await expect(page.locator('#screen')).toContainText('No observations yet. That is different');
  await nav(page,'Patterns').click();
  await expect(page.locator('#screen')).toContainText('There is nothing to infer yet');
});

test('chart table preserves unknown bins and app detail explains exposure limits', async ({page}) => {
  await page.getByRole('button', {name:'Read chart as a table'}).click();
  await expect(page.getByRole('table')).toContainText('Unknown · access gap');
  await expect(page.getByRole('button', {name:'Hide chart table'})).toBeFocused();
  const numbers = await page.getByRole('table').locator('td').allTextContents();
  expect(numbers.reduce((sum,s) => sum+(s.includes('observed') ? parseInt(s) : 0),0)).toBe(102);
  await page.getByRole('button', {name:/Social sample Social · 8 opens/}).click();
  await expect(page.locator('#screen')).toContainText('this is not unique videos or videos completed');
  await page.getByRole('button', {name:'How counting would work'}).click();
  await expect(page.getByRole('dialog')).toContainText('Repeated callbacks or redraws add none');
});

test('insight reveals evidence and offers an equally clear no-change choice', async ({page}) => {
  await page.getByRole('button', {name:'What this is based on'}).click();
  await expect(page.getByRole('dialog')).toContainText('Your intention, feelings');
  await expect(page.getByRole('dialog')).toContainText('The full 18:10–18:20 window');
  await expect(page.getByRole('button', {name:'No change for now'})).toHaveClass('secondary');
  await page.getByRole('button', {name:'No change for now'}).click();
  await expect(page.getByRole('button', {name:'What this is based on'})).toBeFocused();
});

test('rule draft does not apply until reviewed and explicitly saved', async ({page}) => {
  await nav(page,'Protect').click();
  await page.getByRole('button', {name:'Review rule and exceptions'}).click();
  await page.getByLabel('Daily app-time budget, in minutes').fill('20');
  await page.getByLabel('A pause prompt · immediate override').check();
  await page.getByRole('button', {name:'Review demo rule'}).click();
  await expect(page.getByRole('dialog')).toContainText('20 observed app minutes');
  await expect(page.getByRole('dialog')).toContainText('This demo rule only');
  await page.getByRole('button', {name:'Discard draft'}).click();
  await expect(page.locator('#screen')).toContainText('45 min a day · reminder');
  await page.getByRole('button', {name:'Review rule and exceptions'}).click();
  await page.getByLabel('Daily app-time budget, in minutes').fill('20');
  await page.getByRole('button', {name:'Review demo rule'}).click();
  await page.getByRole('button', {name:'Save demo configuration'}).click();
  await expect(page.locator('#screen')).toContainText('20 min a day · reminder');
  await expect(page.getByRole('status')).toContainText('No actual protection activated');
});

test('overlapping rules explain precedence before saving', async ({page}) => {
  await nav(page,'Protect').click();
  await page.getByRole('button', {name:'Add rule',exact:true}).click();
  await page.getByRole('button', {name:'Review demo rule'}).click();
  await expect(page.getByRole('dialog')).toContainText('Usage is counted once');
  await expect(page.getByRole('dialog')).toContainText('does not waive another rule');
  await page.getByRole('button', {name:'Save demo configuration'}).click();
  await expect(page.locator('#screen')).toContainText('Another social boundary');
});

test('focus timer uses elapsed time and emergency releases all rules immediately', async ({page}) => {
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.clock.install({time:new Date('2026-10-27T12:00:00Z')});
  await page.clock.pauseAt(new Date('2026-10-27T12:00:01Z'));
  // Register the app's interval under the controlled clock, not a preexisting
  // real interval. A paused clock removes wall-time races under CPU load.
  await page.reload();
  await nav(page,'Protect').click();
  await page.getByRole('button', {name:'15 min',exact:true}).click();
  await page.getByRole('button', {name:'Preview 15 minutes of focus'}).click();
  await expect(page.locator('#focus-time')).toHaveText('15:00');
  await page.clock.runFor('02:00');
  await expect(page.locator('#focus-time')).toHaveText('13:00');
  await page.getByRole('button', {name:'Emergency access · end now'}).click();
  await expect(page.locator('#focus-time')).toHaveCount(0);
  await expect(page.getByRole('button', {name:'Enable sample social rule'})).toHaveAttribute('aria-pressed','false');
  await expect(page.getByRole('status')).toContainText('released immediately');
  await expect(page.locator('#screen')).toContainText('All demo rules are paused');
});

test('interruption provides scoped override and unconditional emergency path', async ({page}) => {
  await page.locator('#preview-prompt').click();
  await expect(page.getByRole('dialog')).toContainText('this demo rule only');
  await expect(page.getByRole('button', {name:'Emergency access · release all now'})).toBeEnabled();
  await page.getByRole('button', {name:'Continue for 5 minutes'}).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByRole('status')).toContainText('Other rules would remain active');
});

test('modal Escape returns focus and keyboard stays inside an open dialog', async ({page}) => {
  await page.locator('#preview-prompt').click();
  for (let i=0; i<8; i++) {
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => document.querySelector('dialog').contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(page.locator('#preview-prompt')).toBeFocused();
});

test('reduced motion is effective for system preference and stronger in-app choice', async ({page}) => {
  await page.emulateMedia({reducedMotion:'reduce'});
  await nav(page,'History').click();
  await expect(page.locator('.page')).toHaveCSS('animation-name','none');
  await page.emulateMedia({reducedMotion:'no-preference'});
  await page.getByLabel('Reduce motion', {exact:true}).check();
  await nav(page,'Protect').click();
  await expect(page.locator('.page')).toHaveCSS('animation-name','none');
});

test('system theme changes live and no preferences or data persist on reload', async ({page}) => {
  await page.emulateMedia({colorScheme:'dark'});
  await page.getByLabel('Appearance', {exact:true}).selectOption('system');
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  await page.emulateMedia({colorScheme:'light'});
  await expect(page.locator('html')).toHaveAttribute('data-theme','light');
  expect(await page.evaluate(() => [localStorage.length,sessionStorage.length])).toEqual([0,0]);
  await page.reload();
  await expect(page.getByLabel('Appearance', {exact:true})).toHaveValue('light');
});

test('preview sends no external requests and does not expose repository files', async ({page,request}) => {
  const outgoing=[];
  page.on('request', req => { if (!req.url().startsWith('http://127.0.0.1:4179/')) outgoing.push(req.url()); });
  await page.reload();
  for (const route of ['History','Patterns','Protect','Settings']) await nav(page,route).click();
  await page.getByRole('button', {name:'Read',exact:true}).click();
  expect(outgoing).toEqual([]);
  expect((await request.get('/package.json')).status()).toBe(404);
  expect((await request.get('/../../.git/config')).status()).toBe(404);
});

for (const theme of ['light','dark']) {
  for (const route of ['Today','History','Patterns','Protect','Settings']) {
    test(`automated accessibility · ${theme} · ${route}`, async ({page}) => {
      await page.getByLabel('Appearance', {exact:true}).selectOption(theme);
      await nav(page,route).click();
      const results=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze();
      expect(results.violations).toEqual([]);
    });
  }
}
for (const scenario of ['fresh','gap','empty']) {
  test(`automated accessibility · ${scenario}`, async ({page}) => {
    await page.getByLabel('Experience', {exact:true}).selectOption(scenario);
    expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
  });
}
test('automated accessibility · rule form, preview and interruption', async ({page}) => {
  await nav(page,'Protect').click();
  await page.getByRole('button', {name:'Add rule',exact:true}).click();
  for (const action of ['Review demo rule','Discard draft']) {
    expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
    await page.getByRole('button', {name:action}).click();
  }
  await page.locator('#preview-prompt').click();
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
});

test('320px viewport, 200% text, RTL: controls fit without horizontal clipping', async ({page}) => {
  await page.setViewportSize({width:320,height:900});
  await page.getByLabel('Large text · 200%').check();
  for (const direction of ['ltr','rtl']) {
    await page.locator('html').evaluate((el,dir)=>{el.dir=dir;},direction);
    for (const route of ['Today','History','Patterns','Protect','Settings']) {
      await nav(page,route).click();
      expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
      const failures=await page.locator('#screen button, #navigation button').evaluateAll(elements => elements.filter(el=>{
        const r=el.getBoundingClientRect(); return r.width<47.5 || r.height<47.5 || r.left<-.5 || r.right>window.innerWidth+.5;
      }).map(el=>el.textContent));
      expect(failures).toEqual([]);
    }
  }
});

test('review screenshots', async ({page}) => {
  await page.screenshot({path:'.cache/preview-daylight.png',fullPage:true});
  await page.getByLabel('Appearance', {exact:true}).selectOption('dark');
  await page.screenshot({path:'.cache/preview-evening.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});
  await page.getByLabel('Appearance', {exact:true}).selectOption('light');
  await page.screenshot({path:'.cache/preview-mobile.png',fullPage:true});
});

test('semantic color pairs meet their documented contrast targets', async () => {
  const luminance=hex=>{
    const channels=hex.match(/\w\w/g).map(c=>parseInt(c,16)/255).map(c=>c<=.04045?c/12.92:((c+.055)/1.055)**2.4);
    return channels[0]*.2126+channels[1]*.7152+channels[2]*.0722;
  };
  const contrast=(a,b)=>{const values=[luminance(a),luminance(b)].sort((x,y)=>x-y);return (values[1]+.05)/(values[0]+.05);};
  for (const theme of ['light','dark']) {
    const p=palette[theme];
    for (const bg of ['canvas','surface','surfaceAlt','tint']) {
      for (const fg of ['text','muted','accent']) expect(contrast(p[fg],p[bg]),`${theme} ${fg}/${bg}`).toBeGreaterThanOrEqual(4.5);
      for (const fg of ['border','focus']) expect(contrast(p[fg],p[bg]),`${theme} ${fg}/${bg}`).toBeGreaterThanOrEqual(3);
    }
    for (const [fg,bg] of [['onAccent','accent'],['onLavender','lavender'],['caution','cautionSurface'],['danger','surface']]) expect(contrast(p[fg],p[bg]),`${theme} ${fg}/${bg}`).toBeGreaterThanOrEqual(4.5);
  }
});
