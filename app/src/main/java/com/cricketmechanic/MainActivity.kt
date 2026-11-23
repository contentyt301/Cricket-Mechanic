package com.cricketmechanic

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.*
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : Activity() {
    private lateinit var video: VideoView
    private lateinit var overlay: PoseOverlay
    private lateinit var status: TextView
    private lateinit var seek: SeekBar
    private var retriever: MediaMetadataRetriever? = null
    private var landmarker: PoseLandmarker? = null
    private var duration = 0
    private var zoom = 1f
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var lastFrame = -1L
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadVideo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 25, 32)
        window.navigationBarColor = Color.BLACK
        buildUi()
        try {
            val opts = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_full.task").build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.45f)
                .setMinPosePresenceConfidence(0.45f)
                .setMinTrackingConfidence(0.45f)
                .build()
            landmarker = PoseLandmarker.createFromOptions(this, opts)
            status.text = "Pose model ready • Choose a bowling video"
        } catch (e: Exception) {
            status.text = "Model load error: ${e.message}"
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14,18,14,8); setBackgroundColor(Color.rgb(13,25,32)) }
        val title = TextView(this).apply { text="CRICKET MECHANIC"; textSize=25f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setPadding(0,8,0,12) }
        root.addView(title)
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        video = VideoView(this)
        overlay = PoseOverlay(this)
        frame.addView(video, FrameLayout.LayoutParams(-1,-1))
        frame.addView(overlay, FrameLayout.LayoutParams(-1,-1))
        root.addView(frame, LinearLayout.LayoutParams(-1,0,1f))
        val buttons = LinearLayout(this).apply { gravity=Gravity.CENTER; orientation=LinearLayout.HORIZONTAL }
        fun button(label:String, action:()->Unit) { val b=Button(this).apply { text=label; setOnClickListener { action() } }; buttons.addView(b, LinearLayout.LayoutParams(0,58,1f).apply { setMargins(3,8,3,4) }) }
        button("CHOOSE VIDEO") { picker.launch(arrayOf("video/*")) }
        button("−") { setZoom(max(1f,zoom-0.2f)) }
        button("+") { setZoom((zoom+0.2f).coerceAtMost(3f)) }
        button("RESET") { setZoom(1f) }
        root.addView(buttons)
        seek = SeekBar(this)
        root.addView(seek)
        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?, p:Int, from:Boolean) { if(from && duration>0) { video.seekTo(p); analyseAt(p.toLong()) } }
            override fun onStartTrackingTouch(s:SeekBar?) {}
            override fun onStopTrackingTouch(s:SeekBar?) {}
        })
        status = TextView(this).apply { text="Loading pose model…"; textSize=13f; setTextColor(Color.LTGRAY); gravity=Gravity.CENTER; setPadding(0,8,0,10) }
        root.addView(status)
        setContentView(root)
        video.setOnPreparedListener { mp -> duration=mp.duration; seek.max=duration; video.start(); handler.post(ticker) }
    }

    private fun setZoom(z:Float) {
        zoom=z
        video.scaleX=z; video.scaleY=z
        overlay.scaleX=z; overlay.scaleY=z
    }

    private fun loadVideo(uri:Uri) {
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {}
        try { retriever?.release() } catch (_:Exception) {}
        retriever=MediaMetadataRetriever()
        try { retriever?.setDataSource(this,uri) } catch(e:Exception) { status.text="Cannot open video: ${e.message}"; return }
        lastFrame=-1
        video.setVideoURI(uri)
        status.text="Video loaded • Running pose detection"
    }

    private val ticker = object:Runnable {
        override fun run() {
            if (::video.isInitialized && video.isPlaying) {
                val p=video.currentPosition
                if(duration>0) seek.progress=p
                analyseAt(p.toLong())
            }
            handler.postDelayed(this,250)
        }
    }

    private fun analyseAt(ms:Long) {
        if(ms==lastFrame || retriever==null || landmarker==null) return
        lastFrame=ms
        worker.execute {
            try {
                val bmp=retriever?.getFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST)
                if(bmp!=null) {
                    val result=landmarker?.detect(BitmapImageBuilder(bmp).build())
                    val pts=result?.landmarks()?.firstOrNull()?.map { PointF(it.x(),it.y()) } ?: emptyList()
                    runOnUiThread {
                        overlay.setPoints(pts)
                        status.text=if(pts.isEmpty()) "No pose detected • Use a clear, wide shot" else "Pose detected • ${pts.size} landmarks • Experimental 2D tracking"
                    }
                    bmp.recycle()
                }
            } catch(e:Exception) { runOnUiThread { status.text="Pose processing: ${e.message}" } }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        worker.shutdown()
        try { retriever?.release(); landmarker?.close() } catch (_:Exception) {}
        super.onDestroy()
    }
}

class PoseOverlay(context: android.content.Context): View(context) {
    private var points:List<PointF> = emptyList()
    private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.GREEN; strokeWidth=5f; style=Paint.Style.STROKE }
    private val dot=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.YELLOW; style=Paint.Style.FILL }
    private val edges=arrayOf(
        11 to 12,11 to 13,13 to 15,12 to 14,14 to 16,11 to 23,12 to 24,23 to 24,
        23 to 25,25 to 27,27 to 29,29 to 31,27 to 31,24 to 26,26 to 28,28 to 30,30 to 32,28 to 32,
        0 to 1,1 to 3,0 to 2,2 to 4,0 to 5,5 to 6,5 to 7,7 to 9,6 to 8,8 to 10,5 to 11,6 to 12
    )
    fun setPoints(p:List<PointF>) { points=p; invalidate() }
    override fun onDraw(c:Canvas) {
        super.onDraw(c)
        val w=width.toFloat(); val h=height.toFloat()
        for((a,b) in edges) if(a<points.size && b<points.size) {
            val p=points[a]; val q=points[b]
            c.drawLine(p.x*w,p.y*h,q.x*w,q.y*h,line)
        }
        points.forEach { c.drawCircle(it.x*w,it.y*h,6f,dot) }
    }
}
