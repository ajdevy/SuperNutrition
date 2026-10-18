from pathlib import Path
import os,subprocess,xml.etree.ElementTree as ET,re,json,datetime,yaml,time
root=Path.cwd();base=root/'mobile/build/home-no-scroll-2026-10-17/native-ci-journey';flow=root/'mobile/.maestro/photo-analysis/photo-analysis-capture-recovery.yaml'
header,steps=list(yaml.safe_load_all(flow.read_text()));assert header=={'appId':'${APP_ID}'}
locators={}
for line in (flow.parent/'photo-analysis-locators.js').read_text().splitlines():
 if not line.strip() or line.startswith('//'):continue
 match=re.fullmatch(r'output\.(\w+) = "([\w_]+)";',line);assert match,line;locators[match[1]]=match[2]
env=os.environ|{'ADB_SERVER_SOCKET':'tcp:localhost:5041','ANDROID_ADB_SERVER_PORT':'5041','ADB_MDNS_AUTO_CONNECT':'0','ADB_LOCAL_TRANSPORT_MAX_PORT':'5591'};adb=['/Users/anastasia/Library/Android/sdk/platform-tools/adb','-P','5041','-s','emulator-5590'];package='app.supernutrition.uiAcceptance';activity='com.supernutrition.feature.photoanalysis.PhotoAnalysisComposeActivity'
def call(*args,timeout=30):return subprocess.check_output(adb+list(args),env=env,timeout=timeout)
def guard(expected):
 win=call('shell','dumpsys','window').decode();focus=[line for line in win.splitlines() if 'mCurrentFocus=' in line];assert len(focus)==1 and any(p+'/'+activity in focus[0] for p in expected),focus
assert call('emu','avd','name').decode().splitlines()[0]=='SN_Final_Verification_35_20261018';guard(['app.supernutrition',package]);assert call('shell','pm','path',package).startswith(b'package:')
record={'started_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'executor':'isolated ADB native witness of the actual Maestro command sequence; not a Maestro execution','flow':str(flow.relative_to(root)),'package':package,'avd':'SN_Final_Verification_35_20261018','serial':'emulator-5590','adb_port':5041,'steps':[]};print('NATIVE_JOURNEY_STARTED',len(steps),'commands',flush=True)
def find(value,index,action):
 assert set(value)=={'id'};match=re.fullmatch(r'\$\{output\.(\w+)\}',value['id']);assert match;rid=locators[match[1]]
 deadline=time.monotonic()+15;attempt=0;last_dump='';nodes=[]
 while time.monotonic()<deadline:
  guard([package]);attempt+=1;path='/sdcard/sn-ci-journey-'+str(time.time_ns())+'.xml'
  try:
   dump=subprocess.check_output(adb+['shell','uiautomator','dump',path],env=env,timeout=max(1,deadline-time.monotonic()),stderr=subprocess.STDOUT).decode();last_dump=dump
  except subprocess.TimeoutExpired:
   last_dump='fresh hierarchy deadline exceeded';break
  if 'dumped to:' in dump:
   raw=call('shell','cat',path);(base/f'{index:02}-{action}-{rid}.xml').write_bytes(raw);nodes=[n for n in ET.fromstring(raw).iter('node') if n.get('resource-id')==rid and n.get('package')==package];assert len(nodes)<=1,(rid,len(nodes))
   if nodes or action=='runFlow':return rid,nodes
  time.sleep(0.25)
 raise AssertionError((index,action,rid,'not visible within 15s',attempt,last_dump))

counter=0
def execute(step):
 global counter
 counter+=1;index=counter
 if isinstance(step,str):
  assert step=="launchApp";action,value=step,None
 else:
  assert len(step)==1;action,value=next(iter(step.items()))
 if action=='runScript':assert value=={'file':'./photo-analysis-locators.js'};return
 if action=='launchApp':
  call('shell','am','force-stop',package);call('shell','am','start','-W','-n',package+'/'+activity);return
 if action=='runFlow':
  assert set(value)=={'when','commands'} and set(value['when'])=={'visible'};rid,nodes=find(value['when']['visible'],index,action);record['steps'].append({'index':index,'action':'conditional Home entry','resource_id':rid,'entered':bool(nodes)})
  if nodes:
   for child in value['commands']:execute(child)
  return
 assert action in ['assertVisible','tapOn'];rid,nodes=find(value,index,action);assert len(nodes)==1,(index,action,rid,len(nodes));node=nodes[0];bounds=list(map(int,re.findall(r'\d+',node.get('bounds'))));assert len(bounds)==4 and bounds[2]>bounds[0] and bounds[3]>bounds[1],(rid,bounds)
 if action=='tapOn':
  assert node.get('clickable')=='true',(rid,node.attrib);call('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2));print('TAPPED',rid,flush=True)
 record['steps'].append({'index':index,'action':action,'resource_id':rid,'bounds':bounds,'passed':True})
try:
 for step in steps:execute(step)
 record.update(exit_code=0,assertions=sum(s['action']=='assertVisible' for s in record['steps']),taps=sum(s['action']=='tapOn' for s in record['steps']));guard([package])
 subprocess.run(['/Users/anastasia/.local/bin/android','screen','capture','--device','emulator-5590','-o',str(base/'home-after-journey.png')],env=env,check=True,timeout=45);print('NATIVE_JOURNEY_PASSED',record['assertions'],'assertions',record['taps'],'taps',flush=True)
except Exception as failure:
 record.update(exit_code=1,error=str(failure));raise
finally:
 record['finished_at']=datetime.datetime.now(datetime.timezone.utc).isoformat();(base/'run.json').write_text(json.dumps(record,indent=2)+'\n')
