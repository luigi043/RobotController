const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
(async()=>{
 const out=path.resolve(process.env.SCREENSHOT_DIR||'docs/verification/browser');await fs.mkdir(out,{recursive:true});
 const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
 const page=await browser.newPage({viewport:{width:1440,height:1080}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 const shot=async name=>page.screenshot({path:path.join(out,name+'.png'),fullPage:true});
 try{
  await page.goto(process.env.DEMO_URL||require('node:url').pathToFileURL(path.resolve('docs/demo/index.html')).href);
  const get=id=>page.locator('#'+id),button=name=>page.getByRole('button',{name,exact:true});
  assert(await button('Move forward').isDisabled());await shot('01-disconnected');
  await button('Connect demo').click();await page.getByRole('status').filter({hasText:'Demo connected'}).waitFor();assert(await button('Move forward').isEnabled());await shot('02-connected');
  const before=await get('robot').evaluate(e=>parseFloat(e.style.top));const rect=await button('Move forward').boundingBox();await page.mouse.move(rect.x+rect.width/2,rect.y+rect.height/2);await page.mouse.down();await page.waitForTimeout(350);await shot('03-hold-forward');await page.mouse.up();assert((await get('robot').evaluate(e=>parseFloat(e.style.top)))<before);assert.equal(await get('motion').textContent(),'Stopped');assert.match(await get('log').textContent(),/SIM OK: S/);
  await page.keyboard.down('ArrowRight');await page.waitForTimeout(100);await page.keyboard.up('ArrowRight');assert.equal(await get('motion').textContent(),'Stopped');
  await page.keyboard.down('ArrowUp');await page.waitForTimeout(50);await page.evaluate(()=>window.dispatchEvent(new Event('blur')));assert.equal(await get('motion').textContent(),'Stopped');await page.keyboard.up('ArrowUp');
  for(const colour of ['Red','Blue','Green']){await button(colour).click();assert.equal(await get('colour').textContent(),colour)}
  await button('Toggle auto mode').click();assert.equal(await get('auto-state').textContent(),'On (simulated)');await shot('04-auto-mode');await button('Stop robot').click();assert.equal(await get('auto-state').textContent(),'Off');
  await button('Simulate connection loss').click();assert.equal(await get('status').textContent(),'Connection error');assert(await button('Move forward').isDisabled());await shot('05-connection-loss');
  await button('Connect demo').click();await button('Disconnect').click();assert(await button('Move forward').isDisabled());await button('Clear log').click();assert.equal(await get('log').textContent(),'No commands yet.');
  await button('Reset simulation').click();assert.equal(await get('colour').textContent(),'Off');assert.equal(await get('robot').evaluate(e=>parseFloat(e.style.top)),50);
  await page.setViewportSize({width:390,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await shot('06-phone-disconnected');await button('Connect demo').click();await shot('07-phone-connected');
  assert.deepEqual(errors,[]);console.log('PASS: disabled controls, connect, pointer hold/release and movement, keyboard release, blur STOP, RGB, auto/STOP, loss, reconnect/disconnect, clear, reset, mobile overflow, zero browser errors.');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
