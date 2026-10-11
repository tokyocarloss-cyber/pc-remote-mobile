"""Generate original short UI cues; no third-party or theme-franchise recordings."""
import math, struct, wave
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'app/build/generated/nexusAudio/res/raw'
root.mkdir(parents=True,exist_ok=True)
for theme,base in enumerate([660,392,880,220,740,523,466,784,587,988]):
 for event,duration in enumerate([.045,.03,.08,.12,.13,.16,.12]):
  rate=22050; samples=[]
  for i in range(int(rate*duration)):
   t=i/rate; phase=t/duration
   envelope=min(1,t/.008)*max(0,1-phase)**2
   f=base*([1,1.25,.75,1.5,.55,1.25,.75][event])
   tone=math.sin(2*math.pi*f*t)+.2*math.sin(2*math.pi*f*1.5*t)
   samples.append(int(9500*envelope*tone))
  with wave.open(str(root/f'nexus_{theme}_{event}.wav'),'wb') as w:
   w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes(struct.pack('<'+'h'*len(samples),*samples))
