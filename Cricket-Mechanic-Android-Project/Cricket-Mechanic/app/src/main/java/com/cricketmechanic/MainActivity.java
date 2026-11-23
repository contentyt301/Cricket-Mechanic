package com.cricketmechanic;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
public class MainActivity extends Activity {
    private static final int PICK_VIDEO=41;
    private VideoView video;
    private float zoom=1f;
    private TextView status;
    public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(18,28,18,18); root.setBackgroundColor(0xFF101820);
        TextView title=new TextView(this); title.setText("CRICKET MECHANIC"); title.setTextColor(-1); title.setTextSize(24); title.setGravity(Gravity.CENTER); title.setPadding(8,12,8,18); root.addView(title);
        status=new TextView(this); status.setText("Select a bowling video to begin testing"); status.setTextColor(0xFFB8C7D1); status.setGravity(Gravity.CENTER); root.addView(status);
        video=new VideoView(this); video.setBackgroundColor(0xFF000000); LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,0,1f); vp.setMargins(0,18,0,18); root.addView(video,vp); video.setMediaController(new MediaController(this));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER);
        addButton(row,"Choose video",v->pickVideo()); addButton(row,"−",v->setZoom(zoom-.15f)); addButton(row,"+",v->setZoom(zoom+.15f)); addButton(row,"Reset",v->setZoom(1f)); root.addView(row);
        TextView note=new TextView(this); note.setText("Testing build • Video preview and zoom • Pose analysis is not included yet"); note.setTextColor(0xFF8FA3B5); note.setTextSize(12); note.setGravity(Gravity.CENTER); note.setPadding(4,18,4,4); root.addView(note);
        setContentView(root);
    }
    private void addButton(LinearLayout row,String label,View.OnClickListener click) { Button b=new Button(this); b.setText(label); b.setOnClickListener(click); row.addView(b,new LinearLayout.LayoutParams(0,-2,1f)); }
    private void pickVideo() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("video/*"); startActivityForResult(i,PICK_VIDEO); }
    protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PICK_VIDEO && result==RESULT_OK && data!=null) {
            Uri uri=data.getData(); try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {}
            video.setVideoURI(uri);
            video.setOnPreparedListener(mp->{ status.setText("Video loaded • "+mp.getVideoWidth()+" × "+mp.getVideoHeight()); video.start(); });
            video.setOnErrorListener((mp,what,extra)->{ Toast.makeText(this,"Unable to play this video format",Toast.LENGTH_LONG).show(); return true; });
        }
    }
    private void setZoom(float value) { zoom=Math.max(1f,Math.min(3f,value)); video.setScaleX(zoom); video.setScaleY(zoom); status.setText("Video zoom: "+String.format(java.util.Locale.US,"%.1fx",zoom)); }
}
