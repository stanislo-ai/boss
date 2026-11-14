#!/bin/bash
# usage: encode.sh <framesDir> <out.mp4> [fps] [audioOffset] [dur] [sfx.wav] [sfxGainDb]
F=$1; O=$2; FPS=${3:-30}; OFF=${4:-2.08}; DUR=${5:-15.2}; SFX=$6; G=${7:--14}
A=/root/.claude/uploads/728a5fc8-b638-5b92-86a2-d70a02b1119e/fdf24734-eeebfcd0abda598e9fb32e1a713862f5.mp3
FO=$(python3 -c "print($DUR-0.7)")
if [ -n "$SFX" ]; then
  INS=(-i "$SFX"); MIX="[1:a]afade=t=in:d=0.03[m];[2:a]volume=${G}dB,aformat=channel_layouts=stereo[s];[m][s]amix=inputs=2:normalize=0,afade=t=out:st=$FO:d=0.7,loudnorm=I=-12:TP=-1:LRA=9[a]"
else
  INS=(); MIX="[1:a]afade=t=in:d=0.03,afade=t=out:st=$FO:d=0.7,loudnorm=I=-12:TP=-1:LRA=9[a]"
fi
ffmpeg -v error -y -framerate $FPS -i "$F/f_%04d.jpg" -ss $OFF -t $DUR -i "$A" "${INS[@]}" \
  -filter_complex "$MIX" -map 0:v -map "[a]" \
  -c:v libx264 -preset slow -crf 20 -maxrate 18M -bufsize 36M -pix_fmt yuv420p -profile:v high -movflags +faststart -c:a aac -b:a 192k -ar 44100 -t $DUR "$O"
