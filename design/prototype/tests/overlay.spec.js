import {test,expect} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.beforeEach(async ({page})=>{
  await page.goto('/');
  await page.getByRole('button',{name:'Explore the live counter'}).click();
  await expect(page.locator('h1')).toHaveText('A quiet pointof reference.');
});
test('confirmed transitions update visit and today once; duplicate callbacks add nothing',async({page})=>{
  const counter=page.getByRole('button',{name:/Awareness counter:/});
  await expect(counter).toContainText('~18');
  await expect(counter).toContainText('~64');
  await page.getByRole('button',{name:'Simulate next confirmed item'}).click();
  await expect(counter).toContainText('~19');
  await expect(counter).toContainText('~65');
  await page.getByRole('button',{name:'Replay same-item callback'}).click();
  await expect(counter).toContainText('~19');
  await expect(counter).toContainText('~65');
  await page.getByRole('button',{name:'Begin a new confirmed visit'}).click();
  await expect(counter).toContainText('This visit ~1');
  await expect(counter).toContainText('Today ~66');
});
test('unknown surface removes counter and cannot add inferred exposures',async({page})=>{
  await page.getByRole('button',{name:'Lose surface signal'}).click();
  await expect(page.getByRole('button',{name:/Awareness counter:/})).toHaveCount(0);
  await page.getByRole('button',{name:'Simulate next confirmed item'}).click();
  await expect(page.getByRole('status')).toContainText('No exposure added');
  await page.getByRole('button',{name:'Restore surface signal'}).click();
  await expect(page.getByRole('button',{name:/Awareness counter:/})).toContainText('Today ~64');
});
test('hiding display is independent; preset placement needs no drag',async({page})=>{
  await expect(page.locator('.feed-counter')).toHaveClass(/counter-center/);
  await page.getByRole('button',{name:/Awareness counter:/}).click();
  await expect(page.getByRole('dialog')).toContainText('Not unique videos');
  await page.getByRole('button',{name:'Place at trailing edge'}).click();
  await expect(page.locator('.feed-counter')).toHaveClass(/counter-end/);
  await page.getByRole('button',{name:'Hide counter',exact:true}).click();
  await expect(page.locator('.feed-counter')).toHaveCount(0);
  await expect(page.locator('.overlay-state')).toContainText('Observation choice and protection are separate');
  await page.getByRole('button',{name:'Show counter',exact:true}).click();
  await expect(page.locator('.feed-counter')).toContainText('Today ~64');
});
for(const theme of ['light','dark']){
  test(`overlay accessibility and large type · ${theme}`,async({page})=>{
    await page.getByLabel('Appearance',{exact:true}).selectOption(theme);
    expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
    await page.getByRole('button',{name:'Counter details and position'}).click();
    expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
    await page.keyboard.press('Escape');
    await page.setViewportSize({width:320,height:900});
    await page.getByLabel('Large text · 200%').check();
    await page.locator('html').evaluate(el=>{el.dir='rtl';});
    expect(await page.locator('#screen').evaluate(el=>el.scrollWidth<=el.clientWidth)).toBe(true);
    const size=await page.locator('.feed-counter').boundingBox();
    expect(size.width).toBeGreaterThanOrEqual(48);
    expect(size.height).toBeGreaterThanOrEqual(48);
  });
}
test('overlay review screenshot',async({page})=>{
  await page.screenshot({path:'.cache/preview-overlay.png',fullPage:true});
});
