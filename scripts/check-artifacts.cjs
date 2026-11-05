const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
(async()=>{
 const base=process.env.DEMO_URL;if(!base)throw Error('Set DEMO_URL to the public deployment.');
 const manifest=JSON.parse(await fs.readFile('docs/verification/evidence/public-artifacts.json','utf8'));
 for(const file of manifest.filter(f=>f.file.startsWith('native/'))){const response=await fetch(new URL(file.file,base));assert.equal(response.status,200,file.file);assert.match(response.headers.get('content-type'),/image\/png/);const b=Buffer.from(await response.arrayBuffer());assert.equal(crypto.createHash('sha256').update(b).digest('hex'),file.sha256,file.file)}
 const apk=manifest.find(f=>f.file==='robot-controller-debug.apk'),response=await fetch(new URL(apk.file,base));assert.equal(response.status,200);const hash=crypto.createHash('sha256');let size=0;for await(const part of response.body){hash.update(part);size+=part.length}assert.equal(size,apk.size);assert.equal(hash.digest('hex'),apk.sha256);
 const out=process.env.SCREENSHOT_DIR||'docs/verification/public';await fs.mkdir(out,{recursive:true});
 const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
 try{const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto(new URL('native.html',base).href);assert.equal(await page.locator('.native-gallery img').count(),13);await page.screenshot({path:path.join(out,'08-native-gallery.png'),fullPage:true});await page.setViewportSize({width:390,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(out,'09-phone-native-gallery.png'),fullPage:true});assert.deepEqual(errors,[])}finally{await browser.close()}
 console.log(`PASS: all 13 public native PNGs match their SHA-256 digests; public APK matches ${apk.sha256} (${apk.size} bytes); gallery count, phone layout and zero browser errors.`);
})().catch(e=>{console.error(e);process.exitCode=1});
